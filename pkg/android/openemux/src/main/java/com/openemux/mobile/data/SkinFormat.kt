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
    /** Two elements with one id, two images with one file name, or one image as both PNG and WebP, in a layout. */
    DUPLICATE_ID,
    /** An id or image base name that is not a plain file name. */
    BAD_ID,
    BAD_COLOR,
    UNKNOWN_COLOR_PART,
    BAD_SHAPE,
    /** Not one game screen (two for the NDS), or not as many as a screen-layout page shows. */
    BAD_SCREENS,
    /** A screen-layout page other than `top`, `bottom` and `side`. */
    UNKNOWN_SCREEN_LAYOUT,
    /** Screen-layout pages in a skin for a console other than the NDS. */
    SCREEN_LAYOUTS_NOT_NDS,
    /** Screen-layout pages without all three of `top`, `bottom` and `side`. */
    INCOMPLETE_SCREEN_LAYOUTS,
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
    /**
     * The path of [file] (the model's `<name>.png`) in [layout]'s folder, as PNG or as WebP (`<name>.webp`, issue 806),
     * or null when the package has neither (the colours are drawn instead).
     */
    fun image(layout: SkinLayoutId, file: String): String? =
        SkinFormat.imageNames(file).map { "${layout.id}/$it" }.firstOrNull { it in source.files }

    fun background(layout: SkinLayoutId): String? = skin.layouts[layout]?.let { image(layout, it.background.imageFile) }

    fun frame(layout: SkinLayoutId): String? = skin.layouts[layout]?.frame?.let { image(layout, it.imageFile) }

    /** Each of [element]'s image files ([SkinElement.imageFiles] order) as a path, or null for a colour fallback. */
    fun images(layout: SkinLayoutId, element: SkinElement): List<String?> = element.imageFiles.map { image(layout, it) }

    /** The path of [file] in [layout]'s keypad page folder, or null when the package lacks it. */
    fun keypadImage(layout: SkinLayoutId, file: String): String? = image(layout, "${SkinKeypadPage.FOLDER}/$file")

    /**
     * The page's background image: the keypad page's own ([keypadPage]) when it names one, in its folder; else the
     * layout's.
     */
    fun background(layout: SkinLayoutId, keypadPage: Boolean): String? {
        val own = skin.layouts[layout]?.keypad?.background?.takeIf { keypadPage } ?: return background(layout)
        return keypadImage(layout, own.imageFile)
    }

    /** [element]'s images on the page shown: in the keypad page's folder ([keypadPage]) or the layout's. */
    fun images(layout: SkinLayoutId, element: SkinElement, keypadPage: Boolean): List<String?> =
        if (keypadPage) element.imageFiles.map { keypadImage(layout, it) } else images(layout, element)

    /** The path of [file] in the folder of [layout]'s screen-layout page [page], or null when the package lacks it. */
    fun screenImage(layout: SkinLayoutId, page: String, file: String): String? = image(layout, "$page/$file")

    /** Screen-layout page [page]'s background image: its own when it has a background, in its folder; else the layout's. */
    fun screenBackground(layout: SkinLayoutId, page: String): String? {
        val own = skin.layouts[layout]?.screenLayouts?.get(page)?.background ?: return background(layout)
        return screenImage(layout, page, own.imageFile)
    }

    /** Screen-layout page [page]'s frame image, in its folder; null when it has no frame or the package lacks it. */
    fun screenFrame(layout: SkinLayoutId, page: String): String? =
        skin.layouts[layout]?.screenLayouts?.get(page)?.frame?.let { screenImage(layout, page, it.imageFile) }

    /** [element]'s images on screen-layout page [page], in its folder. */
    fun screenImages(layout: SkinLayoutId, page: String, element: SkinElement): List<String?> =
        element.imageFiles.map { screenImage(layout, page, it) }
}

/**
 * Reads, checks and writes OpenEmux's skin format (docs/skin-format.md). Reading refuses a skin it cannot draw with a
 * [SkinFormatException]; writing gives back a skin.json the reader turns into the same [OpenEmuxSkin].
 */
object SkinFormat {
    const val VERSION = 1
    const val SKIN_JSON = "skin.json"
    const val MAX_JSON_BYTES = 1 shl 20

    /** An image's extension in the model's names ([SkinElement.imageFiles]), and the other one a package may use. */
    const val PNG = ".png"
    const val WEBP = ".webp"

