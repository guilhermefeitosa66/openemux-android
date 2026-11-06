package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.forCore
import com.openemux.mobile.data.skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import kotlin.math.floor
import kotlin.math.min

/**
 * The skins the app ships in OpenEmux's own format (issue 770: the Sega, NEC and Famicom Disk System consoles; issue
 * 771: the Atari consoles, the Lynx, the Neo Geo Pocket, the WonderSwan, the Vectrex and the Virtual Boy; issue 772:
 * the consoles with a keypad), read by the app's own reader: the six layouts, every image at its element's size, a
 * Dark twin with the same layout, and every control pressing what the drawn skin's presses for that console.
 */
class OwnFormatBundledSkinsTest {
    private val assets = File("src/main/assets")

    private fun packages(variant: SkinVariant) = File(assets, variant.assetDir).listFiles()!!
        .filter { it.isFile && ImportedSkinRenderer.isOpenEmuxPackage(it) }.sortedBy { it.name }

    private fun read(file: File): SkinPackage = SkinFormat.readZip(file)

    /** A PNG's width and height, from its header. */
    private fun pngSize(pkg: SkinPackage, path: String): Pair<Int, Int> = pkg.source.open(path)!!.use { input ->
        val data = DataInputStream(input)
        val header = ByteArray(16).also { data.readFully(it) }
        assertEquals("$path is a PNG", 0x89.toByte(), header[0])
        data.readInt() to data.readInt()
    }

    @Test fun theConsolesShipInTheOwnFormat() {
        val ids = packages(SkinVariant.CONSOLE).map { it.name.removeSuffix(".zip") }
        assertEquals(listOf(
            "A2600", "A5200", "A7800", "CV", "FDS", "GG", "INTV", "LYNX", "MCD", "NGP", "O2", "PCE", "PCECD", "S32X", "SATURN",
            "SG1000", "VB", "VECTREX", "WS",
        ), ids)
        assertEquals(ids, packages(SkinVariant.DARK).map { it.name.removeSuffix(".zip") })
    }

    @Test fun everyPackageHasTheSixLayoutsForItsConsole() {
        for (file in packages(SkinVariant.CONSOLE) + packages(SkinVariant.DARK)) {
            val pkg = read(file)
            assertEquals(file.name.removeSuffix(".zip"), pkg.skin.console.id)
            assertEquals(file.path, SkinLayoutId.entries.toSet(), pkg.skin.layouts.keys)
        }
    }

    @Test fun theDarkTwinHasTheSameLayout() {
        for (file in packages(SkinVariant.CONSOLE)) {
            val own = read(file).skin
            val dark = read(File(assets, "${SkinVariant.DARK.assetDir}/${file.name}")).skin
            assertEquals("${own.name} Dark", dark.name)
            for ((id, layout) in own.layouts) {
                val d = dark.layouts.getValue(id)
                val where = "${file.name} ${id.id}"
                assertEquals(where, layout.size, d.size)
                assertEquals(where, layout.screens, d.screens)
                assertEquals(where, layout.frame?.rect, d.frame?.rect)
                for (keypad in listOf(false, true)) {
                    assertEquals(where, layout.elements(keypad).map { listOf(it.id, it.type, it.bind, it.rect, it.label) },
                        d.elements(keypad).map { listOf(it.id, it.type, it.bind, it.rect, it.label) })
                }
            }
        }
    }

    @Test fun everyImageIsThereAtItsSize() {
        for (file in packages(SkinVariant.CONSOLE) + packages(SkinVariant.DARK)) {
            val pkg = read(file)
            for ((id, layout) in pkg.skin.layouts) {
                val where = "${file.path} ${id.id}"
                assertEquals(where, layout.size.width to layout.size.height, pngSize(pkg, pkg.background(id)!!))
                val frame = layout.frame!!
                assertEquals(where, frame.rect.width.toInt() to frame.rect.height.toInt(), pngSize(pkg, pkg.frame(id)!!))
                layout.keypad?.let {
                    assertEquals(where, layout.size.width to layout.size.height, pngSize(pkg, pkg.background(id, keypadPage = true)!!))
                    assertEquals(where, "${id.id}/keypad/background.png", pkg.background(id, keypadPage = true))
                }
                for ((e, keypad) in layout.elements.map { it to false } + layout.elements(keypadPage = true).map { it to true }) {
                    val w = e.rect.width.toInt()
                    val h = e.rect.height.toInt()
                    fun share(f: Double) = floor(min(w, h) * f + 0.5).toInt().let { it to it }
                    val sizes = when (e.type) {
                        SkinElementType.DPAD -> listOf(w to h, share(OverlayLines.DPAD_TRAVEL.toDouble()))
                        SkinElementType.STICK -> listOf(w to h, share(0.5))
                        else -> listOf(w to h)
                    }
                    pkg.images(id, e, keypad).zip(sizes).forEach { (path, size) ->
                        assertNotNull("$where ${e.id}", path)
                        assertEquals("$where $path", size, pngSize(pkg, path!!))
                    }
                }
            }
        }
    }

