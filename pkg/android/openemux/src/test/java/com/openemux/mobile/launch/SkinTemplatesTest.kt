package com.openemux.mobile.launch

import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.floor
import kotlin.math.min

/**
 * The templates for skin creators (tools/skins/templates, issue 777), in OpenEmux's own format: one per console with a
 * bundled skin, each read by the app's own reader, with the six layouts, every image at the size the guide gives,
 * every control bound to something the app presses, and the phone and tablet layouts where the bundled skin has them.
 */
class SkinTemplatesTest {
    private val skins = File("src/main/assets/skins")
    private val templates = File("tools/skins/templates")

    /** The bundled packages in the console's own colours: console id to file. */
    private fun bundled() = skins.listFiles()!!
        .filter { it.isFile && (it.name.endsWith(".deltaskin") || it.name.endsWith(".manicskin")) }
        .associateBy { it.name.substringBeforeLast('.') }

    private fun template(id: String): SkinPackage = SkinFormat.readZip(File(templates, "$id-template.zip"))

    /** A PNG's width and height, from its header. */
    private fun pngSize(pkg: SkinPackage, path: String): Pair<Int, Int> = pkg.source.open(path)!!.use { input ->
        val data = DataInputStream(input)
        val header = ByteArray(16).also { data.readFully(it) }
        assertEquals("$path is a PNG", 0x89.toByte(), header[0])
        data.readInt() to data.readInt()
    }

    /** As build.py rounds: half up. */
    private fun px(v: Double) = floor(v + 0.5).toInt()

    @Test fun everyBundledConsoleHasATemplateAndNothingElseDoes() {
        val made = templates.listFiles()!!.filter { it.name.endsWith(".zip") }.map { it.name.removeSuffix("-template.zip") }
        assertEquals(bundled().keys.sorted(), made.sorted())
        assertTrue(File(templates, "README.md").isFile)
        assertTrue(File(templates, "ASSET-PLAN.md").isFile)
    }

    @Test fun aTemplateHasTheSixLayoutsAndItsGuideFiles() {
        for (id in bundled().keys) {
            val pkg = template(id)
            assertEquals(id, pkg.skin.console.id)
            assertEquals(SkinLayoutId.entries.toSet(), pkg.skin.layouts.keys)
            assertTrue("$id README.txt", "README.txt" in pkg.source.files)
            for (layout in SkinLayoutId.entries) assertTrue("$id ${layout.id}.svg", "sources/${layout.id}.svg" in pkg.source.files)
        }
    }

    @Test fun everyImageIsThereAtItsSize() {
        for (id in bundled().keys) {
            val pkg = template(id)
            for ((layoutId, layout) in pkg.skin.layouts) {
                val where = "$id ${layoutId.id}"
                assertEquals(where, layout.size.width to layout.size.height, pngSize(pkg, pkg.background(layoutId)!!))
                val frame = layout.frame!!
                assertEquals(where, frame.rect.width.toInt() to frame.rect.height.toInt(), pngSize(pkg, pkg.frame(layoutId)!!))
                for (e in layout.elements) {
                    val w = e.rect.width.toInt()
                    val h = e.rect.height.toInt()
                    // Whole pixels, so the image is exactly the element.
                    for (v in listOf(e.rect.x, e.rect.y, e.rect.width, e.rect.height)) assertEquals("$where ${e.id}", floor(v), v, 0f)
                    val side = min(w, h)
                    val sizes = when (e.type) {
                        SkinElementType.DPAD -> listOf(w to h, px(side * OverlayLines.DPAD_TRAVEL.toDouble()).let { it to it })
                        SkinElementType.STICK -> listOf(w to h, px(side * 0.5).let { it to it })
                        else -> listOf(w to h)
                    }
                    pkg.images(layoutId, e).zip(sizes).forEach { (path, size) ->
                        assertNotNull("$where ${e.id}", path)
                        assertEquals("$where $path", size, pngSize(pkg, path!!))
                    }
                }
            }
        }
    }