    /**
     * The names [file] (a model image name, `<name>.png`) may have in a package, in the order they are looked for:
     * `<name>.png`, then `<name>.webp` (issue 806: the bundled colour variants' art is lossy WebP).
     */
    fun imageNames(file: String): List<String> = listOf(file, file.removeSuffix(PNG) + WEBP)

    /** The name the model gives the image at [path] in a package: `<name>.png`, also for one kept as `<name>.webp`. */
    fun modelName(path: String): String = if (path.endsWith(WEBP)) path.removeSuffix(WEBP) + PNG else path

    /** A package's [files] under the names the model gives its images ([modelName]). */
    fun modelFiles(files: Set<String>): Set<String> = files.map(::modelName).toSet()

    /** The input names of the quick actions on the frame. */
    val ACTION_BINDS = setOf(
        "menu", "quickSave", "quickLoad", "fastForward", "toggleFastForward", "turbo", "slowMotion", "screenshot",
        "restart", "reset", "screens",
    )
    val STICK_BINDS = setOf("analogStick", "rightAnalogStick")
    val KEYPAD_BINDS = (0..9).map { "keypad$it" }.toSet() + setOf("keypadStar", "keypadHash")
    const val DPAD_BIND = "dpad"

    /** A D-pad binds the D-pad, or the console's second one (the Virtual Boy's right pad, SkinBinds.SECOND_DPAD). */
    val DPAD_BINDS = setOf(DPAD_BIND, SkinBinds.DPAD2)

    /** A button that opens a layout's keypad page and, on that page, closes it (docs/skin-format.md). */
    const val KEYPAD_TOGGLE = "keypadToggle"

    /** A button takes any name a Delta or Manic skin may use, plus the format's own quick actions and keypad toggle. */
    val BUTTON_BINDS = SkinBinds.NAMES + ACTION_BINDS + KEYPAD_TOGGLE

    private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val COLOR = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")

    fun bindsFor(type: SkinElementType): Set<String> = when (type) {
        SkinElementType.BUTTON -> BUTTON_BINDS
        SkinElementType.DPAD -> DPAD_BINDS
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
        val skin = parse(String(bytes, Charsets.UTF_8))
        oneFormatPerImage(skin, source.files)
        return SkinPackage(skin, source)
    }

    /** Refuses a layout folder (its keypad page's included) holding one image as both `<name>.png` and `<name>.webp`. */
    private fun oneFormatPerImage(skin: OpenEmuxSkin, files: Set<String>) {
        for (id in skin.layouts.keys) {
            val webp = files.filter { it.startsWith("${id.id}/") && it.endsWith(WEBP) }.sorted()
            val both = webp.firstOrNull { it.removeSuffix(WEBP) + PNG in files } ?: continue
            val name = both.removePrefix("${id.id}/").removeSuffix(WEBP)
            throw SkinFormatException(SkinError.DUPLICATE_ID, "layouts.${id.id}", "image $name is both $name$PNG and $name$WEBP")
        }
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
        val allCombos = root.optArray("combos", "combos")?.let { combos(it, "combos") }.orEmpty()
        val combos = allCombos.filterValues { SkinCombos.valid(it) }
        val layoutsJson = root.obj("layouts", "layouts")
        if (layoutsJson.length() == 0) throw SkinFormatException(SkinError.NO_LAYOUTS, "layouts")
        val layouts = linkedMapOf<SkinLayoutId, SkinLayout>()
        for (key in layoutsJson.keys().asSequence().toList()) {
            val path = "layouts.$key"
            val id = SkinLayoutId.byId(key) ?: throw SkinFormatException(SkinError.UNKNOWN_LAYOUT, path, key)
            layouts[id] = layout(layoutsJson.obj(key, path), path, console, combos)
        }
        // A combo's label is the names the skin gives its buttons, on any layout.
        val labels = combos.mapValues { (_, buttons) -> SkinCombos.label(buttons, layouts.values) }
        val labelled = layouts.mapValues { (_, l) ->
            l.copy(elements = l.elements.map { e -> if (e.isCombo) e.copy(label = labels.getValue(e.id)) else e })
        }
        // A fixed order, whatever the file's: the model (and the round trip) does not depend on how it was written.
        return OpenEmuxSkin(
            console, name, SkinLayoutId.entries.filter { it in labelled }.associateWith { labelled.getValue(it) }, format,
            leftOutCombos = allCombos.keys - combos.keys,
        )
    }