    /** The RetroPad binds a console's drawn skin presses: its D-pad, face buttons, shoulders and keys. */
    private fun drawnBinds(console: Console): Set<String> {
        val skin = console.skin
        val keys = skin.shouldersLeft + skin.shouldersRight + skin.pills
        val clusters = skin.clusters.flatMap { c ->
            when (c) {
                is SkinCluster.DPad -> c.binds
                is SkinCluster.Face -> c.buttons.mapNotNull { it.bind }
                is SkinCluster.Stick -> listOf(c.bind)
            }
        }
        return (keys.mapNotNull { it.bind } + clusters).toSet()
    }

    @Test fun everyControlPressesWhatTheDrawnSkinPresses() {
        for (file in packages(SkinVariant.CONSOLE)) {
            val pkg = read(file)
            val console = pkg.skin.console
            val drawn = drawnBinds(console)
            // Each button's label names the same control on the drawn skin, which presses the same bind.
            val byLabel = (console.skin.shouldersLeft + console.skin.shouldersRight + console.skin.pills).associate { it.label to it.bind } +
                console.skin.clusters.filterIsInstance<SkinCluster.Face>().flatMap { it.buttons }.associate { it.label to it.bind }
            for ((id, layout) in pkg.skin.layouts) {
                val where = "${file.name} ${id.id}"
                val pressed = layout.elements.flatMap { e ->
                    when (e.type) {
                        SkinElementType.DPAD -> OpenEmuxBinds.dpad(console, e.bind).also { assertNotNull("$where ${e.id}", it) }!!
                        SkinElementType.STICK -> listOf(if (e.bind == "rightAnalogStick") "analog_right" else "analog_left")
                        // The drawn skin's keypad key, which opens its keypad: the package's opens its keypad page.
                        SkinElementType.BUTTON -> if (e.bind == "menu") emptyList() else if (e.bind == SkinFormat.KEYPAD_TOGGLE) listOf(KEYPAD_BIND) else {
                            val bind = SkinBinds.bind(console, e.bind, hardcore = false)
                            assertNotNull("$where ${e.id}", bind)
                            byLabel[e.label]?.let { assertEquals("$where ${e.label}", it, bind) }
                            listOf(bind!!)
                        }
                        else -> emptyList()
                    }
                }
                assertEquals(where, drawn, pressed.toSet())
            }
        }
    }

    @Test fun theVirtualBoysRightPadIsTheSecondDpad() {
        val pkg = read(File(assets, "${SkinVariant.CONSOLE.assetDir}/VB.zip"))
        for ((id, layout) in pkg.skin.layouts) {
            val pads = layout.elements.filter { it.type == SkinElementType.DPAD }
            assertEquals(id.id, listOf("dpad" to "dpad", "dpad2" to "dpad2"), pads.map { it.id to it.bind })
            // Side by side, the second on the right, as the controller has them.
            assertTrue(id.id, pads[1].rect.x > pads[0].rect.x + pads[0].rect.width)
            assertEquals(listOf("dpad2.png", "dpad2-ball.png"), pkg.images(id, pads[1]).map { it!!.substringAfter('/') })
        }
    }

    @Test fun theAtari7800Reads1OnTheLeftAnd2OnTheRight() {
        // RT-458: the ProLine's 1, the button 2600 games read, lower left on B; 2 upper right on A.
        val pkg = read(File(assets, "${SkinVariant.CONSOLE.assetDir}/A7800.zip"))
        for ((id, layout) in pkg.skin.layouts) {
            val one = layout.elements.single { it.label == "1" }
            val two = layout.elements.single { it.label == "2" }
            assertEquals(id.id, "b" to "a", one.bind to two.bind)
            assertTrue(id.id, one.rect.x < two.rect.x && one.rect.y > two.rect.y)
        }
    }

    @Test fun everyQuickActionAndTheMenuPressSomething() {
        for (file in packages(SkinVariant.CONSOLE)) {
            val pkg = read(file)
            for ((id, layout) in pkg.skin.layouts) {
                val actions = layout.elements.filter { it.type == SkinElementType.ACTION }.map { it.bind }
                assertEquals("${file.name} ${id.id}", listOf("quickSave", "quickLoad", "turbo", "toggleFastForward"), actions)
                assertTrue(layout.elements.any { it.bind == "menu" })
                actions.forEach { assertNotNull(SkinBinds.bind(pkg.skin.console, it, false)) }
            }
        }
    }

