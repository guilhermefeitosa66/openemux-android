package com.openemux.mobile.data

import android.content.Context
import android.util.Log
import com.retroarch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One HTTP GET's outcome; [code] is -1 when the network failed. */
class HttpReply(val code: Int, val body: ByteArray?) {
    val text: String? get() = body?.toString(Charsets.UTF_8)
}

/** A GET, behind an interface so the tests need no network. */
fun interface HttpGet {
    suspend fun get(url: String): HttpReply
}

/** The plain [HttpURLConnection] GET the app uses. Never logs the URL: ScreenScraper's carry credentials. */
class UrlHttp(private val tag: String, private val timeoutMs: Int = 15_000) : HttpGet {
    override suspend fun get(url: String): HttpReply = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "OpenEmux/${BuildConfig.VERSION_NAME}")
            try {
                val code = conn.responseCode
                HttpReply(code, if (code == 200) conn.inputStream.use { it.readBytes() } else null)
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Log.w(tag, "request failed: ${e.javaClass.simpleName}")
            HttpReply(-1, null)
        }
    }
}

/**
 * The ScreenScraper accounts the user may add (issue 753). [user]/[password] give the user's own
 * daily quota; [devId]/[devPassword] replace the developer credentials the release build embeds,
 * and only when both are set, as on the desktop.
 */
data class ScreenScraperAccount(
    val user: String = "",
    val password: String = "",
    val devId: String = "",
    val devPassword: String = "",
) {
    val hasUser: Boolean get() = user.isNotBlank() && password.isNotEmpty()
    val hasDeveloper: Boolean get() = devId.isNotBlank() && devPassword.isNotEmpty()
}

/** Where the accounts are kept. */
interface AccountStore {
    val account: StateFlow<ScreenScraperAccount>
    fun save(account: ScreenScraperAccount)
}

/**
 * The accounts in a private preferences file of their own, which no backup carries (see
 * `backup_rules.xml`), the app's own Backup does not export (it exports "openemux") and Share
 * nearby never sends. Same model as the RetroAchievements token.
 */
class PrefsAccountStore(context: Context) : AccountStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(read())
    override val account: StateFlow<ScreenScraperAccount> = _account.asStateFlow()

    private fun read() = ScreenScraperAccount(
        prefs.getString(USER, "").orEmpty(), prefs.getString(PASSWORD, "").orEmpty(),
        prefs.getString(DEV_ID, "").orEmpty(), prefs.getString(DEV_PASSWORD, "").orEmpty(),
    )

    override fun save(account: ScreenScraperAccount) {
        prefs.edit().putString(USER, account.user.trim()).putString(PASSWORD, account.password)
            .putString(DEV_ID, account.devId.trim()).putString(DEV_PASSWORD, account.devPassword).apply()
        _account.value = read()
    }

    companion object {
        /** Named in backup_rules.xml and data_extraction_rules.xml: keep them in step. */
        const val PREFS = "screenscraper"
        private const val USER = "user"
        private const val PASSWORD = "password"
        private const val DEV_ID = "dev_id"
        private const val DEV_PASSWORD = "dev_password"
    }
}

/** What ScreenScraper answered about one game. */
sealed interface SsReply {
    class Ok(val game: JSONObject) : SsReply
    data object NotFound : SsReply
    data object Quota : SsReply
    /** Wrong or refused credentials. */
    data object Refused : SsReply
    /** No developer credentials at all: this build has none and the user added none. */
    data object NoCredentials : SsReply
    /** [network]: the request never got an answer; otherwise the service answered with an error. */
    data class Unreachable(val network: Boolean) : SsReply
}

/** What "Check account" found. */
sealed interface SsAccountReply {
    /** A user account: its requests today out of its daily maximum (null when not reported). */
    data class User(val name: String, val used: Int?, val max: Int?) : SsAccountReply
    /** No user account, and the developer credentials were accepted. */
    data object Developer : SsAccountReply
    data object Refused : SsAccountReply
    data object NoCredentials : SsAccountReply
    data object Quota : SsAccountReply
    data class Unreachable(val network: Boolean) : SsAccountReply
}

/**
 * ScreenScraper's API, shared by the label sync and the manager's search (issue 753): the
 * credentials in effect, the one-request-per-second pacing the API demands, and the reading of its
 * answers. Status codes follow the desktop's `screenscraper.py`.
 */
