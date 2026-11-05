package com.openemux.mobile.data

import com.openemux.mobile.launch.SkinBinds
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Why a skin was refused; [SkinFormatException.path] says where in skin.json (or the package) it went wrong. */
enum class SkinError {
    /** The file is not a zip. */
    BAD_PACKAGE,
    /** The folder or zip has no skin.json. */
    NO_SKIN_JSON,
    /** skin.json is larger than [SkinFormat.MAX_JSON_BYTES]. */
    TOO_LARGE,
    /** skin.json is not JSON, or its top level is not an object. */
    BAD_JSON,
    MISSING_KEY,
    /** A key holds the wrong kind of value (a string for a number, an empty name, ...). */
    BAD_VALUE,
    /** A format version this app does not read. */
    UNSUPPORTED_FORMAT,
    UNKNOWN_CONSOLE,
    NO_LAYOUTS,
    UNKNOWN_LAYOUT,
    UNKNOWN_TYPE,
    /** An input name no element type takes. */
    UNKNOWN_BIND,
    /** An input name the element's type does not take (a stick bound to "a"). */
    BIND_NOT_FOR_TYPE,
    /** A layout size or a rect with no area. */
    BAD_SIZE,
    OUT_OF_LAYOUT,
    /** Two elements with one id, or two images with one file name, in a layout. */
    DUPLICATE_ID,
    /** An id or image base name that is not a plain file name. */
    BAD_ID,
    BAD_COLOR,
    UNKNOWN_COLOR_PART,
    BAD_SHAPE,
    /** Not one game screen (two for the NDS). */
    BAD_SCREENS,
}

/** A skin [SkinFormat] refused: [error] is what is wrong, [path] where (`layouts.phone-portrait.elements[2].bind`). */
class SkinFormatException(val error: SkinError, val path: String, detail: String? = null) :
    Exception("$error at ${path.ifEmpty { "skin" }}" + (detail?.let { ": $it" } ?: ""))

/** The files of a skin, by path relative to the skin's root (`skin.json`, `phone-portrait/a.png`). */
interface SkinSource {
    val files: Set<String>

    /** The file's bytes, or null when the skin has no such file. */
    fun open(path: String): InputStream?
}

/** A skin unpacked in [dir]. */
class FolderSkinSource(private val dir: File) : SkinSource {
    override val files: Set<String> =
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    override fun open(path: String): InputStream? = if (path in files) File(dir, path).inputStream() else null
}

/**
 * A skin zipped in [file], read in place (nothing is extracted). The skin's files may sit at the zip's root or in a
 * single folder (what zipping the folder itself gives); entries whose name could leave the skin are ignored.
 */
class ZipSkinSource(private val file: File) : SkinSource {
    private val root: String
    override val files: Set<String>

    init {
        val names = ZipFile(file).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList() }
            .filter { SkinFormat.safePath(it) }
        val tops = names.map { it.substringBefore('/', "") }.toSet()
        root = if (SkinFormat.SKIN_JSON !in names && tops.size == 1 && tops.single().isNotEmpty()) tops.single() + "/" else ""
        files = names.filter { it.startsWith(root) }.map { it.removePrefix(root) }.toSet()
    }

    override fun open(path: String): InputStream? {
        if (path !in files) return null
        val zip = ZipFile(file)
        val stream = zip.getInputStream(zip.getEntry(root + path))
        return object : InputStream() {
            override fun read() = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int) = stream.read(b, off, len)
            override fun close() = zip.close()
        }
    }
}

/** A skin read from [source]: the model plus where each of its images is, for the ones the package has. */
class SkinPackage(val skin: OpenEmuxSkin, val source: SkinSource) {
    /** The path of [file] in [layout]'s folder, or null when the package lacks it (the colours are drawn instead). */
    fun image(layout: SkinLayoutId, file: String): String? = "${layout.id}/$file".takeIf { it in source.files }

    fun background(layout: SkinLayoutId): String? = skin.layouts[layout]?.let { image(layout, it.background.imageFile) }

    fun frame(layout: SkinLayoutId): String? = skin.layouts[layout]?.frame?.let { image(layout, it.imageFile) }