    /**
     * The root `combos` (issue 842): each combo's id and the inputs it presses, by id. A combo this app cannot press
     * (an input it does not know or a combo may not press, fewer than [SkinCombos.MIN] or more than [SkinCombos.MAX]
     * buttons, one input twice) is left out rather than refused, as an older app leaves out every combo: a newer app
     * may allow more.
     */
    private fun combos(json: JSONArray, path: String): Map<String, List<String>> {
        val out = linkedMapOf<String, List<String>>()
        for (i in 0 until json.length()) {
            val at = "$path[$i]"
            val c = json.obj(i, at)
            val id = c.string("id", "$at.id")
            if (!ID.matches(id)) throw SkinFormatException(SkinError.BAD_ID, "$at.id", id)
            if (id in out) throw SkinFormatException(SkinError.DUPLICATE_ID, "$at.id", id)
            val buttonsJson = c.array("buttons", "$at.buttons")
            val buttons = (0 until buttonsJson.length()).map {
                buttonsJson.opt(it) as? String ?: throw SkinFormatException(SkinError.BAD_VALUE, "$at.buttons[$it]", "${buttonsJson.opt(it)}")
            }
            out[id] = buttons
        }
        return out
    }

    /**
     * A layout's `combos` (issue 842): where each of [combos] is on it, as a button element pressing its inputs. A
     * place for a combo the skin does not have (or one left out) is left out; an id one of [elements] has is refused.
     */
    private fun layoutCombos(json: JSONArray, path: String, size: SkinSize, combos: Map<String, List<String>>, elements: List<SkinElement>): List<SkinElement> {
        val ids = elements.map { it.id }.toHashSet()
        val out = mutableListOf<SkinElement>()
        for (i in 0 until json.length()) {
            val at = "$path.combos[$i]"
            val c = json.obj(i, at)
            val id = c.string("id", "$at.id")
            if (!ID.matches(id)) throw SkinFormatException(SkinError.BAD_ID, "$at.id", id)
            if (!ids.add(id)) throw SkinFormatException(SkinError.DUPLICATE_ID, "$at.id", id)
            val buttons = combos[id] ?: continue
            // Read as the button it is drawn as; its inputs, its look and its label are the combo's.
            val asButton = JSONObject(c.toString()).put("type", SkinElementType.BUTTON.id).put("bind", buttons.first())
            listOf("image", "art", "group", "label", "shape", "corner").forEach { asButton.remove(it) }
            val e = element(asButton, at, size)
            val outline = ComboOutline.read(c.optText("shape", "$at.shape"), c.optText("corner", "$at.corner"))
            // A triangle is touched along its rect's diagonal (the overlay's _triangle): never turned, or the touch
            // would miss what is drawn.
            val rotation = if (outline.shape == SkinShape.TRIANGLE) 0f else e.rotation
            out += e.copy(bind = SkinCombos.bind(buttons), shape = outline.shape, corner = outline.corner, rotation = rotation)
        }
        return out
    }

    private fun layout(json: JSONObject, path: String, console: Console, combos: Map<String, List<String>> = emptyMap()): SkinLayout {
        val sizeJson = json.obj("size", "$path.size")
        val size = SkinSize(sizeJson.int("width", "$path.size.width"), sizeJson.int("height", "$path.size.height"))
        if (size.width <= 0 || size.height <= 0) throw SkinFormatException(SkinError.BAD_SIZE, "$path.size")
        val screens = screens(json, path, size, if (console == Console.NDS) 2 else 1)
        val background = background(json, path) ?: SkinBackground()
        val frame = json.optObj("frame", "$path.frame")?.let { frame(it, "$path.frame", size) }
        val elements = elements(json.optArray("elements", "$path.elements") ?: JSONArray(), path, size)
        // Each image file with the key that names it, so a clash points at the second one.
        unique(
            listOf(background.imageFile to "$path.background") + listOfNotNull(frame?.let { it.imageFile to "$path.frame" }) +
                imageOwners(elements, path),
        )
        val placed = json.optArray("combos", "$path.combos")?.let { layoutCombos(it, path, size, combos, elements) }.orEmpty()
        val keypad = json.optObj("keypad", "$path.keypad")?.let { keypad(it, "$path.keypad", size) }
        val pages = json.optObj("screenLayouts", "$path.screenLayouts")?.let { screenLayouts(it, "$path.screenLayouts", size, console) }
        return SkinLayout(size, screens, background, frame, elements + placed, keypad, pages.orEmpty())
    }