    @Test fun everyControlPressesSomething() {
        for (id in bundled().keys) {
            val pkg = template(id)
            val console = pkg.skin.console
            for ((layoutId, layout) in pkg.skin.layouts) for (e in layout.elements) {
                val known = when (e.type) {
                    SkinElementType.BUTTON, SkinElementType.ACTION ->
                        // screens is the format's own: the NDS's next screen layout, no RetroArch bind.
                        e.bind == "screens" || SkinBinds.bind(console, e.bind, hardcore = false) != null
                    else -> e.bind in SkinFormat.bindsFor(e.type)
                }
                assertTrue("$id ${layoutId.id} ${e.id} ${e.bind}", known)
            }
        }
    }

    @Test fun phoneAndTabletAreTheBundledLayouts() {
        for ((id, file) in bundled()) {
            val pkg = template(id)
            val info = ZipFile(file).use { JSONObject(it.getInputStream(it.getEntry("info.json")).bufferedReader().readText()) }
            val reps = info.getJSONObject("representations")
            for ((layoutId, family, scale) in listOf(
                Triple(SkinLayoutId.PHONE_PORTRAIT, "iphone/edgeToEdge/portrait", 3),
                Triple(SkinLayoutId.PHONE_LANDSCAPE, "iphone/edgeToEdge/landscape", 3),
                Triple(SkinLayoutId.TABLET_PORTRAIT, "ipad/standard/portrait", 2),
                Triple(SkinLayoutId.TABLET_LANDSCAPE, "ipad/standard/landscape", 2),
            )) {
                val (f, kind, orient) = family.split('/')
                val rep = reps.getJSONObject(f).getJSONObject(kind).getJSONObject(orient)
                val layout = pkg.skin.layouts.getValue(layoutId)
                val where = "$id ${layoutId.id}"
                val mapping = rep.getJSONObject("mappingSize")
                assertEquals(where, mapping.getInt("width") * scale, layout.size.width)
                assertEquals(where, mapping.getInt("height") * scale, layout.size.height)
                val rect = { o: JSONObject ->
                    val x0 = px(o.getDouble("x") * scale)
                    val y0 = px(o.getDouble("y") * scale)
                    listOf(x0, y0, px((o.getDouble("x") + o.getDouble("width")) * scale) - x0,
                        px((o.getDouble("y") + o.getDouble("height")) * scale) - y0)
                }
                val of = { r: com.openemux.mobile.data.SkinRect -> listOf(r.x, r.y, r.width, r.height).map { it.toInt() } }
                val screens = rep.getJSONArray("screens")
                assertEquals(where, (0 until screens.length()).map { rect(screens.getJSONObject(it).getJSONObject("outputFrame")) },
                    layout.screens.map(of))
                val items = rep.getJSONArray("items")
                assertEquals(where, items.length(), layout.elements.size)
                layout.elements.forEachIndexed { i, e ->
                    val item = items.getJSONObject(i)
                    assertEquals("$where ${e.id}", rect(item.getJSONObject("frame")), of(e.rect))
                    val inputs = item.get("inputs")
                    val expected = if (inputs is JSONObject) {
                        val up = inputs.getString("up")
                        when {
                            up.startsWith("rightAnalogStick") -> "rightAnalogStick"
                            up.startsWith("analogStick") -> "analogStick"
                            else -> "dpad"
                        }
                    } else {
                        (inputs as org.json.JSONArray).getString(0)
                    }
                    assertEquals("$where ${e.id}", expected, e.bind)
                }
            }
        }
    }

    @Test fun theTemplatesAreNotInTheApk() {
        // Creators download them from the repository; the APK carries only the bundled skins.
        val assets = File("src/main/assets").walkTopDown().filter { it.name.endsWith("-template.zip") }.toList()
        assertEquals(emptyList<File>(), assets)
    }
}