    /** Each of [element]'s image files ([SkinElement.imageFiles] order) as a path, or null for a colour fallback. */
    fun images(layout: SkinLayoutId, element: SkinElement): List<String?> = element.imageFiles.map { image(layout, it) }
}

/**
 * Reads, checks and writes OpenEmux's skin format (docs/skin-format.md). Reading refuses a skin it cannot draw with a
 * [SkinFormatException]; writing gives back a skin.json the reader turns into the same [OpenEmuxSkin].
 */
object SkinFormat {
    const val VERSION = 1
    const val SKIN_JSON = "skin.json"
    const val MAX_JSON_BYTES = 1 shl 20

    /** The input names of the quick actions on the frame. */
    val ACTION_BINDS = setOf(
        "menu", "quickSave", "quickLoad", "fastForward", "toggleFastForward", "turbo", "slowMotion", "screenshot",
        "restart", "reset", "screens",
    )
    val STICK_BINDS = setOf("analogStick", "rightAnalogStick")
    val KEYPAD_BINDS = (0..9).map { "keypad$it" }.toSet() + setOf("keypadStar", "keypadHash")
    const val DPAD_BIND = "dpad"

    /** A button takes any name a Delta or Manic skin may use, plus the format's own quick actions. */
    val BUTTON_BINDS = SkinBinds.NAMES + ACTION_BINDS

    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val COLOR = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")

    fun bindsFor(type: SkinElementType): Set<String> = when (type) {
        SkinElementType.BUTTON -> BUTTON_BINDS
        SkinElementType.DPAD -> setOf(DPAD_BIND)
        SkinElementType.STICK -> STICK_BINDS
        SkinElementType.ACTION -> ACTION_BINDS
        SkinElementType.KEYPAD -> KEYPAD_BINDS
    }

    /**
     * Whether a zip entry name stays inside the skin (relative, no "..", no backslash) and is not the `__MACOSX/` copy
     * of the resource forks macOS adds when it zips a folder.
     */
    fun safePath(name: String) =
        !name.startsWith("/") && !name.startsWith("__MACOSX/") && '\\' !in name && name.split('/').none { it == ".." }

    fun readFolder(dir: File): SkinPackage = read(FolderSkinSource(dir))

    /** A file that is not a zip, or one that fails while skin.json is read from it (a bad entry), is [SkinError.BAD_PACKAGE]. */
    fun readZip(file: File): SkinPackage = try {
        read(ZipSkinSource(file))
    } catch (e: java.io.IOException) {
        throw SkinFormatException(SkinError.BAD_PACKAGE, "", e.message)
    }

    fun read(source: SkinSource): SkinPackage {
        val stream = source.open(SKIN_JSON) ?: throw SkinFormatException(SkinError.NO_SKIN_JSON, "")
        val bytes = stream.use { it.readNBytesCompat(MAX_JSON_BYTES + 1) }
        if (bytes.size > MAX_JSON_BYTES) throw SkinFormatException(SkinError.TOO_LARGE, "")
        return SkinPackage(parse(String(bytes, Charsets.UTF_8)), source)
    }

