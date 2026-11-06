package com.openemux.mobile.share

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.CoreDef
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.Paths
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.RomMeta
import com.openemux.mobile.data.SCRATCH_SLOT
import com.openemux.mobile.data.SaveState
import com.openemux.mobile.data.SkinBackground
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinFrame
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.data.titleFromFileName
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** PNG, JPEG and WebP heads, enough for the receiver's image check. */
val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)
val WEBP = "RIFF".toByteArray() + byteArrayOf(1, 0, 0, 0) + "WEBPVP8 ".toByteArray()

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** A file with [bytes] and an mtime of [mtime] Unix seconds; folders made on the way. */
fun File.put(bytes: ByteArray, mtime: Long = 1_700_000_000L): File {
    parentFile?.mkdirs()
    writeBytes(bytes)
    setLastModified(mtime * 1000)
    return this
}

fun File.put(text: String, mtime: Long = 1_700_000_000L): File = put(text.toByteArray(), mtime)

/**
 * The app's folders in a temp dir, and a [ShareLibrary] over them that keeps
 * meta and play time in memory, the way [com.openemux.mobile.data.Library] does
 * on disk.
 */
class FakeLibrary(root: File = Files.createTempDirectory("share-lib").toFile()) : ShareLibrary {
    override val paths = Paths(File(root, "external"), File(root, "internal"))
    val meta = mutableMapOf<String, RomMeta>()
    val playtime = mutableMapOf<String, Long>()
    val received = mutableListOf<List<File>>()
    /** The core each console defaults to, as `ConsoleSettings.core`. */
    val consoleCore = mutableMapOf<Console, String>()
    var failOnReceived = false
    var failOnUpdate = false
    /** The skin folders there were each time a received skin was about to be imported. */
    val skinImports = mutableListOf<List<String>>()
    /** Which import (1 for the first) fails before anything is written, or null. */
    var failSkinImport: Int? = null

    init {
        paths.base.mkdirs()
        paths.internal.mkdirs()
    }

    fun rom(console: Console, name: String, bytes: ByteArray = name.toByteArray(), mtime: Long = 1_700_000_000L): Rom {
        val file = File(paths.roms(console), name).put(bytes, mtime)
        return Rom(romKey(console, file), console, file, titleFromFileName(name))
    }

    /** A core's `.so` and `.info`, as Setup leaves them. */
    fun installCore(core: CoreDef, corename: String, version: String, so: Boolean = true) {
        if (so) File(paths.cores, core.fileName).put("elf")
        File(paths.info, "${core.id}_libretro.info").put("display_name = \"x\"\ncorename = \"$corename\"\ndisplay_version = \"$version\"\n")
    }

    override fun romMeta(key: String) = meta[key] ?: RomMeta()
    override fun effectiveCore(rom: Rom): CoreDef = rom.console.core(romMeta(rom.key).core ?: consoleCore[rom.console])
    override fun playtimeSeconds(rom: Rom) = playtime[rom.key] ?: 0L
    override fun updateRom(key: String, change: (RomMeta) -> RomMeta) {
        if (failOnUpdate) throw IllegalStateException("meta.json is read-only")
        meta[key] = change(romMeta(key))
    }

    override fun onReceived(placed: List<File>) {
        if (failOnReceived) throw IllegalStateException("scan failed")
        received += placed
    }

    override fun beforeSkinImport() {
        skinImports += File(paths.base, "skins").walkTopDown().filter { it.name == SkinFormat.SKIN_JSON }.map { it.parentFile.name }.sorted().toList()
        if (skinImports.size == failSkinImport) throw IOException("meta.json is read-only")
    }

    /** One of the player's own skins, as the store keeps it: a folder with its skin.json and [images]. */
    fun ownSkin(id: String, skin: OpenEmuxSkin = testSkin(), images: Map<String, ByteArray> = mapOf("phone-portrait/a.png" to PNG)): File =
        File(skins.root(skin.console), id).apply {
            File(this, SkinFormat.SKIN_JSON).put(SkinFormat.toJson(skin))
            images.forEach { (path, bytes) -> File(this, path).put(bytes) }
        }

