package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.SkinCatalog
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinScreenPage
import com.openemux.mobile.data.SkinStore
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
 * the consoles with a keypad; issue 806: the eleven single-screen consoles that shipped Delta/Manic packages, with
 * their official colours; issue 808: the DS, with its screen-layout pages), read by the app's own reader: the six layouts, every image at its element's size (PNG for
 * the console's own colours and Dark, WebP for a colour variant), every variant with the same layout, and every
 * control pressing what the drawn skin's presses for that console.
 */
class OwnFormatBundledSkinsTest {
    private val assets = File("src/main/assets")

    private fun packages(variant: SkinVariant) = File(assets, variant.assetDir).listFiles()!!
        .filter { it.isFile && ImportedSkinRenderer.isOpenEmuxPackage(it) }.sortedBy { it.name }

    /** Every own-format package in every variant folder, with its variant. */
    private fun allPackages(): List<Pair<SkinVariant, File>> = SkinVariant.entries.flatMap { v -> packages(v).map { v to it } }

    private fun read(file: File): SkinPackage = SkinFormat.readZip(file)

    private val catalog by lazy { File(assets, SkinCatalog.ASSET).readText() }

    /** A PNG's width and height from its header, or, with [webp], a WebP's (issue 806: a colour variant's images). */
    private fun imageSize(pkg: SkinPackage, path: String, webp: Boolean): Pair<Int, Int> = pkg.source.open(path)!!.use { input ->
        val bytes = input.readBytes()
        assertEquals("$path is a ${if (webp) "WebP" else "PNG"}", if (webp) ".webp" else ".png", "." + path.substringAfterLast('.'))
        if (!webp) {
            assertEquals("$path is a PNG", 0x89.toByte(), bytes[0])
            val data = DataInputStream(bytes.inputStream(16, 8))
            return data.readInt() to data.readInt()
        }
        assertEquals("$path is a WebP", "RIFFWEBP", String(bytes, 0, 4) + String(bytes, 8, 4))
        fun le(at: Int, n: Int) = (0 until n).fold(0) { acc, i -> acc or ((bytes[at + i].toInt() and 0xFF) shl (8 * i)) }
        when (val chunk = String(bytes, 12, 4)) {
            // The extended form (what an image with alpha is): the canvas's width and height less one, 24 bits each.
            "VP8X" -> le(24, 3) + 1 to le(27, 3) + 1
            // Lossless: 14 bits each, less one, after the signature byte.
            "VP8L" -> le(21, 4).let { (it and 0x3FFF) + 1 to ((it shr 14) and 0x3FFF) + 1 }
            "VP8 " -> (le(26, 2) and 0x3FFF) to (le(28, 2) and 0x3FFF)
            else -> throw AssertionError("$path: WebP chunk $chunk")
        }
    }

    @Test fun theConsolesShipInTheOwnFormat() {
        val ids = packages(SkinVariant.CONSOLE).map { it.name.removeSuffix(".zip") }
        assertEquals(listOf(
            "A2600", "A5200", "A7800", "CV", "FC", "FDS", "GB", "GBA", "GBC", "GC", "GG", "INTV", "LYNX", "MCD", "MD", "N64", "NDS", "NGP",
            "O2", "PCE", "PCECD", "PS", "PSP", "S32X", "SATURN", "SFC", "SG1000", "SMS", "VB", "VECTREX", "WS",
        ), ids)
        assertEquals(ids, packages(SkinVariant.DARK).map { it.name.removeSuffix(".zip") })
        // Issue 808: no Delta/Manic package is left in any variant folder, the DS's moved last.
        for (v in SkinVariant.entries) {
            val left = File(assets, v.assetDir).listFiles().orEmpty().filter { it.isFile && SkinStore.isPackageName(it.name) }.map { it.name }
            assertEquals(v.id, emptyList<String>(), left)
        }
    }

    @Test fun everyVariantInTheCatalogShipsAPackage() {
        // Issue 806: every variant the picker lists for a console in the own format is a zip in that variant's folder.
        var colors = 0
        for (file in packages(SkinVariant.CONSOLE)) {
            val console = read(file).skin.console
            val listed = SkinCatalog.parse(catalog, console).map { it.variant }
            assertEquals(console.id, listOf(SkinVariant.CONSOLE, SkinVariant.DARK), listed.take(2))
            for (v in listed) assertTrue("${v.id}/${file.name}", File(assets, "${v.assetDir}/${file.name}").isFile)
            colors += listed.size - 2
        }
        // The 33 official colours of the eleven consoles issue 806 moved and the DS's five (issue 808).
        assertEquals(38, colors)
        // And no zip ships that the catalog does not list.
        for ((v, file) in allPackages()) {
            val console = read(file).skin.console
            assertTrue("${v.id}/${file.name}", SkinCatalog.parse(catalog, console).any { it.variant == v })
        }
    }

    @Test fun everyPackageHasTheSixLayoutsForItsConsole() {
        for ((_, file) in allPackages()) {
            val pkg = read(file)
            assertEquals(file.name.removeSuffix(".zip"), pkg.skin.console.id)
            assertEquals(file.path, SkinLayoutId.entries.toSet(), pkg.skin.layouts.keys)
        }
    }

    @Test fun everyVariantHasTheSameLayout() {
        for (file in packages(SkinVariant.CONSOLE)) {
            val own = read(file).skin
            for ((v, twin) in allPackages().filter { (v, f) -> v != SkinVariant.CONSOLE && f.name == file.name }) {
                val other = read(twin).skin
                // Dark, or the colour's name (issue 720): "OpenEmux Game Boy Red".
                if (v == SkinVariant.DARK) assertEquals("${own.name} Dark", other.name)
                else assertTrue(other.name, other.name.startsWith("${own.name} ") && other.name != "${own.name} Dark")
                for ((id, layout) in own.layouts) {
                    val d = other.layouts.getValue(id)
                    val where = "${v.id}/${file.name} ${id.id}"
                    assertEquals(where, layout.size, d.size)
                    assertEquals(where, layout.screens, d.screens)
                    assertEquals(where, layout.frame?.rect, d.frame?.rect)
                    for (keypad in listOf(false, true)) {
                        assertEquals(where, layout.elements(keypad).map { listOf(it.id, it.type, it.bind, it.rect, it.label) },
                            d.elements(keypad).map { listOf(it.id, it.type, it.bind, it.rect, it.label) })
                    }
                    // The DS's screen-layout pages (issue 808) too.
                    assertEquals(where, layout.screenLayouts.keys, d.screenLayouts.keys)
                    for ((page, p) in layout.screenLayouts) {
                        val q = d.screenLayouts.getValue(page)
                        assertEquals("$where $page", p.screens, q.screens)
                        assertEquals("$where $page", p.frame?.rect, q.frame?.rect)
                        assertEquals("$where $page", p.elements.map { listOf(it.id, it.type, it.bind, it.rect, it.label) },
                            q.elements.map { listOf(it.id, it.type, it.bind, it.rect, it.label) })
                    }
                }
            }
        }
    }

    @Test fun everyImageIsThereAtItsSize() {
        for ((v, file) in allPackages()) {
            val pkg = read(file)
            // The console's own colours and Dark are PNG (issue 718), every official colour WebP (issues 720, 806).
            val webp = v != SkinVariant.CONSOLE && v != SkinVariant.DARK
            fun pngSize(pkg: SkinPackage, path: String) = imageSize(pkg, path, webp)
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

    /**
     * Issue 808: the DS, in every variant, has the three screen-layout pages in every layout (what its Delta package drew
     * as one art per screen layout), each image there at its size, and SCREENS on every page to step on.
     */
    @Test fun theDsHasEveryLayoutsScreenLayoutPagesInEveryVariant() {
        val ds = allPackages().filter { (_, f) -> f.name == "NDS.zip" }
        assertEquals(listOf("console", "dark", "black", "pink", "turquoise", "blue", "white").sorted(), ds.map { it.first.id }.sorted())
        for ((v, file) in ds) {
            val pkg = read(file)
            val webp = v != SkinVariant.CONSOLE && v != SkinVariant.DARK
            for ((id, layout) in pkg.skin.layouts) {
                assertEquals(id.id, SkinScreenPage.IDS, layout.screenLayouts.keys.toList())
                assertEquals(id.id, 2, layout.screens.size)
                assertTrue(id.id, layout.elements.any { it.bind == "screens" })
                for ((page, p) in layout.screenLayouts) {
                    val where = "${v.id} ${id.id} $page"
                    assertEquals(where, SkinScreenPage.SCREENS.getValue(page), p.screens.size)
                    assertTrue(where, p.elements.any { it.bind == "screens" })
                    // The same controls as the layout, each where the page puts it.
                    assertEquals(where, layout.elements.map { it.bind }.sorted(), p.elements.map { it.bind }.sorted())
                    assertEquals(where, layout.size.width to layout.size.height, imageSize(pkg, pkg.screenBackground(id, page)!!, webp))
                    assertEquals(where, p.frame!!.rect.width.toInt() to p.frame!!.rect.height.toInt(), imageSize(pkg, pkg.screenFrame(id, page)!!, webp))
                    for (e in p.elements) {
                        val paths = pkg.screenImages(id, page, e)
                        assertTrue("$where ${e.id}", paths.isNotEmpty())
                        assertEquals("$where ${e.id}", e.rect.width.toInt() to e.rect.height.toInt(), imageSize(pkg, paths[0]!!, webp))
                        paths.forEach { assertNotNull("$where ${e.id}", it) }
                    }
                }
            }
            // Played on a phone, both phone layouts draw their pages.
            assertTrue(v.id, ndsPages(pkg.skin, LayoutChoice.Use(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)))
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
                // Issue 806: the consoles that were Delta/Manic packages keep the slow-motion key the app gave those.
                val slow = if (pkg.skin.console in SLOW_MOTION) listOf("slowMotion") else emptyList()
                // The DS's strip ends with SCREENS, the step to its next screen layout, as its Delta package's did.
                val screens = if (pkg.skin.console == Console.NDS) listOf("screens") else emptyList()
                assertEquals("${file.name} ${id.id}", listOf("quickSave", "quickLoad", "turbo") + slow + "toggleFastForward" + screens, actions)
                assertTrue(layout.elements.any { it.bind == "menu" })
                // SCREENS presses nothing itself: planPage makes it the step to the next page (issue 807).
                (actions - "screens").forEach { assertNotNull(it, OpenEmuxBinds.action(pkg.skin.console, it, false)?.bind) }
            }
        }
    }

    private val SLOW_MOTION = setOf(
        Console.FC, Console.SFC, Console.GB, Console.GBC, Console.GBA, Console.MD, Console.SMS, Console.N64, Console.GC,
        Console.PS, Console.PSP,
    )

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
                // On every layout's right side, centred on the same row as the D-pad or stick: face to face, the
                // relief under a key (the rect's last rows) being no part of where it is.
                val left = page.elements.first { it.type == SkinElementType.DPAD || it.type == SkinElementType.STICK }
                assertTrue(where, keys.all { it.rect.x > left.rect.x + left.rect.width })
                if (id.orientation == SkinOrientation.LANDSCAPE) {
                    val top = keys.minOf { it.face.y }
                    val bottom = keys.maxOf { it.face.y + it.face.height }
                    assertEquals(where, left.face.y + left.face.height / 2f, (top + bottom) / 2f, 1.5f)
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