    /** The consoles whose drawn skin has a keypad (issue 772): each package has a keypad page in every layout. */
    private val keypadConsoles = Console.entries.filter { it.skin.keypad.isNotEmpty() }

    @Test fun theKeypadConsolesHaveAKeypadPageInEveryLayout() {
        assertEquals(listOf("A5200", "CV", "INTV", "O2"), keypadConsoles.map { it.id })
        for (variant in listOf(SkinVariant.CONSOLE, SkinVariant.DARK)) for (console in keypadConsoles) {
            val pkg = read(File(assets, "${variant.assetDir}/${console.id}.zip"))
            val use = LayoutChoice.Use(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)
            assertTrue(console.id, keypadPages(pkg.skin, use))
            for ((id, layout) in pkg.skin.layouts) {
                val where = "${console.id} ${id.id}"
                val page = layout.keypad!!
                // The twelve keys, 1 to 9 then the keys beside the 0, and the toggle on both pages.
                val keys = page.elements.filter { it.type == SkinElementType.KEYPAD }
                assertEquals(where, (1..9).map { "keypad$it" } + listOf("keypadStar", "keypad0", "keypadHash"), keys.map { it.bind })
                assertEquals(where, 1, page.elements.count { it.bind == SkinFormat.KEYPAD_TOGGLE })
                assertEquals(where, 1, layout.elements.count { it.bind == SkinFormat.KEYPAD_TOGGLE })
                // The keys' labels are the drawn keypad's: CLR and ENT beside the 0 on the Intellivision and Odyssey 2.
                assertEquals(where, console.skin.keypad.last().map { it.label }, keys.takeLast(3).map { it.label })
                // The left control stays; the face buttons give way to the keypad.
                assertEquals(where, layout.elements.filter { it.type == SkinElementType.DPAD || it.type == SkinElementType.STICK }.map { it.rect },
                    page.elements.filter { it.type == SkinElementType.DPAD || it.type == SkinElementType.STICK }.map { it.rect })
                val face = layout.elements.filter { it.group == "face" }
                assertTrue(where, face.isNotEmpty() && page.elements.none { it.group == "face" })
                // On every layout's right side, centred on the same row as the D-pad or stick.
                val left = page.elements.first { it.type == SkinElementType.DPAD || it.type == SkinElementType.STICK }
                assertTrue(where, keys.all { it.rect.x > left.rect.x + left.rect.width })
                if (id.orientation == SkinOrientation.LANDSCAPE) {
                    val top = keys.minOf { it.rect.y }
                    val bottom = keys.maxOf { it.rect.y + it.rect.height }
                    assertEquals(where, left.rect.y + left.rect.height / 2f, (top + bottom) / 2f, 1.5f)
                }
            }
        }
    }

    @Test fun everyKeypadKeyPressesWhatTheDrawnKeypadPresses() {
        for (console in keypadConsoles) {
            val pkg = read(File(assets, "${SkinVariant.CONSOLE.assetDir}/${console.id}.zip"))
            val cores = listOf<String?>(null) + console.cores.map { it.id }
            for (core in cores) {
                val drawn = console.skin.forCore(core).keypad.flatten().map { it.bind }
                for ((id, layout) in pkg.skin.layouts) {
                    val plan = planPage(console, layout, 1080, 2340, hardcore = false, coreId = core, keypadPage = true, toggle = "portrait")
                    val keys = plan.elements.filterIsInstance<ElementPlan.Button>().filter { layout.keypad!!.elements[it.index].type == SkinElementType.KEYPAD }
                    // Row by row as drawn: the keys beside the 0 are the drawn keypad's first and last of its last row.
                    assertEquals("${console.id} $core ${id.id}", drawn, keys.map { it.bind })
                }
            }
        }
    }

    @Test fun theColecoVisionFiresAreLOnAAndROnB() {
        val pkg = read(File(assets, "${SkinVariant.CONSOLE.assetDir}/CV.zip"))
        for ((id, layout) in pkg.skin.layouts) {
            val l = layout.elements.single { it.label == "L" }
            val r = layout.elements.single { it.label == "R" }
            assertEquals(id.id, "a" to "b", OpenEmuxBinds.button(Console.CV, l.bind) to OpenEmuxBinds.button(Console.CV, r.bind))
            // Gearcoleco reads them the other way round (data/Skin.kt).
            assertEquals(id.id, "b" to "a", OpenEmuxBinds.button(Console.CV, l.bind, "gearcoleco") to OpenEmuxBinds.button(Console.CV, r.bind, "gearcoleco"))
        }
    }
}
