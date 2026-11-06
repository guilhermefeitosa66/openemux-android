package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinStoreException
import com.openemux.mobile.data.SkinVariant
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * "Create copy and edit" (issue 783): which bundled skins can be made into the player's own today (the packages in
 * OpenEmux's format), and every one of those copied reads back with the same layouts, elements and files.
 */
class SkinConversionTest {
    private val assets = File("src/main/assets")
    private lateinit var base: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-conversion").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private fun lister(vararg folders: Pair<String, Array<String>>): (String) -> Array<String>? = folders.toMap()::get

    @Test fun aPackageInOpenEmuxsFormatCanBeConverted() {
        val list = lister("skins" to arrayOf("GG.zip", "SFC.deltaskin"), "skins/dark" to arrayOf("GG.zip"))
        assertEquals(SkinConversion.Kind.OPENEMUX, SkinConversion.kind(list, Console.GG, SkinVariant.CONSOLE))
        assertEquals(SkinConversion.Kind.OPENEMUX, SkinConversion.kind(list, Console.GG, SkinVariant.DARK))
    }

    @Test fun deltaAndManicPackagesCannotYet() {
        val list = lister("skins" to arrayOf("SFC.deltaskin", "GB.manicskin"))
        assertNull(SkinConversion.kind(list, Console.SFC, SkinVariant.CONSOLE))
        assertNull(SkinConversion.kind(list, Console.GB, SkinVariant.CONSOLE))
    }

    @Test fun aConsoleWithNoPackageConvertsItsDrawnSkin() {
        val list = lister("skins" to arrayOf("SFC.deltaskin"))
        assertEquals(SkinConversion.Kind.DRAWN, SkinConversion.kind(list, Console.O2, SkinVariant.CONSOLE))
        assertEquals(SkinConversion.Kind.DRAWN, SkinConversion.kind(list, Console.O2, SkinVariant.DARK))
        assertEquals(SkinConversion.Kind.DRAWN, SkinConversion.kind(lister(), Console.GG, SkinVariant.CONSOLE))
    }

    @Test fun aVariantWithNoPackageOfItsOwnFollowsTheConsolesOwn() {
        val list = lister("skins" to arrayOf("GG.zip"), "skins/dark" to arrayOf("FC.deltaskin"))
        assertEquals(SkinConversion.Kind.OPENEMUX, SkinConversion.kind(list, Console.GG, SkinVariant.RED))
        assertEquals(SkinConversion.Kind.OPENEMUX, SkinConversion.kind(list, Console.GG, SkinVariant.DARK))
        // The variant's own package wins over the console's, whatever its format.
        val delta = lister("skins" to arrayOf("FC.zip"), "skins/red" to arrayOf("FC.deltaskin"))
        assertNull(SkinConversion.kind(delta, Console.FC, SkinVariant.RED))
    }

    @Test fun theAppsOwnAssetsAnswerForTheOwnFormatAndTheDrawnConsoles() {
        val list = { dir: String -> File(assets, dir).list() }
        fun kind(k: SkinConversion.Kind) = Console.entries.filter { SkinConversion.kind(list, it, SkinVariant.CONSOLE) == k }.map { it.id }.sorted()
        // Issue 770 shipped eight consoles in OpenEmux's format, issue 771 seven more.
        assertEquals(
            listOf("A2600", "A7800", "FDS", "GG", "LYNX", "MCD", "NGP", "PCE", "PCECD", "S32X", "SATURN", "SG1000", "VB", "VECTREX", "WS"),
            kind(SkinConversion.Kind.OPENEMUX),
        )
        // Issue 798: the consoles left on the drawn skin.
        assertEquals(listOf("A5200", "CV", "INTV", "O2"), kind(SkinConversion.Kind.DRAWN))
    }

    @Test fun aDrawnSkinIsCopiedWithItsImagesAndNothingLeftInTheCache() {
        val cache = File(base, "cache").apply { mkdirs() }
        val painted = mutableListOf<String>()
        val ref = SkinConversion.convertDrawn(store, Console.O2, "My Odyssey", cache) { art, file ->
            painted += "${art.layout.id}/${art.file}"
            file.writeBytes(byteArrayOf(art.width.toByte()))
        }
        val dir = store.file(Console.O2, ref)!!
        val copy = SkinFormat.readFolder(dir)
        assertEquals("My Odyssey", copy.skin.name)
        assertEquals(DrawnSkinCopy.convert(Console.O2, "My Odyssey").skin, copy.skin)
        assertEquals(painted.toSet() + SkinFormat.SKIN_JSON, copy.source.files)
        assertEquals(24, painted.size)
        val listed = store.list(Console.O2).single { it.ref == ref }
        assertEquals(SkinForm.entries.toSet(), listed.forms)
        assertEquals(emptyList<String>(), cache.list()!!.toList())
    }

    @Test fun aDrawnCopyCutShortLeavesNothing() {
        val cache = File(base, "cache").apply { mkdirs() }
        assertThrows(IllegalStateException::class.java) {
            SkinConversion.convertDrawn(store, Console.CV, "Mine", cache) { _, _ -> throw IllegalStateException("disk full") }
        }
        assertEquals(emptyList<Any>(), store.list(Console.CV))
        assertEquals(emptyList<String>(), cache.list()!!.toList())
        // A cache that cannot hold the folder is refused before anything is written.
        val file = File(base, "not-a-folder").apply { writeText("x") }
        assertThrows(SkinStoreException::class.java) { SkinConversion.convertDrawn(store, Console.CV, "Mine", file) { _, _ -> } }
        assertEquals(emptyList<Any>(), store.list(Console.CV))
    }

    @Test fun noPackageOrOneNotInOpenEmuxsFormatIsRefused() {
        assertThrows(SkinStoreException::class.java) { SkinConversion.convert(store, Console.GG, null, "Mine") }
        val delta = File(base, "SFC.deltaskin").apply { writeText("x") }
        assertThrows(SkinStoreException::class.java) { SkinConversion.convert(store, Console.SFC, delta, "Mine") }
        assertEquals(emptyList<Any>(), store.list(Console.SFC))
    }

    /** Every own-format package the app ships, in every variant folder. */
    private fun ownFormatPackages(): List<Pair<SkinVariant, File>> = SkinVariant.entries.flatMap { v ->
        File(assets, v.assetDir).listFiles().orEmpty().filter { it.isFile && ImportedSkinRenderer.isOpenEmuxPackage(it) }.map { v to it }
    }

    @Test fun everyOwnFormatBundledSkinCopiesWithEveryLayoutElementAndFile() {
        val packages = ownFormatPackages()
        assertEquals(30, packages.size)
        for ((variant, zip) in packages) {
            val where = "${variant.id} ${zip.name}"
            val source = SkinFormat.readZip(zip)
            val console = source.skin.console
            val before = zip.readBytes()
            val ref = SkinConversion.convert(store, console, zip, "${source.skin.name} copy")
            val dir = store.file(console, ref)!!
            val copy = SkinFormat.readFolder(dir)
            assertEquals(where, source.skin.copy(name = "${source.skin.name} copy"), copy.skin)
            assertEquals(where, SkinLayoutId.entries.toSet(), copy.skin.layouts.keys)
            assertEquals(where, source.source.files, copy.source.files)
            for (path in source.source.files - SkinFormat.SKIN_JSON) {
                assertArrayEquals("$where $path", source.source.open(path)!!.use { it.readBytes() }, File(dir, path).readBytes())
            }
            assertEquals(where, source.skin.forms.toSet(), store.list(console).first { it.ref == ref }.forms)
            assertArrayEquals("$where is only read", before, zip.readBytes())
        }
    }
}