    /** The [count] game screens of the page at [path]. */
    private fun screens(json: JSONObject, path: String, size: SkinSize, count: Int): List<SkinRect> {
        val screensJson = json.array("screens", "$path.screens")
        if (screensJson.length() != count) {
            throw SkinFormatException(SkinError.BAD_SCREENS, "$path.screens", "${screensJson.length()}, $count expected")
        }
        return (0 until screensJson.length()).map { rect(screensJson.obj(it, "$path.screens[$it]"), "$path.screens[$it]", size) }
    }

    /** An NDS layout's screen-layout pages: all of `top`, `bottom` and `side`, in that order, and no other. */
    private fun screenLayouts(json: JSONObject, path: String, size: SkinSize, console: Console): Map<String, SkinScreenPage> {
        if (console != Console.NDS) throw SkinFormatException(SkinError.SCREEN_LAYOUTS_NOT_NDS, path, console.id)
        val keys = json.keys().asSequence().toList()
        keys.firstOrNull { it !in SkinScreenPage.SCREENS }?.let { throw SkinFormatException(SkinError.UNKNOWN_SCREEN_LAYOUT, "$path.$it", it) }
        SkinScreenPage.IDS.firstOrNull { it !in keys }?.let { throw SkinFormatException(SkinError.INCOMPLETE_SCREEN_LAYOUTS, "$path.$it", "missing") }
        return SkinScreenPage.SCREENS.entries.associate { (id, count) -> id to screenPage(json.obj(id, "$path.$id"), "$path.$id", size, count) }
    }

    /** One screen-layout page: its [count] screens, and optionally its own background, frame and controls. */
    private fun screenPage(json: JSONObject, path: String, size: SkinSize, count: Int): SkinScreenPage {
        val screens = screens(json, path, size, count)
        // An empty background object is no background: the layout's stays, as when it is left out.
        val background = background(json, path)?.takeIf { it.color != null || it.image != null }
        val frame = json.optObj("frame", "$path.frame")?.let { frame(it, "$path.frame", size) }
        val elements = elements(json.optArray("elements", "$path.elements") ?: JSONArray(), path, size)
        unique(
            listOfNotNull(background?.let { it.imageFile to "$path.background" }, frame?.let { it.imageFile to "$path.frame" }) +
                imageOwners(elements, path),
        )
        return SkinScreenPage(screens, background, frame, elements)
    }

    /** A layout's keypad page: controls of its own and, optionally, its own background; its images in its own folder. */
    private fun keypad(json: JSONObject, path: String, size: SkinSize): SkinKeypadPage {
        // An empty background object is no background: the layout's stays, as when it is left out.
        val background = background(json, path)?.takeIf { it.color != null || it.image != null }
        val elements = elements(json.array("elements", "$path.elements"), path, size)
        unique(listOfNotNull(background?.let { it.imageFile to "$path.background" }) + imageOwners(elements, path))
        return SkinKeypadPage(elements, background)
    }

    /** The `background` object of the page at [path], or null when it has none. */
    private fun background(json: JSONObject, path: String): SkinBackground? = json.optObj("background", "$path.background")?.let {
        SkinBackground(it.optColor("color", "$path.background.color"), it.optImage("image", "$path.background.image"))
    }

    /** The elements of a page at [path], their ids unique in it. */
    private fun elements(json: JSONArray, path: String, size: SkinSize): List<SkinElement> {
        val elements = (0 until json.length()).map { element(json.obj(it, "$path.elements[$it]"), "$path.elements[$it]", size) }
        val ids = HashSet<String>()
        elements.forEachIndexed { i, e ->
            if (!ids.add(e.id)) throw SkinFormatException(SkinError.DUPLICATE_ID, "$path.elements[$i].id", e.id)
        }
        return elements
    }

    private fun imageOwners(elements: List<SkinElement>, path: String) =
        elements.flatMapIndexed { i, e -> e.imageFiles.map { it to "$path.elements[$i]" } }

