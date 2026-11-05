package com.openemux.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkinFormatTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun rect(x: Float, y: Float, w: Float, h: Float) = SkinRect(x, y, w, h)

    /** Every type, colour part and optional key, so the round trip covers the whole writer. */
    private val full = OpenEmuxSkin(
        Console.GBA, "Test \"quoted\" skin",
        mapOf(
            SkinLayoutId.PHONE_PORTRAIT to SkinLayout(
                SkinSize(1080, 2340),
                listOf(rect(0f, 120f, 1080f, 720f)),
                SkinBackground(0xFF112233.toInt(), "bg-dark"),
                SkinFrame(rect(0f, 100f, 1080f, 840f), SkinFrameColors(0xFF000000.toInt(), 0x80FFFFFF.toInt(), 0xFF00AA00.toInt(), SkinIconTone.DARK), "bezel"),
                listOf(
                    SkinElement("dpad", SkinElementType.DPAD, "dpad", rect(60f, 1300f, 400f, 400f), "dpad", colors = mapOf("pad" to 0xFF333333.toInt(), "ball" to 0xFFAAAAAA.toInt())),
                    SkinElement("a", SkinElementType.BUTTON, "a", rect(800f, 1350f, 180.5f, 180.5f), "face", SkinShape.CIRCLE, "A", mapOf("button" to 0xFFCC0000.toInt(), "letter" to 0xFFFFFFFF.toInt()), "a-red"),
                    SkinElement("start", SkinElementType.BUTTON, "start", rect(560f, 2100f, 160f, 60f), shape = SkinShape.PILL),
                    SkinElement("stick", SkinElementType.STICK, "analogStick", rect(100f, 1800f, 300f, 300f), colors = mapOf("base" to 0xFF222222.toInt(), "knob" to 0xFF555555.toInt())),
                    SkinElement("save", SkinElementType.ACTION, "quickSave", rect(20f, 110f, 60f, 60f)),
                    SkinElement("key-1", SkinElementType.KEYPAD, "keypad1", rect(400f, 1900f, 80f, 80f), "keypad", SkinShape.RECT, "1", mapOf("letter" to 0xFF000000.toInt())),
                ),
            ),
            SkinLayoutId.FOLD_LANDSCAPE to SkinLayout(SkinSize(2208, 1768), listOf(rect(400f, 0f, 1400f, 1000f))),
        ),
    )

    private val minimal = """{"format":1,"console":"GBA","name":"Mini","layouts":{"phone-portrait":
        {"size":{"width":100,"height":200},"screens":[{"x":0,"y":0,"width":100,"height":60}]}}}"""

    private fun json(skin: OpenEmuxSkin = full) = JSONObject(SkinFormat.toJson(skin))

    private fun JSONObject.layout(id: String = "phone-portrait") = getJSONObject("layouts").getJSONObject(id)
    private fun JSONObject.element(i: Int) = layout().getJSONArray("elements").getJSONObject(i)

    private fun refusedText(error: SkinError, path: String? = null, json: () -> String) {
        try {
            SkinFormat.parse(json())
            fail("expected $error")
        } catch (e: SkinFormatException) {
            assertEquals(e.message, error, e.error)
            path?.let { assertEquals(it, e.path) }
        }
    }

    private fun refused(error: SkinError, path: String? = null, edit: JSONObject.() -> Unit) =
        refusedText(error, path) { json().apply(edit).toString() }

    @Test fun aWrittenSkinReadsBackTheSame() {
        assertEquals(full, SkinFormat.parse(SkinFormat.toJson(full)))
        val mini = SkinFormat.parse(minimal)
        assertEquals(mini, SkinFormat.parse(SkinFormat.toJson(mini)))
        assertEquals(SkinBackground(), mini.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).background)
        assertNull(mini.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).frame)
        assertEquals(emptyList<SkinElement>(), mini.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements)
    }

    @Test fun theWriterKeepsItsOwnOrderAndLeavesUnsetKeysOut() {
        val text = SkinFormat.toJson(full)
        assertTrue(text.startsWith("{\n  \"format\": 1,\n  \"console\": \"GBA\","))
        assertTrue(text.endsWith("}\n"))
        assertTrue("\"x\": 60," in text)
        assertTrue("\"width\": 180.5," in text)
        assertTrue("\"activeIcon\": \"dark\"" in text)
        assertTrue("\"icons\": \"#FFFFFF80\"" in text)
        assertTrue("\"color\": \"#112233\"" in text)
        val root = JSONObject(text)
        assertEquals(JSONArray(listOf("phone", "fold")).toString(), root.getJSONArray("forms").toString())
        // The D-pad's bind is implied; unset keys and empty colour sets are left out.
        assertFalse(root.element(0).has("bind"))
        assertFalse(root.element(2).has("colors") || root.element(2).has("group") || root.element(2).has("label"))
        val fold = root.layout("fold-landscape")
        assertFalse(fold.has("background") || fold.has("frame"))
        assertEquals(0, fold.getJSONArray("elements").length())
        val bare = full.copy(layouts = mapOf(SkinLayoutId.PHONE_PORTRAIT to full.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).copy(frame = SkinFrame(rect(0f, 0f, 10f, 10f)))))
        assertFalse(json(bare).layout().getJSONObject("frame").has("colors"))
        assertFalse(json(bare).layout().getJSONObject("frame").has("image"))
    }

    @Test fun theFormsFollowTheLayouts() {
        assertEquals(listOf(SkinForm.PHONE, SkinForm.FOLD), full.forms)
        val all = full.copy(layouts = SkinLayoutId.entries.associateWith { full.layouts.getValue(SkinLayoutId.FOLD_LANDSCAPE) })
        assertEquals(SkinForm.entries, all.forms)
        val tablet = full.copy(layouts = mapOf(SkinLayoutId.TABLET_LANDSCAPE to full.layouts.getValue(SkinLayoutId.FOLD_LANDSCAPE)))
        assertEquals(listOf(SkinForm.TABLET), tablet.forms)
        // The file's "forms" is informative: the layouts decide.
        val read = SkinFormat.parse(json().put("forms", JSONArray(listOf("tablet"))).toString())
        assertEquals(listOf(SkinForm.PHONE, SkinForm.FOLD), read.forms)
    }

    @Test fun theLayoutIdsAreTheFolderNames() {
        assertEquals(
            listOf("phone-portrait", "phone-landscape", "tablet-portrait", "tablet-landscape", "fold-portrait", "fold-landscape"),
            SkinLayoutId.entries.map { it.id },
        )
        assertEquals(SkinLayoutId.FOLD_PORTRAIT, SkinLayoutId.byId("fold-portrait"))
        assertNull(SkinLayoutId.byId("watch-portrait"))
    }

    @Test fun layoutsComeOutInTheirOwnOrderWhateverTheFiles() {
        val text = """{"format":1,"console":"GBA","name":"N","layouts":{
            "fold-landscape":{"size":{"width":10,"height":10},"screens":[{"x":0,"y":0,"width":5,"height":5}]},
            "phone-portrait":{"size":{"width":10,"height":10},"screens":[{"x":0,"y":0,"width":5,"height":5}]}}}"""
        assertEquals(listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.FOLD_LANDSCAPE), SkinFormat.parse(text).layouts.keys.toList())
    }

    @Test fun eachElementNamesItsImageFiles() {
        val e = full.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements
        assertEquals(listOf("dpad.png", "dpad-ball.png"), e[0].imageFiles)
        assertEquals(listOf("a-red.png"), e[1].imageFiles)
        assertEquals(listOf("stick-base.png", "stick-knob.png"), e[3].imageFiles)
        assertEquals(listOf("key-1.png"), e[5].imageFiles)
        assertEquals("background.png", SkinBackground().imageFile)
        assertEquals("frame.png", SkinFrame(rect(0f, 0f, 1f, 1f)).imageFile)
    }

    /** [skin] unpacked in a folder with skin.json and [images] in its layouts' folders. */
    private fun folder(skin: OpenEmuxSkin, vararg images: String): File {
        val dir = tmp.newFolder()
        File(dir, "skin.json").writeText(SkinFormat.toJson(skin))
        File(dir, "README.txt").writeText("ignored")
        images.forEach { File(dir, it).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3)) }
        return dir
    }

    @Test fun aFolderReadsWithItsImagesResolved() {
        val pkg = SkinFormat.readFolder(folder(full, "phone-portrait/bg-dark.png", "phone-portrait/dpad.png", "phone-portrait/a-red.png", "tablet-portrait/x.png"))
        assertEquals(full, pkg.skin)
        val p = SkinLayoutId.PHONE_PORTRAIT
        val elements = full.layouts.getValue(p).elements
        assertEquals("phone-portrait/bg-dark.png", pkg.background(p))
        assertNull(pkg.frame(p)) // bezel.png is missing: the frame's colours are drawn
        assertEquals(listOf("phone-portrait/dpad.png", null), pkg.images(p, elements[0]))
        assertEquals(listOf("phone-portrait/a-red.png"), pkg.images(p, elements[1]))
        assertNull(pkg.background(SkinLayoutId.FOLD_LANDSCAPE))
        assertNull(pkg.frame(SkinLayoutId.FOLD_LANDSCAPE)) // the layout has no frame
        assertNull(pkg.background(SkinLayoutId.TABLET_PORTRAIT)) // a folder with no layout entry is not a layout
        assertEquals(listOf<Byte>(1, 2, 3), pkg.source.open("phone-portrait/dpad.png")!!.use { it.readBytes() }.toList())
        assertNull(pkg.source.open("phone-portrait/frame.png"))
    }

    @Test fun aFolderWithNoSkinJsonIsRefused() {
        val dir = tmp.newFolder()
        File(dir, "phone-portrait").mkdirs()
        assertError(SkinError.NO_SKIN_JSON) { SkinFormat.readFolder(dir) }
    }

    @Test fun anOversizedSkinJsonIsRefused() {
        val dir = tmp.newFolder()
        File(dir, "skin.json").writeText(" ".repeat(SkinFormat.MAX_JSON_BYTES + 1))
        assertError(SkinError.TOO_LARGE) { SkinFormat.readFolder(dir) }
    }

    private fun zip(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("skin", ".zip", tmp.root)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    @Test fun aZipReadsWithTheSkinAtItsRootOrInOneFolder() {
        val json = SkinFormat.toJson(full).toByteArray()
        val atRoot = SkinFormat.readZip(zip(mapOf("skin.json" to json, "phone-portrait/" to ByteArray(0), "phone-portrait/dpad.png" to byteArrayOf(9))))
        assertEquals(full, atRoot.skin)
        assertEquals("phone-portrait/dpad.png", atRoot.images(SkinLayoutId.PHONE_PORTRAIT, full.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0])[0])
        assertEquals(listOf<Byte>(9), atRoot.source.open("phone-portrait/dpad.png")!!.use { it.readBytes() }.toList())
        assertNull(atRoot.source.open("phone-portrait/a.png"))

        val inFolder = SkinFormat.readZip(zip(mapOf("My skin/skin.json" to json, "My skin/phone-portrait/bg-dark.png" to byteArrayOf(7))))
        assertEquals(full, inFolder.skin)
        assertEquals("phone-portrait/bg-dark.png", inFolder.background(SkinLayoutId.PHONE_PORTRAIT))
        // The single-byte read goes through the same entry.
        assertEquals(7, inFolder.source.open("phone-portrait/bg-dark.png")!!.use { it.read() })
    }

    @Test fun aZipsEntriesThatLeaveTheSkinAreIgnored() {
        val source = ZipSkinSource(zip(mapOf("skin.json" to minimal.toByteArray(), "../evil.png" to byteArrayOf(1), "a/../../b.png" to byteArrayOf(1), "/abs.png" to byteArrayOf(1), "c\\d.png" to byteArrayOf(1))))
        assertEquals(setOf("skin.json"), source.files)
        assertTrue(SkinFormat.safePath("phone-portrait/a.png"))
        assertFalse(SkinFormat.safePath("phone-portrait/../a.png"))
    }

    @Test fun aZipMadeOnMacosReadsFromItsSingleFolder() {
        val json = SkinFormat.toJson(full).toByteArray()
        val pkg = SkinFormat.readZip(zip(mapOf("My skin/skin.json" to json, "__MACOSX/My skin/._skin.json" to byteArrayOf(0))))
        assertEquals(full, pkg.skin)
        assertEquals(setOf("skin.json"), pkg.source.files)
    }

    @Test fun aZipWithNoSkinJsonOrTwoFoldersIsRefused() {
        assertError(SkinError.NO_SKIN_JSON) { SkinFormat.readZip(zip(mapOf("a/skin.json" to minimal.toByteArray(), "b/x.png" to byteArrayOf(1)))) }
        assertError(SkinError.NO_SKIN_JSON) { SkinFormat.readZip(zip(mapOf("x.png" to byteArrayOf(1)))) }
        assertError(SkinError.NO_SKIN_JSON) { SkinFormat.readZip(zip(mapOf("skin/x.png" to byteArrayOf(1)))) }
    }

    @Test fun aFileThatIsNotAZipIsRefused() {
        val file = File(tmp.root, "not.zip").apply { writeText("hello") }
        assertError(SkinError.BAD_PACKAGE) { SkinFormat.readZip(file) }
        assertError(SkinError.BAD_PACKAGE) { SkinFormat.readZip(File(tmp.root, "missing.zip")) }
        // A zip whose skin.json entry is damaged (its local header's signature broken) fails while it is opened.
        val damaged = zip(mapOf("skin.json" to minimal.toByteArray()))
        damaged.writeBytes(damaged.readBytes().also { it[0] = 0 })
        assertError(SkinError.BAD_PACKAGE) { SkinFormat.readZip(damaged) }
    }

    private fun assertError(error: SkinError, block: () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: SkinFormatException) {
            assertEquals(error, e.error)
        }
    }

    @Test fun theTopLevelIsChecked() {
        refusedText(SkinError.BAD_JSON, "") { "not json" }
        refusedText(SkinError.BAD_JSON) { "[1]" }
        refused(SkinError.MISSING_KEY, "format") { remove("format") }
        refused(SkinError.MISSING_KEY, "format") { put("format", JSONObject.NULL) }
        refused(SkinError.BAD_VALUE, "format") { put("format", "1") }
        refused(SkinError.BAD_VALUE, "format") { put("format", 1.5) }
        refused(SkinError.UNSUPPORTED_FORMAT, "format") { put("format", 2) }
        refused(SkinError.UNKNOWN_CONSOLE, "console") { put("console", "XBOX") }
        refused(SkinError.BAD_VALUE, "console") { put("console", 3) }
        refused(SkinError.BAD_VALUE, "name") { put("name", "  ") }
        refused(SkinError.MISSING_KEY, "layouts") { remove("layouts") }
        refused(SkinError.BAD_VALUE, "layouts") { put("layouts", JSONArray()) }
        refused(SkinError.NO_LAYOUTS, "layouts") { put("layouts", JSONObject()) }
        refused(SkinError.UNKNOWN_LAYOUT, "layouts.watch") { getJSONObject("layouts").put("watch", JSONObject()) }
        refused(SkinError.BAD_VALUE, "layouts.fold-landscape") { getJSONObject("layouts").put("fold-landscape", 1) }
    }

    @Test fun aLayoutsSizeAndScreensAreChecked() {
        refused(SkinError.MISSING_KEY, "layouts.phone-portrait.size") { layout().remove("size") }
        refused(SkinError.BAD_SIZE, "layouts.phone-portrait.size") { layout().getJSONObject("size").put("width", 0) }
        refused(SkinError.BAD_SIZE, "layouts.phone-portrait.size") { layout().getJSONObject("size").put("height", -5) }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.size.width") { layout().getJSONObject("size").put("width", 10.5) }
        refused(SkinError.MISSING_KEY, "layouts.phone-portrait.screens") { layout().remove("screens") }
        refused(SkinError.BAD_SCREENS, "layouts.phone-portrait.screens") { layout().put("screens", JSONArray()) }
        refused(SkinError.BAD_SCREENS, "layouts.phone-portrait.screens") {
            layout().getJSONArray("screens").put(layout().getJSONArray("screens").get(0))
        }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.screens[0]") { layout().put("screens", JSONArray(listOf(1))) }
        refused(SkinError.OUT_OF_LAYOUT, "layouts.phone-portrait.screens[0]") { layout().getJSONArray("screens").getJSONObject(0).put("x", 10) }
        refused(SkinError.BAD_SIZE, "layouts.phone-portrait.screens[0]") { layout().getJSONArray("screens").getJSONObject(0).put("height", 0) }
        refused(SkinError.MISSING_KEY, "layouts.phone-portrait.screens[0].y") { layout().getJSONArray("screens").getJSONObject(0).remove("y") }
    }

    @Test fun theNdsHasTwoScreens() {
        val screen = """{"x":0,"y":0,"width":256,"height":192}"""
        val nds = { screens: String -> """{"format":1,"console":"NDS","name":"DS","layouts":{"phone-portrait":{"size":{"width":256,"height":800},"screens":[$screens]}}}""" }
        assertEquals(2, SkinFormat.parse(nds("$screen,$screen")).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).screens.size)
        refusedText(SkinError.BAD_SCREENS) { nds(screen) }
    }

    @Test fun rectsMustStayInTheLayout() {
        listOf("x" to -1, "y" to -1).forEach { (k, v) ->
            refused(SkinError.OUT_OF_LAYOUT, "layouts.phone-portrait.elements[1]") { element(1).put(k, v) }
        }
        refused(SkinError.OUT_OF_LAYOUT) { element(1).put("x", 1000) }
        refused(SkinError.OUT_OF_LAYOUT) { element(1).put("y", 2300) }
        refused(SkinError.OUT_OF_LAYOUT, "layouts.phone-portrait.frame") { layout().getJSONObject("frame").put("width", 2000) }
        refused(SkinError.BAD_SIZE, "layouts.phone-portrait.elements[1]") { element(1).put("width", 0) }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.elements[1].x") { element(1).put("x", "left") }
    }

    @Test fun theBackgroundAndFrameAreChecked() {
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.background") { layout().put("background", "black") }
        refused(SkinError.BAD_COLOR, "layouts.phone-portrait.background.color") { layout().getJSONObject("background").put("color", "#12345") }
        refused(SkinError.BAD_COLOR) { layout().getJSONObject("background").put("color", "red") }
        refused(SkinError.BAD_ID, "layouts.phone-portrait.background.image") { layout().getJSONObject("background").put("image", "../bg") }
        refused(SkinError.BAD_ID) { layout().getJSONObject("background").put("image", "bg.png") }
        refused(SkinError.UNKNOWN_COLOR_PART, "layouts.phone-portrait.frame.colors.glow") { layout().getJSONObject("frame").getJSONObject("colors").put("glow", "#000000") }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.frame.colors.activeIcon") { layout().getJSONObject("frame").getJSONObject("colors").put("activeIcon", "grey") }
        refused(SkinError.BAD_COLOR, "layouts.phone-portrait.frame.colors.icons") { layout().getJSONObject("frame").getJSONObject("colors").put("icons", "#GGGGGG") }
        refused(SkinError.BAD_ID, "layouts.phone-portrait.frame.image") { layout().getJSONObject("frame").put("image", "Bezel") }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.elements") { layout().put("elements", JSONObject()) }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.elements[0]") { layout().getJSONArray("elements").put(0, "dpad") }
    }

    @Test fun aFrameWithNoColoursOrImageReadsAsDefaults() {
        val text = json().apply {
            layout().getJSONObject("frame").remove("colors")
            layout().getJSONObject("frame").remove("image")
        }.toString()
        val frame = SkinFormat.parse(text).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).frame!!
        assertEquals(SkinFrameColors(), frame.colors)
        assertNull(frame.image)
        val partial = json().apply { layout().getJSONObject("frame").put("colors", JSONObject().put("icons", "#FFFFFF")) }.toString()
        assertEquals(SkinFrameColors(icons = 0xFFFFFFFF.toInt()), SkinFormat.parse(partial).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).frame!!.colors)
    }

    @Test fun elementsAreChecked() {
        refused(SkinError.MISSING_KEY, "layouts.phone-portrait.elements[1].id") { element(1).remove("id") }
        refused(SkinError.BAD_ID, "layouts.phone-portrait.elements[1].id") { element(1).put("id", "A") }
        refused(SkinError.BAD_ID) { element(1).put("id", "a/b") }
        refused(SkinError.BAD_ID) { element(1).put("id", "-a") }
        refused(SkinError.DUPLICATE_ID, "layouts.phone-portrait.elements[2].id") { element(2).put("id", "a") }
        refused(SkinError.DUPLICATE_ID, "layouts.phone-portrait.elements[2]") { element(2).put("id", "bg-dark") }
        refused(SkinError.DUPLICATE_ID) { element(2).put("image", "a-red") }
        refused(SkinError.UNKNOWN_TYPE, "layouts.phone-portrait.elements[1].type") { element(1).put("type", "trigger") }
        refused(SkinError.MISSING_KEY, "layouts.phone-portrait.elements[1].bind") { element(1).remove("bind") }
        refused(SkinError.UNKNOWN_BIND, "layouts.phone-portrait.elements[1].bind") { element(1).put("bind", "jump") }
        refused(SkinError.BIND_NOT_FOR_TYPE, "layouts.phone-portrait.elements[3].bind") { element(3).put("bind", "a") }
        refused(SkinError.BIND_NOT_FOR_TYPE) { element(4).put("bind", "a") }
        refused(SkinError.BIND_NOT_FOR_TYPE) { element(5).put("bind", "start") }
        refused(SkinError.BIND_NOT_FOR_TYPE) { element(1).put("bind", "keypad2") }
        refused(SkinError.BIND_NOT_FOR_TYPE, "layouts.phone-portrait.elements[0].bind") { element(0).put("bind", "up") }
        refused(SkinError.BAD_SHAPE, "layouts.phone-portrait.elements[1].shape") { element(1).put("shape", "star") }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.elements[1].label") { element(1).put("label", 1) }
        refused(SkinError.UNKNOWN_COLOR_PART, "layouts.phone-portrait.elements[1].colors.pad") { element(1).getJSONObject("colors").put("pad", "#000000") }
        refused(SkinError.UNKNOWN_COLOR_PART, "layouts.phone-portrait.elements[4].colors.button") { element(4).put("colors", JSONObject().put("button", "#000000")) }
        refused(SkinError.BAD_COLOR, "layouts.phone-portrait.elements[1].colors.letter") { element(1).getJSONObject("colors").put("letter", "#FFF") }
        refused(SkinError.BAD_VALUE, "layouts.phone-portrait.elements[1].colors.letter") { element(1).getJSONObject("colors").put("letter", 1) }
        refused(SkinError.BAD_ID, "layouts.phone-portrait.elements[1].image") { element(1).put("image", "a.png") }
    }

    @Test fun aDpadsBindIsImpliedOrDpad() {
        val withBind = json().apply { element(0).put("bind", "dpad") }.toString()
        assertEquals("dpad", SkinFormat.parse(withBind).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0].bind)
        assertEquals("dpad", SkinFormat.parse(SkinFormat.toJson(full)).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0].bind)
    }

    @Test fun aColourPartSetToNullReadsAsLeftOut() {
        val text = json().apply { element(1).put("colors", JSONObject().put("button", JSONObject.NULL).put("letter", "#FFFFFF")) }.toString()
        val a = SkinFormat.parse(text).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[1]
        assertEquals(mapOf("letter" to 0xFFFFFFFF.toInt()), a.colors)
    }

    @Test fun colourPartsComeOutInTheTypesOrderAndAlphaIsKept() {
        val text = json().apply { element(1).put("colors", JSONObject().put("letter", "#ffffff").put("button", "#00000080")) }.toString()
        val a = SkinFormat.parse(text).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[1]
        assertEquals(listOf("button", "letter"), a.colors.keys.toList())
        assertEquals(0x80000000.toInt(), a.colors["button"])
        assertEquals(0xFFFFFFFF.toInt(), a.colors["letter"])
    }

    @Test fun unknownKeysAreIgnored() {
        val text = json().apply {
            put("author", "someone")
            layout().put("notes", JSONArray())
            element(1).put("haptics", true)
        }.toString()
        assertEquals(full, SkinFormat.parse(text))
        // A null reads as the key left out.
        val nulled = json().apply { element(1).put("label", JSONObject.NULL) }.toString()
        assertNull(SkinFormat.parse(nulled).layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[1].label)
    }

    @Test fun eachTypeTakesItsOwnBinds() {
        assertEquals(setOf("dpad"), SkinFormat.bindsFor(SkinElementType.DPAD))
        assertEquals(setOf("analogStick", "rightAnalogStick"), SkinFormat.bindsFor(SkinElementType.STICK))
        assertEquals(12, SkinFormat.bindsFor(SkinElementType.KEYPAD).size)
        assertTrue("keypadStar" in SkinFormat.bindsFor(SkinElementType.KEYPAD) && "keypad0" in SkinFormat.bindsFor(SkinElementType.KEYPAD))
        assertTrue("slowMotion" in SkinFormat.bindsFor(SkinElementType.ACTION) && "screens" in SkinFormat.bindsFor(SkinElementType.ACTION))
        // A button takes what a Delta or Manic skin may name, quick actions included.
        assertTrue(listOf("a", "l1", "cUp", "mode", "quickSave", "slowMotion").all { it in SkinFormat.bindsFor(SkinElementType.BUTTON) })
        assertFalse("analogStick" in SkinFormat.bindsFor(SkinElementType.BUTTON))
    }

    @Test fun theDocumentsExampleIsAValidSkin() {
        // Gradle runs the unit tests from the module's folder, where docs/ is.
        val doc = File("docs/skin-format.md").readText()
        val example = doc.substringAfter("```json\n").substringBefore("```")
        val skin = SkinFormat.parse(example)
        assertEquals(Console.GBA, skin.console)
        assertEquals(listOf(SkinForm.PHONE), skin.forms)
        assertEquals(5, skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements.size)
        // Every error kind is in the document's table.
        SkinError.entries.forEach { assertTrue(it.name, "| `${it.name}` |" in doc) }
    }

    @Test fun theErrorSaysWhatAndWhere() {
        assertEquals("BAD_COLOR at layouts.x.color: red", SkinFormatException(SkinError.BAD_COLOR, "layouts.x.color", "red").message)
        assertEquals("NO_SKIN_JSON at skin", SkinFormatException(SkinError.NO_SKIN_JSON, "").message)
    }

    @Test fun theEnumsLookUpByTheirIds() {
        assertEquals(SkinElementType.KEYPAD, SkinElementType.byId("keypad"))
        assertEquals(SkinShape.PILL, SkinShape.byId("pill"))
        assertEquals(SkinIconTone.LIGHT, SkinIconTone.byId("light"))
        assertNull(SkinShape.byId("Pill"))
        assertTrue(SkinRect(0f, 0f, 10f, 10f).inside(SkinSize(10, 10)))
        assertFalse(SkinRect(0f, 0f, 10.5f, 10f).inside(SkinSize(10, 10)))
    }
}