class ScreenScraper(
    private val http: HttpGet,
    private val account: () -> ScreenScraperAccount,
    private val embeddedDevId: String = BuildConfig.SS_DEVID,
    private val embeddedDevPassword: String = BuildConfig.SS_DEVPASSWORD,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pace: Long = 1000L,
) {
    private val requests = Mutex()
    private var lastRequest = 0L

    /** The developer credentials in effect: the user's own when both are set, otherwise the build's. */
    fun developer(): Pair<String, String>? {
        val a = account()
        if (a.hasDeveloper) return a.devId.trim() to a.devPassword
        return (embeddedDevId to embeddedDevPassword).takeIf { embeddedDevId.isNotEmpty() && embeddedDevPassword.isNotEmpty() }
    }

    val usable: Boolean get() = developer() != null

    /** Whether the build embeds developer credentials of its own. */
    val embedded: Boolean get() = embeddedDevId.isNotEmpty() && embeddedDevPassword.isNotEmpty()

    private fun base(dev: Pair<String, String>): List<Pair<String, String>> {
        val a = account()
        return buildList {
            add("devid" to dev.first)
            add("devpassword" to dev.second)
            add("softname" to "OpenEmux")
            add("output" to "json")
            if (a.hasUser) {
                add("ssid" to a.user.trim())
                add("sspassword" to a.password)
            }
        }
    }

    private fun url(endpoint: String, params: List<Pair<String, String>>) =
        "$API/$endpoint?" + params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    /**
     * Looks a game up. With [identity], by hash (CRC, MD5, the file name and size); without, by
     * [name] alone, as the desktop's "Search by name" does.
     */
    suspend fun game(systemId: Int, name: String, identity: RomIdentity?): SsReply {
        val dev = developer() ?: return SsReply.NoCredentials
        val params = base(dev) + buildList {
            add("systemeid" to systemId.toString())
            add("romtype" to "rom")
            add("romnom" to (identity?.name ?: name))
            if (identity != null) {
                add("crc" to identity.crc)
                identity.md5?.let { add("md5" to it) }
                add("romtaille" to identity.size.toString())
            }
        }
        val reply = get(url("jeuInfos.php", params))
        return when (reply.code) {
            200 -> reply.text?.let { body ->
                runCatching { JSONObject(body).getJSONObject("response").getJSONObject("jeu") }.getOrNull()
            }?.let { SsReply.Ok(it) } ?: SsReply.Unreachable(network = false)
            else -> classify(reply.code)
        }
    }

    /** Downloads one media URL from a [SsReply.Ok] game, at most [maxWidth] pixels wide. */
    suspend fun media(url: String, maxWidth: Int = 640): HttpReply = get("$url&maxwidth=$maxWidth")

    /** "Check account": the user's quota when there is a user account, or whether the developer one is accepted. */
    suspend fun check(): SsAccountReply {
        val dev = developer() ?: return SsAccountReply.NoCredentials
        val a = account()
        if (!a.hasUser) {
            val reply = get(url("ssinfraInfos.php", base(dev)))
            return if (reply.code == 200) SsAccountReply.Developer else accountError(reply.code)
        }
        val reply = get(url("ssuserInfos.php", base(dev)))
        if (reply.code != 200) return accountError(reply.code)
        val user = reply.text?.let { runCatching { JSONObject(it).getJSONObject("response").getJSONObject("ssuser") }.getOrNull() }
            ?: return SsAccountReply.Unreachable(network = false)
        fun int(key: String) = user.optString(key).toIntOrNull()
        return SsAccountReply.User(user.optString("id").ifEmpty { a.user.trim() }, int("requeststoday"), int("maxrequestsperday"))
    }

    private fun accountError(code: Int): SsAccountReply = when (classify(code)) {
        SsReply.Quota -> SsAccountReply.Quota
        SsReply.Refused -> SsAccountReply.Refused
        else -> SsAccountReply.Unreachable(network = code == -1)
    }

    /** The API punishes bursts: one request per second, media included, the sync's and the search's alike. */
    private suspend fun get(url: String): HttpReply = requests.withLock {
        val wait = lastRequest + pace - clock()
        if (wait > 0) delay(wait)
        lastRequest = clock()
        http.get(url).also { if (it.code in QUOTA) Log.w(TAG, "screenscraper quota: ${it.code}") }
    }

    companion object {
        private const val TAG = "OpenEmuxScreenScraper"
        private const val API = "https://api.screenscraper.fr/api2"
        private val QUOTA = setOf(429, 430)
        private val REFUSED = setOf(401, 403, 426)

        fun classify(code: Int): SsReply = when (code) {
            404 -> SsReply.NotFound
            in QUOTA -> SsReply.Quota
            in REFUSED -> SsReply.Refused
            else -> SsReply.Unreachable(network = code == -1)
        }

        /** The media types each art kind is taken from, best first (desktop `screenscraper.py`). */
        fun mediaTypes(kind: ArtKind): List<String> =
            if (kind == ArtKind.COVER) listOf("box-2D", "box-texture", "box-3D") else listOf("support-texture")

        /** The regions to prefer for [fileName]: its own region tags first, then the desktop's defaults. */
        fun regionPriority(fileName: String): List<String> {
            val tagged = listOfNotNull(
                "us".takeIf { "(USA" in fileName },
                "eu".takeIf { "Europe" in fileName },
                "jp".takeIf { "Japan" in fileName },
                "br".takeIf { "Brazil" in fileName },
            )
            return (tagged + listOf("wor", "us", "eu", "jp", "ss")).distinct()
        }

        /**
         * Up to [max] distinct media URLs of [kind] from a game: by type first, then by region,
         * a media with no region right after the known ones and an unknown region last.
         */
        fun mediaUrls(game: JSONObject, kind: ArtKind, regions: List<String>, max: Int): List<String> {
            val medias = game.optJSONArray("medias") ?: return emptyList()
            val all = (0 until medias.length()).mapNotNull { medias.optJSONObject(it) }
            fun rank(region: String) = when {
                region.isEmpty() -> regions.size
                region in regions -> regions.indexOf(region)
                else -> regions.size + 1
            }
            return mediaTypes(kind).flatMap { type ->
                all.filter { it.optString("type") == type }.sortedBy { rank(it.optString("region")) }.map { it.optString("url") }
            }.filter { it.isNotEmpty() }.distinct().take(max)
        }
    }
}
