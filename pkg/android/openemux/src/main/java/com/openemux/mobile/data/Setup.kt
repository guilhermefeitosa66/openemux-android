package com.openemux.mobile.data

import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.ZipInputStream

/**
 * The emulator downloads: the cores for this device's ABI, every core's .info
 * and the system files PPSSPP and Dolphin need. Nothing gates the app on them
 * (issue 922): the core a console needs is fetched the first time one of its
 * games is launched ([ensureCore]), the info files come down on their own at
 * first run ([ensureInfo]), and "Download all emulators" ([run]) fetches the
 * rest, three at a time. What is already installed is skipped, so a retry
 * picks up where it stopped.
 */
class Setup(private val paths: Paths, abis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()) {
    enum class Step(@StringRes val label: Int) {
        CORES(R.string.data_setup_step_cores),
        INFO(R.string.data_setup_step_info),
        SYSTEM(R.string.data_setup_step_system),
    }

    sealed interface State {
        /** Nothing running; [missing] cores still to fetch on demand (0 once everything is on the device). */
        data class Idle(val missing: Int) : State
        /** [item] is null for an archive named after its step. */
        data class Running(val step: Step, val done: Int, val total: Int, val item: String?, val bytes: Long) : State {
            @Composable
            fun itemName(): String = item ?: stringResource(step.label)
        }
        /** [reason] takes the item's name as %1$s and [detail] as %2$s. */
        data class Failed(
            @StringRes val reason: Int,
            val step: Step,
            val done: Int,
            val total: Int,
            val item: String? = null,
            val detail: String = "",
        ) : State {
            @Composable
            fun message(): String = stringResource(reason, item ?: stringResource(step.label), detail)
        }
        /** Every emulator and its files are on the device. */
        data object Done : State
    }

    /** A download that failed, with the words for it: [reason] takes [item] as %1$s and [detail] as %2$s. */
    class SetupException(@StringRes val reason: Int, val item: String, val detail: String, cause: IOException) : IOException(cause.message, cause)

    /** [name] is null for an archive named after its step. */
    private class Item(val step: Step, val name: String?, val url: String, val installed: () -> Boolean, val install: (File) -> Unit)

    private val abi = abis.firstOrNull { it in ABIS }
    private val abisNamed = abis.joinToString()
    private val stamp = File(paths.internal, "setup.done")
    private val marks = File(paths.internal, "setup")

    private val _state = MutableStateFlow<State>(if (needed()) State.Idle(-1) else State.Done)
    val state: StateFlow<State> = _state.asStateFlow()

    /** One download of a given file at a time: a launch and "Download all" asking for the same core share it. */
    private val downloads = Mutex()

    private fun needed() = !stamp.exists() || stamp.readText() != "$VERSION/$abi"

    private fun coreItem(abi: String, core: CoreDef): Item {
        val target = File(paths.cores, core.fileName)
        return Item(Step.CORES, core.name, "$BUILDBOT/nightly/android/latest/$abi/${core.fileName}.zip", { target.length() > 0 }) { zip ->
            unzip(zip, paths.cores) { entry -> if (entry == core.fileName) entry else null }
        }
    }

    private val infoItem = marked("info", Step.INFO, null, "$BUILDBOT/assets/frontend/info.zip") { zip ->
        unzip(zip, paths.info) { entry -> entry.takeIf { it.endsWith(".info") && '/' !in it } }
    }

    private fun systemItem(name: String, file: String) =
        marked("system-$file", Step.SYSTEM, name, "$BUILDBOT/assets/system/$file.zip") { zip -> unzip(zip, paths.system) { it } }

    private fun items(abi: String): List<Item> =
        Console.entries.flatMap { it.cores }.distinctBy { it.id }.map { coreItem(abi, it) } + infoItem + SYSTEM_PACKS.map { (name, file) -> systemItem(name, file) }

    /** An archive that leaves no single file to check for: a marker records it. */
    private fun marked(id: String, step: Step, name: String?, url: String, install: (File) -> Unit): Item {
        val mark = File(marks, "$id.ok")
        return Item(step, name, url, { mark.exists() }) { zip -> install(zip); mark.parentFile?.mkdirs(); mark.writeText("") }
    }

    /** Whether [core] is on the device, so a launch can start at once. */
    fun coreInstalled(core: CoreDef): Boolean = File(paths.cores, core.fileName).length() > 0

    /** Whether the core info files are on the device (the pickers' names). */
    fun infoInstalled(): Boolean = infoItem.installed()

    /** How many of the cores the consoles name are not on the device yet. */
    fun missingCores(): Int {
        val abi = abi ?: return 0
        return Console.entries.flatMap { it.cores }.distinctBy { it.id }.count { !coreItem(abi, it).installed() }
    }

    /** Counts the cores still to fetch, for the shelf's pill; nothing when a download runs or everything is there. */
    fun refresh() {
        if (_state.value is State.Running) return
        _state.value = if (needed()) State.Idle(missingCores()) else State.Done
    }