    /** Refuses a page whose images clash: each file with the key that names it, the second one is pointed at. */
    private fun unique(owners: List<Pair<String, String>>) {
        val files = HashSet<String>()
        owners.forEach { (file, owner) ->
            if (!files.add(file)) throw SkinFormatException(SkinError.DUPLICATE_ID, owner, "image $file used twice")
        }
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
        val radius = json.optFloat("radius", "$path.radius") ?: 0f
        if (radius < 0f) throw SkinFormatException(SkinError.BAD_VALUE, "$path.radius", "$radius")
        return SkinFrame(rect(json, path, size), colors, json.optImage("image", "$path.image"), radius)
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
            // A triangle is a combo button's alone (issue 842), and a combo's shape is read apart (layoutCombos).
            SkinShape.byId(it)?.takeIf { s -> s != SkinShape.TRIANGLE } ?: throw SkinFormatException(SkinError.BAD_SHAPE, "$path.shape", it)
        }
        val colors = linkedMapOf<String, Int>()
        json.optObj("colors", "$path.colors")?.let { c ->
            for (part in c.keys().asSequence().toList()) {
                if (part !in type.colorParts) throw SkinFormatException(SkinError.UNKNOWN_COLOR_PART, "$path.colors.$part", part)
                // A part set to null reads as left out, like any other key.
                colors[part] = color(c.optText(part, "$path.colors.$part") ?: continue, "$path.colors.$part")
            }
        }
        val rect = rect(json, path, size)
        fun bad(key: String, value: Any?): Nothing = throw SkinFormatException(SkinError.BAD_VALUE, "$path.$key", "$value")
        val radius = json.optFloat("radius", "$path.radius")?.also { if (it < 0f) bad("radius", it) }
        val rotation = json.optFloat("rotation", "$path.rotation") ?: 0f
        if (kotlin.math.abs(rotation) > SkinShapes.MAX_TURN) bad("rotation", rotation)
        // The relief leaves the face some of the control: a depth as tall as the control would draw no button.
        val depth = json.optFloat("depth", "$path.depth") ?: 0f
        if (depth < 0f || depth > rect.height * (1f - SkinElement.MIN_FACE)) bad("depth", depth)
        val labelSize = json.optFloat("labelSize", "$path.labelSize")?.also { if (it <= 0f) bad("labelSize", it) }
        val labelOffset = json.optObj("labelOffset", "$path.labelOffset")?.let {
            SkinPoint(it.float("x", "$path.labelOffset.x"), it.float("y", "$path.labelOffset.y"))
        }
        val art = json.optText("art", "$path.art")?.let { SkinArt.byId(it) ?: bad("art", it) } ?: SkinArt.FULL
        return SkinElement(
            id, type, bind, rect,
            group = json.optText("group", "$path.group"),
            shape = shape,
            label = json.optText("label", "$path.label"),
            // The parts in the type's order, whatever the file's.
            colors = type.colorParts.filter { it in colors }.associateWith { colors.getValue(it) },
            image = json.optImage("image", "$path.image"),
            radius = radius, rotation = rotation, depth = depth, labelSize = labelSize, labelOffset = labelOffset, art = art,
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
    private fun JSONObject.optFloat(key: String, path: String): Float? = optTyped<Number>(key, path)?.toFloat()

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

    /** One layout as its entry in a skin.json's `layouts` (the editor adding a layout, issue 785). */
    fun layoutText(layout: SkinLayout): String = StringBuilder().also { emit(it, layoutJson(layout), "") }.toString()

    /** [skin] as a skin.json: keys in a fixed order, two-space indents, optional keys left out when unset. */
    fun toJson(skin: OpenEmuxSkin): String {
        val root = linkedMapOf<String, Any>(
            "format" to skin.format,
            "console" to skin.console.id,
            "name" to skin.name,
            "forms" to skin.forms.map { it.id },
        )
        combosJson(SkinCombos.of(skin.layouts.values))?.let { root["combos"] = it }
        root["layouts"] = skin.layouts.entries.associate { (id, layout) -> id.id to layoutJson(layout) }
        return StringBuilder().also { emit(it, root, "") }.append('\n').toString()
    }

    /** The root `combos` of [combos], or null when there are none (the key is left out). */
    private fun combosJson(combos: List<SkinCombo>): List<Map<String, Any>>? =
        combos.map { linkedMapOf<String, Any>("id" to it.id, "buttons" to it.buttons) }.takeIf { it.isNotEmpty() }

    /** [combos] as a skin.json's root `combos` array (the editor writing them, issue 842); null when there are none. */
    fun combosText(combos: List<SkinCombo>): String? = combosJson(combos)?.let { StringBuilder().also { b -> emit(b, it, "") }.toString() }

    /** The combo buttons of a layout ([elements]: its combos) as its `combos` array; null when it has none. */
    fun layoutCombosText(elements: List<SkinElement>): String? =
        elements.filter { it.isCombo }.map(::comboJson).takeIf { it.isNotEmpty() }?.let { StringBuilder().also { b -> emit(b, it, "") }.toString() }

    /**
     * Where a combo is on a layout and how it looks: never its type or its inputs (the root says those), so an app
     * that reads no combos takes nothing for an element it would refuse.
     */
    private fun comboJson(e: SkinElement): Map<String, Any> = buildMap {
        put("id", e.id)
        putAll(rectJson(e.rect))
        e.shape?.takeIf { it != SkinShape.CIRCLE }?.let { put("shape", it.id) }
        e.corner?.takeIf { e.shape == SkinShape.TRIANGLE }?.let { put("corner", it.id) }
        e.radius?.let { put("radius", it) }
        if (e.rotation != 0f) put("rotation", e.rotation)
        if (e.depth != 0f) put("depth", e.depth)
        e.labelSize?.let { put("labelSize", it) }
        e.labelOffset?.let { put("labelOffset", linkedMapOf<String, Any>("x" to it.x, "y" to it.y)) }
        if (e.colors.isNotEmpty()) put("colors", e.colors.mapValues { colorJson(it.value) })
    }

    private fun layoutJson(layout: SkinLayout): Map<String, Any> = buildMap {
        put("size", mapOf("width" to layout.size.width, "height" to layout.size.height))
        put("screens", layout.screens.map { rectJson(it) })
        backgroundJson(layout.background)?.let { put("background", it) }
        layout.frame?.let { put("frame", frameJson(it)) }
        put("elements", layout.elements.filter { !it.isCombo }.map { elementJson(it) })
        layout.elements.filter { it.isCombo }.takeIf { it.isNotEmpty() }?.let { put("combos", it.map(::comboJson)) }
        layout.keypad?.let { page ->
            put("keypad", buildMap {
                page.background?.let(::backgroundJson)?.let { put("background", it) }
                put("elements", page.elements.map { elementJson(it) })
            })
        }
        if (layout.screenLayouts.isNotEmpty()) {
            put("screenLayouts", layout.screenLayouts.mapValues { (_, page) ->
                buildMap {
                    put("screens", page.screens.map { rectJson(it) })
                    page.background?.let(::backgroundJson)?.let { put("background", it) }
                    page.frame?.let { put("frame", frameJson(it)) }
                    put("elements", page.elements.map { elementJson(it) })
                }
            })
        }
    }

    private fun frameJson(frame: SkinFrame): Map<String, Any> = buildMap {
        putAll(rectJson(frame.rect))
        val c = frame.colors
        val colors = buildMap {
            c.frame?.let { put("frame", colorJson(it)) }
            c.icons?.let { put("icons", colorJson(it)) }
            c.active?.let { put("active", colorJson(it)) }
            c.activeIcon?.let { put("activeIcon", it.id) }
        }
        if (frame.radius != 0f) put("radius", frame.radius)
        if (colors.isNotEmpty()) put("colors", colors)
        frame.image?.let { put("image", it) }
    }

    /** A background's keys, or null when it sets none. */
    private fun backgroundJson(bg: SkinBackground): Map<String, Any>? = buildMap {
        bg.color?.let { put("color", colorJson(it)) }
        bg.image?.let { put("image", it) }
    }.takeIf { it.isNotEmpty() }

    private fun elementJson(e: SkinElement): Map<String, Any> = buildMap {
        put("id", e.id)
        put("type", e.type.id)
        if (e.type != SkinElementType.DPAD || e.bind != DPAD_BIND) put("bind", e.bind)
        e.group?.let { put("group", it) }
        putAll(rectJson(e.rect))
        e.shape?.let { put("shape", it.id) }
        e.radius?.let { put("radius", it) }
        if (e.rotation != 0f) put("rotation", e.rotation)
        if (e.depth != 0f) put("depth", e.depth)
        e.label?.let { put("label", it) }
        e.labelSize?.let { put("labelSize", it) }
        e.labelOffset?.let { put("labelOffset", linkedMapOf<String, Any>("x" to it.x, "y" to it.y)) }
        if (e.colors.isNotEmpty()) put("colors", e.colors.mapValues { colorJson(it.value) })
        e.image?.let { put("image", it) }
        if (e.art != SkinArt.FULL) put("art", e.art.id)
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