    /** The same rule as `Library.states`. */
    override fun states(rom: Rom): List<SaveState> {
        val prefix = "${rom.baseName}.state"
        return paths.states(rom.console).listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".png") }
            .mapNotNull { file ->
                val suffix = file.name.removePrefix(prefix)
                val slot = when {
                    suffix.isEmpty() -> 0
                    suffix == ".auto" -> -1
                    suffix.all { it.isDigit() } && suffix.toInt() != SCRATCH_SLOT -> suffix.toInt()
                    else -> return@mapNotNull null
                }
                SaveState(slot, file, null, file.lastModified())
            }
    }
}

/** A skin in OpenEmux's format with a phone's two layouts, two buttons each. */
fun testSkin(name: String = "Night", console: Console = Console.SFC, ids: List<SkinLayoutId> = listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)): OpenEmuxSkin {
    val layout = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)), SkinBackground(0xFF112233.toInt()),
        frame = SkinFrame(SkinRect(0f, 0f, 100f, 70f)),
        elements = listOf(
            SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f), colors = mapOf("button" to 0xFFCC0000.toInt())),
            SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(40f, 100f, 20f, 20f)),
        ),
    )
    return OpenEmuxSkin(console, name, ids.associateWith { layout })
}

/** [files] (path to bytes) zipped, as a skin travels. */
fun zipBytes(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
    ZipOutputStream(out).use { zip ->
        files.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
    }
}.toByteArray()

/** [skin] zipped with [images], as the sender's export writes it. */
fun skinZipBytes(skin: OpenEmuxSkin = testSkin(), images: Map<String, ByteArray> = mapOf("phone-portrait/a.png" to PNG)): ByteArray =
    zipBytes(mapOf(SkinFormat.SKIN_JSON to SkinFormat.toJson(skin).toByteArray()) + images)

/** A manifest built by hand, item by item, then parsed like the server does. */
class ManifestDsl {
    private val games = JSONArray()
    private var total = 0L
    private var count = 0

    fun game(id: String, console: String, title: String = id, metadata: JSONObject? = null, items: GameDsl.() -> Unit) {
        val dsl = GameDsl(id).apply(items)
        val game = JSONObject().put("id", id).put("console", console).put("title", title).put("items", dsl.items)
        metadata?.let { game.put("metadata", it) }
        games.put(game)
        total += dsl.total
        count += dsl.items.length()
    }

    fun json(): JSONObject = JSONObject().put("games", games).put("total_size", total).put("file_count", count)
}

class GameDsl(private val gameId: String) {
    val items = JSONArray()
    var total = 0L
    private var n = 0

    /** An item whose content is [bytes]; returns its id. */
    fun item(
        kind: String,
        name: String,
        bytes: ByteArray = name.toByteArray(),
        mtime: Long = 1_700_000_000L,
        hash: Boolean = true,
        role: String? = null,
        state: JSONObject? = null,
        id: String = "${gameId}i${++n}",
    ): String {
        val item = JSONObject().put("id", id).put("kind", kind).put("name", name).put("size", bytes.size).put("mtime", mtime)
        if (hash) item.put("sha256", sha256(bytes))
        role?.let { item.put("part", JSONObject().put("unit", gameId).put("role", it)) }
        state?.let { item.put("state", it) }
        items.put(item)
        total += bytes.size
        return id
    }
}

fun manifest(block: ManifestDsl.() -> Unit): Manifest = ManifestParser.parse(ManifestDsl().apply(block).json())

fun stateInfo(core: String, version: String, slot: Any): JSONObject =
    JSONObject().put("core", core).put("core_version", version).put("slot", slot)

/** A [SpaceProbe] with a fixed answer per folder prefix; everything else is one big store. */
class FakeSpace(private val stores: Map<String, Long> = emptyMap(), private val default: Long = Long.MAX_VALUE / 2) : SpaceProbe {
    override fun store(dir: File): Pair<Any, Long> {
        val match = stores.keys.firstOrNull { dir.path.startsWith(it) } ?: return "default" to default
        return match to stores.getValue(match)
    }
}