    /** The core info files, fetched once at first run so the pickers name the cores; quiet, logged on failure. */
    suspend fun ensureInfo() = withContext(Dispatchers.IO) {
        if (infoItem.installed()) return@withContext
        runCatching { fetch(infoItem) { _, _ -> } }.onFailure { Log.w(TAG, "core info not fetched yet", it) }
    }

    /**
     * Fetches [core] if it is not on the device, with the files its emulator needs (the PSP and GameCube system
     * packs) and the info files; [onProgress] gets the bytes so far and the total (0 while unknown). Throws a
     * [SetupException] when a download fails; cancellation stops it between chunks.
     */
    suspend fun ensureCore(core: CoreDef, onProgress: (bytes: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val abi = abi ?: throw SetupException(R.string.data_setup_unsupported, core.name, abisNamed, IOException("unsupported ABI"))
        val wanted = listOfNotNull(
            coreItem(abi, core),
            infoItem,
            SYSTEM_PACKS.firstOrNull { (name, _) -> core.id.contains(name, ignoreCase = true) }?.let { (name, file) -> systemItem(name, file) },
        )
        for (item in wanted) {
            if (item.installed()) continue
            downloads.withLock {
                if (item.installed()) return@withLock
                try {
                    fetch(item, onProgress)
                } catch (e: IOException) {
                    throw failure(e, item)
                }
            }
        }
        refresh()
    }

    /** Every emulator and its files, three downloads at a time; the state follows for the pill and the sheet. */
    suspend fun run() = withContext(Dispatchers.IO) {
        val abi = abi ?: run {
            _state.value = State.Failed(R.string.data_setup_unsupported, Step.CORES, 0, 0, detail = abisNamed)
            return@withContext
        }
        val items = items(abi)
        val todo = items.filter { !it.installed() }
        var done = items.size - todo.size
        if (todo.isNotEmpty()) _state.value = State.Running(todo.first().step, done, items.size, todo.first().name, 0)
        val gate = Semaphore(PARALLEL)
        var failed: State.Failed? = null
        try {
            coroutineScope {
                todo.forEach { item ->
                    launch {
                        gate.withPermit {
                            if (failed != null) return@withPermit
                            try {
                                downloads.withLock {
                                    if (!item.installed()) fetch(item) { bytes, _ -> _state.value = State.Running(item.step, done, items.size, item.name, bytes) }
                                }
                                done++
                                _state.value = State.Running(item.step, done, items.size, item.name, 0)
                            } catch (e: IOException) {
                                failed = failed ?: failure(e, item).let { State.Failed(it.reason, item.step, done, items.size, item.name, it.detail) }
                            }
                        }
                    }
                }
            }
        } finally {
            val f = failed
            if (f != null) {
                _state.value = f
            } else if (todo.all { it.installed() }) {
                stamp.writeText("$VERSION/$abi")
                _state.value = State.Done
            } else {
                refresh()
            }
        }
    }

    /** Downloads and installs [item], reporting the bytes through [onProgress]; the temporary file never stays. */
    private suspend fun fetch(item: Item, onProgress: (Long, Long) -> Unit) {
        val tmp = File(marks, "download-${item.url.hashCode().toUInt()}.zip")
        try {
            download(item.url, tmp, onProgress)
            item.install(tmp)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        target.parentFile?.mkdirs()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) throw HttpError(conn.responseCode)
            val total = conn.contentLengthLong.coerceAtLeast(0)
            var read = 0L
            var shown = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // A launch cancelled from the overlay stops here, between chunks.
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read - shown > 256 * 1024) { shown = read; onProgress(read, total) }
                    }
                }
            }
            onProgress(read, total)
        } finally {
            conn.disconnect()
        }
    }

    /** Extracts the entries [rename] keeps, under the name it gives, never outside [dir]. */
    private fun unzip(zip: File, dir: File, rename: (String) -> String?) {
        dir.mkdirs()
        val root = dir.canonicalPath + File.separator
        var extracted = 0
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = rename(entry.name) ?: continue
                val target = File(dir, name)
                if (!target.canonicalPath.startsWith(root)) continue
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                // Written aside first, so a cut download never leaves a half core behind.
                val part = File(target.path + ".part")
                part.outputStream().use { input.copyTo(it) }
                if (!part.renameTo(target)) throw IOException("could not write ${target.name}")
                extracted++
            }
        }
        if (extracted == 0) throw IOException("empty archive")
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private fun failure(e: IOException, item: Item): SetupException {
        val (reason, detail) = when (e) {
            is UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> R.string.data_setup_no_internet to ""
            is SocketTimeoutException -> R.string.data_setup_timeout to ""
            is HttpError -> R.string.data_setup_http to e.code.toString()
            else -> R.string.data_setup_failed to (e.message ?: e.javaClass.simpleName)
        }
        return SetupException(reason, item.name ?: "", detail, e)
    }

    private companion object {
        const val VERSION = 1
        const val BUILDBOT = "https://buildbot.libretro.com"
        const val PARALLEL = 3
        const val TAG = "OpenEmuxSetup"
        val ABIS = setOf("arm64-v8a", "x86_64")
        val SYSTEM_PACKS = listOf("PPSSPP" to "PPSSPP", "Dolphin" to "Dolphin")
    }
}