    /** [json] (a skin.json's text) as a skin. */
    fun parse(json: String): OpenEmuxSkin {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw SkinFormatException(SkinError.BAD_JSON, "", e.message)
        }
        val format = root.int("format", "format")
        if (format != VERSION) throw SkinFormatException(SkinError.UNSUPPORTED_FORMAT, "format", "$format")
        val consoleId = root.string("console", "console")
        val console = Console.byId(consoleId) ?: throw SkinFormatException(SkinError.UNKNOWN_CONSOLE, "console", consoleId)
        val name = root.string("name", "name")
        if (name.isBlank()) throw SkinFormatException(SkinError.BAD_VALUE, "name", "empty")
        val layoutsJson = root.obj("layouts", "layouts")
        if (layoutsJson.length() == 0) throw SkinFormatException(SkinError.NO_LAYOUTS, "layouts")
        val layouts = linkedMapOf<SkinLayoutId, SkinLayout>()
        for (key in layoutsJson.keys().asSequence().toList()) {
            val path = "layouts.$key"
            val id = SkinLayoutId.byId(key) ?: throw SkinFormatException(SkinError.UNKNOWN_LAYOUT, path, key)
            layouts[id] = layout(layoutsJson.obj(key, path), path, console)
        }
        // A fixed order, whatever the file's: the model (and the round trip) does not depend on how it was written.
        return OpenEmuxSkin(console, name, SkinLayoutId.entries.filter { it in layouts }.associateWith { layouts.getValue(it) }, format)
    }

    private fun layout(json: JSONObject, path: String, console: Console): SkinLayout {
        val sizeJson = json.obj("size", "$path.size")
        val size = SkinSize(sizeJson.int("width", "$path.size.width"), sizeJson.int("height", "$path.size.height"))
        if (size.width <= 0 || size.height <= 0) throw SkinFormatException(SkinError.BAD_SIZE, "$path.size")
        val screensJson = json.array("screens", "$path.screens")
        val screenCount = if (console == Console.NDS) 2 else 1
        if (screensJson.length() != screenCount) {
            throw SkinFormatException(SkinError.BAD_SCREENS, "$path.screens", "${screensJson.length()}, $screenCount expected")
        }
        val screens = (0 until screensJson.length()).map { rect(screensJson.obj(it, "$path.screens[$it]"), "$path.screens[$it]", size) }
        val background = json.optObj("background", "$path.background")?.let {
            SkinBackground(it.optColor("color", "$path.background.color"), it.optImage("image", "$path.background.image"))
        } ?: SkinBackground()
        val frame = json.optObj("frame", "$path.frame")?.let { frame(it, "$path.frame", size) }
        val elementsJson = json.optArray("elements", "$path.elements") ?: JSONArray()
        val elements = (0 until elementsJson.length()).map {
            element(elementsJson.obj(it, "$path.elements[$it]"), "$path.elements[$it]", size)
        }
        val ids = HashSet<String>()
        elements.forEachIndexed { i, e ->
            if (!ids.add(e.id)) throw SkinFormatException(SkinError.DUPLICATE_ID, "$path.elements[$i].id", e.id)
        }
        // Each image file with the key that names it, so a clash points at the second one.
        val owners = listOf(background.imageFile to "$path.background") +
            listOfNotNull(frame?.let { it.imageFile to "$path.frame" }) +
            elements.flatMapIndexed { i, e -> e.imageFiles.map { it to "$path.elements[$i]" } }
        val files = HashSet<String>()
        owners.forEach { (file, owner) ->
            if (!files.add(file)) throw SkinFormatException(SkinError.DUPLICATE_ID, owner, "image $file used twice")
        }
        return SkinLayout(size, screens, background, frame, elements)
    }

    private fun frame(json: JSONObject, path: String, size: SkinSize): SkinFrame {
        val colorsJson = json.optObj("colors", "$path.colors")
        val colors = colorsJson?.let { c ->
            c.keys().forEach {
                if (it !in FRAME_PARTS) throw SkinFormatException(SkinError.UNKNOWN_COLOR_PART, "$path.colors.$it", it)
            }
            SkinFrameColors(
                c.optColor("frame", "$path.colors.frame"),
                c.optColor("icons", "$path.colors.icons"),
                c.optColor("active", "$path.colors.active"),
                c.optText("activeIcon", "$path.colors.activeIcon")?.let {
                    SkinIconTone.byId(it) ?: throw SkinFormatException(SkinError.BAD_VALUE, "$path.colors.activeIcon", it)
                },
            )
        } ?: SkinFrameColors()
        return SkinFrame(rect(json, path, size), colors, json.optImage("image", "$path.image"))
    }

    private fun element(json: JSONObject, path: String, size: SkinSize): SkinElement {
        val id = json.string("id", "$path.id")
        if (!ID.matches(id)) throw SkinFormatException(SkinError.BAD_ID, "$path.id", id)
        val typeId = json.string("type", "$path.type")
        val type = SkinElementType.byId(typeId) ?: throw SkinFormatException(SkinError.UNKNOWN_TYPE, "$path.type", typeId)
        val bind = if (type == SkinElementType.DPAD) json.optText("bind", "$path.bind") ?: DPAD_BIND else json.string("bind", "$path.bind")
        if (bind !in bindsFor(type)) {
            val known = SkinElementType.entries.any { bind in bindsFor(it) }
            throw SkinFormatException(if (known) SkinError.BIND_NOT_FOR_TYPE else SkinError.UNKNOWN_BIND, "$path.bind", bind)
        }
        val shape = json.optText("shape", "$path.shape")?.let {
            SkinShape.byId(it) ?: throw SkinFormatException(SkinError.BAD_SHAPE, "$path.shape", it)
        }
        val colors = linkedMapOf<String, Int>()
        json.optObj("colors", "$path.colors")?.let { c ->
            for (part in c.keys().asSequence().toList()) {
                if (part !in type.colorParts) throw SkinFormatException(SkinError.UNKNOWN_COLOR_PART, "$path.colors.$part", part)
                // A part set to null reads as left out, like any other key.
                colors[part] = color(c.optText(part, "$path.colors.$part") ?: continue, "$path.colors.$part")
            }
        }
        return SkinElement(
            id, type, bind, rect(json, path, size),
            group = json.optText("group", "$path.group"),
            shape = shape,
            label = json.optText("label", "$path.label"),
            // The parts in the type's order, whatever the file's.
            colors = type.colorParts.filter { it in colors }.associateWith { colors.getValue(it) },
            image = json.optImage("image", "$path.image"),
        )
    }

    private fun rect(json: JSONObject, path: String, size: SkinSize): SkinRect {
        val r = SkinRect(json.float("x", "$path.x"), json.float("y", "$path.y"), json.float("width", "$path.width"), json.float("height", "$path.height"))
        if (r.width <= 0f || r.height <= 0f) throw SkinFormatException(SkinError.BAD_SIZE, path)
        if (!r.inside(size)) throw SkinFormatException(SkinError.OUT_OF_LAYOUT, path, "$r outside ${size.width}x${size.height}")
        return r
    }

    private val FRAME_PARTS = setOf("frame", "icons", "active", "activeIcon")

    private fun JSONObject.value(key: String, path: String): Any =
        opt(key)?.takeIf { it != JSONObject.NULL } ?: throw SkinFormatException(SkinError.MISSING_KEY, path)

    private inline fun <reified T> JSONObject.typed(key: String, path: String): T =
        value(key, path) as? T ?: throw SkinFormatException(SkinError.BAD_VALUE, path, "${opt(key)}")

    private inline fun <reified T> JSONObject.optTyped(key: String, path: String): T? =
        if (opt(key) == null || opt(key) == JSONObject.NULL) null else typed(key, path)

    private fun JSONObject.obj(key: String, path: String): JSONObject = typed(key, path)
    private fun JSONObject.optObj(key: String, path: String): JSONObject? = optTyped(key, path)
    private fun JSONObject.array(key: String, path: String): JSONArray = typed(key, path)
    private fun JSONObject.optArray(key: String, path: String): JSONArray? = optTyped(key, path)
    private fun JSONObject.string(key: String, path: String): String = typed(key, path)
    private fun JSONObject.optText(key: String, path: String): String? = optTyped(key, path)
    private fun JSONObject.float(key: String, path: String): Float = typed<Number>(key, path).toFloat()

    private fun JSONObject.int(key: String, path: String): Int {
        val n = typed<Number>(key, path)
        if (n.toDouble() != n.toInt().toDouble()) throw SkinFormatException(SkinError.BAD_VALUE, path, "$n")
        return n.toInt()
    }

    private fun JSONArray.obj(i: Int, path: String): JSONObject =
        opt(i) as? JSONObject ?: throw SkinFormatException(SkinError.BAD_VALUE, path, "${opt(i)}")

    private fun JSONObject.optColor(key: String, path: String): Int? = optText(key, path)?.let { color(it, path) }

    /** #RRGGBB or #RRGGBBAA as ARGB. */
    private fun color(text: String, path: String): Int {
        if (!COLOR.matches(text)) throw SkinFormatException(SkinError.BAD_COLOR, path, text)
        val rgb = text.substring(1, 7).toLong(16)
        val alpha = if (text.length == 9) text.substring(7, 9).toLong(16) else 0xFF
        return ((alpha shl 24) or rgb).toInt()
    }

    private fun JSONObject.optImage(key: String, path: String): String? = optText(key, path)?.also {
        if (!ID.matches(it)) throw SkinFormatException(SkinError.BAD_ID, path, it)
    }

    /** [skin] as a skin.json: keys in a fixed order, two-space indents, optional keys left out when unset. */
    fun toJson(skin: OpenEmuxSkin): String {
        val root = linkedMapOf<String, Any>(
            "format" to skin.format,
            "console" to skin.console.id,
            "name" to skin.name,
            "forms" to skin.forms.map { it.id },
            "layouts" to skin.layouts.entries.associate { (id, layout) -> id.id to layoutJson(layout) },
        )
        return StringBuilder().also { emit(it, root, "") }.append('\n').toString()
    }

    private fun layoutJson(layout: SkinLayout): Map<String, Any> = buildMap {
        put("size", mapOf("width" to layout.size.width, "height" to layout.size.height))
        put("screens", layout.screens.map { rectJson(it) })
        val bg = buildMap {
            layout.background.color?.let { put("color", colorJson(it)) }
            layout.background.image?.let { put("image", it) }
        }
        if (bg.isNotEmpty()) put("background", bg)
        layout.frame?.let { frame ->
            put("frame", buildMap {
                putAll(rectJson(frame.rect))
                val c = frame.colors
                val colors = buildMap {
                    c.frame?.let { put("frame", colorJson(it)) }
                    c.icons?.let { put("icons", colorJson(it)) }
                    c.active?.let { put("active", colorJson(it)) }
                    c.activeIcon?.let { put("activeIcon", it.id) }
                }
                if (colors.isNotEmpty()) put("colors", colors)
                frame.image?.let { put("image", it) }
            })
        }
        put("elements", layout.elements.map { elementJson(it) })
    }

    private fun elementJson(e: SkinElement): Map<String, Any> = buildMap {
        put("id", e.id)
        put("type", e.type.id)
        if (e.type != SkinElementType.DPAD) put("bind", e.bind)
        e.group?.let { put("group", it) }
        putAll(rectJson(e.rect))
        e.shape?.let { put("shape", it.id) }
        e.label?.let { put("label", it) }
        if (e.colors.isNotEmpty()) put("colors", e.colors.mapValues { colorJson(it.value) })
        e.image?.let { put("image", it) }
    }

    private fun rectJson(r: SkinRect) = linkedMapOf<String, Any>("x" to r.x, "y" to r.y, "width" to r.width, "height" to r.height)

    /** #RRGGBB, or #RRGGBBAA when [argb] is not opaque. */
    private fun colorJson(argb: Int): String {
        val rgb = "#%06X".format(argb and 0xFFFFFF)
        val alpha = argb ushr 24
        return if (alpha == 0xFF) rgb else rgb + "%02X".format(alpha)
    }

    private fun emit(out: StringBuilder, value: Any, indent: String) {
        val inner = "$indent  "
        when (value) {
            is Map<*, *> -> {
                out.append("{\n")
                value.entries.forEachIndexed { i, (k, v) ->
                    out.append(inner).append(JSONObject.quote(k as String)).append(": ")
                    emit(out, v!!, inner)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(indent).append('}')
            }
            is List<*> -> if (value.isEmpty()) out.append("[]") else {
                out.append("[\n")
                value.forEachIndexed { i, v ->
                    out.append(inner)
                    emit(out, v!!, inner)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(indent).append(']')
            }
            is String -> out.append(JSONObject.quote(value))
            // A whole number reads as one: 120, not 120.0.
            is Float -> out.append(if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString())
            else -> out.append(value.toString())
        }
    }

    /** Up to [limit] bytes of the stream: InputStream.readNBytes is API 33. */
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
