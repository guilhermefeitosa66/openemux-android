package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinVariant
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
 * 771: the Atari consoles, the Lynx, the Neo Geo Pocket, the WonderSwan, the Vectrex and the Virtual Boy), read
 * by the app's own reader: the six layouts, every image at its element's size, a Dark twin with the same layout, and
 * every control pressing what the drawn skin's presses for that console.
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
        assertEquals(listOf("A2600", "A7800", "FDS", "GG", "LYNX", "MCD", "NGP", "PCE", "PCECD", "S32X", "SATURN", "SG1000", "VB", "VECTREX", "WS"), ids)
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
                assertEquals(where, layout.elements.map { listOf(it.id, it.type, it.bind, it.rect, it.label) },
                    d.elements.map { listOf(it.id, it.type, it.bind, it.rect, it.label) })
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
                for (e in layout.elements) {
                    val w = e.rect.width.toInt()
                    val h = e.rect.height.toInt()
                    val sizes = if (e.type == SkinElementType.DPAD) {
                        val side = floor(min(w, h) * OverlayLines.DPAD_TRAVEL.toDouble() + 0.5).toInt()
                        listOf(w to h, side to side)
                    } else {
                        listOf(w to h)
                    }
                    pkg.images(id, e).zip(sizes).forEach { (path, size) ->
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
                        SkinElementType.BUTTON -> if (e.bind == "menu") emptyList() else {
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
}
