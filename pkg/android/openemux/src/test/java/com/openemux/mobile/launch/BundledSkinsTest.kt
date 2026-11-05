package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinCatalog
import com.openemux.mobile.data.SkinVariant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class BundledSkinsTest {
    private val assets = File("src/main/assets")

    @Test fun theUsersOwnPackageIsFoundFirstByName() {
        val base = Files.createTempDirectory("skins-base").toFile()
        try {
            assertNull(ImportedSkinRenderer.imported(base, Console.SFC))
            val dir = File(base, "skins/SFC").apply { mkdirs() }
            File(dir, "notes.txt").writeText("not a skin")
            File(dir, "folder.deltaskin").mkdirs()
            assertNull(ImportedSkinRenderer.imported(base, Console.SFC))
            File(dir, "b.manicskin").writeText("")
            File(dir, "a.DELTASKIN").writeText("")
            assertEquals("a.DELTASKIN", ImportedSkinRenderer.imported(base, Console.SFC)?.name)
            assertNull(ImportedSkinRenderer.imported(base, Console.FC))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun aSkinFolderInTheOwnFormatCompetesByName() {
        val base = Files.createTempDirectory("skins-base").toFile()
        try {
            val dir = File(base, "skins/GBA").apply { mkdirs() }
            // A folder without a skin.json is no skin.
            File(dir, "0-empty").mkdirs()
            File(dir, "b.deltaskin").writeText("")
            assertEquals("b.deltaskin", ImportedSkinRenderer.imported(base, Console.GBA)?.name)
            File(dir, "a-mine").apply { mkdirs() }.resolve("skin.json").writeText("{}")
            assertEquals("a-mine", ImportedSkinRenderer.imported(base, Console.GBA)?.name)
            assertTrue(ImportedSkinRenderer.isOpenEmuxSkin(File(dir, "a-mine")))
            assertFalse(ImportedSkinRenderer.isOpenEmuxSkin(File(dir, "0-empty")))
            assertFalse(ImportedSkinRenderer.isOpenEmuxSkin(File(dir, "b.deltaskin")))
            File(dir, "0-empty/skin.json").mkdirs()
            assertFalse(ImportedSkinRenderer.isOpenEmuxSkin(File(dir, "0-empty")))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun theBundledPackageIsTheOneNamedForTheConsole() {
        val names = arrayOf("dark", "FC.deltaskin", "GB.manicskin", "GBA.deltaskin")
        assertEquals("FC.deltaskin", ImportedSkinRenderer.bundledName(names, Console.FC))
        assertEquals("GB.manicskin", ImportedSkinRenderer.bundledName(names, Console.GB))
        assertNull(ImportedSkinRenderer.bundledName(names, Console.GBC))
        assertNull(ImportedSkinRenderer.bundledName(null, Console.FC))
        assertNull(ImportedSkinRenderer.bundledName(arrayOf("FC"), Console.FC))
    }

    /** Every console the app ships a skin for has a Dark one, in the same format and with the same layout. */
    @Test fun everyBundledSkinHasADarkTwinWithTheSameLayout() {
        val own = packages(File(assets, SkinVariant.CONSOLE.assetDir))
        assertTrue(own.isNotEmpty())
        own.forEach { pkg ->
            val dark = File(assets, "${SkinVariant.DARK.assetDir}/${pkg.name}")
            assertTrue("no Dark skin for ${pkg.name}", dark.isFile)
            val a = info(pkg)
            val b = info(dark)
            assertEquals(a.getString("gameTypeIdentifier"), b.getString("gameTypeIdentifier"))
            assertEquals("${a.getString("name")} Dark", b.getString("name"))
            assertEquals("${a.getString("identifier")}.dark", b.getString("identifier"))
            assertEquals(pkg.name, a.getJSONObject("representations").toString(), b.getJSONObject("representations").toString())
            assertEquals(pkg.name, entries(pkg), entries(dark))
        }
    }

    private fun packages(dir: File) =
        dir.listFiles { f -> f.isFile && (f.name.endsWith(".deltaskin") || f.name.endsWith(".manicskin")) }.orEmpty().sortedBy { it.name }

    private val catalog by lazy { File(assets, SkinCatalog.ASSET).readText() }

    /**
     * Every official color the catalog lists for a console (issue 720) ships as a package with the console's own
     * layout: the same representations, frames, screens and knobs, only the art in WebP instead of PNG.
     */
    @Test fun everyColorVariantInTheCatalogShipsWithTheConsolesLayout() {
        var colors = 0
        Console.entries.forEach { console ->
            val listed = SkinCatalog.parse(catalog, console)
            val own = packages(File(assets, SkinVariant.CONSOLE.assetDir)).firstOrNull { it.name.substringBeforeLast('.') == console.id }
            if (own == null) {
                assertTrue("${console.id} has no skin but the catalog lists it", listed.isEmpty())
                return@forEach
            }
            assertEquals(console.id, listOf(SkinVariant.CONSOLE, SkinVariant.DARK), listed.take(2).map { it.variant })
            listed.drop(2).forEach { swatch ->
                val v = swatch.variant
                val pkg = File(assets, "${v.assetDir}/${own.name}")
                assertTrue("no ${v.id} skin for ${own.name}", pkg.isFile)
                val a = info(own)
                val b = info(pkg)
                assertEquals(a.getString("gameTypeIdentifier"), b.getString("gameTypeIdentifier"))
                assertEquals("${a.getString("identifier")}.${v.id}", b.getString("identifier"))
                assertTrue(b.getString("name"), b.getString("name").startsWith("${a.getString("name")} "))
                assertEquals(
                    "${v.id}/${own.name}", a.getJSONObject("representations").toString(),
                    b.getJSONObject("representations").toString().replace(".webp", ".png"),
                )
                assertEquals("${v.id}/${own.name}", entries(own), entries(pkg).map { it.replace(".webp", ".png") }.toSortedSet())
                assertTrue(entries(pkg).none { it.endsWith(".png") })
                assertTrue("${v.id}/${own.name} has no swatch", swatch.body != null && swatch.key != null)
                colors++
            }
        }
        assertEquals(38, colors)
    }

    /** No color package ships that the catalog does not list, so the picker shows every one the APK carries. */
    @Test fun everyColorPackageIsInTheCatalog() {
        SkinVariant.entries.filter { it.description == null }.forEach { v ->
            packages(File(assets, v.assetDir)).forEach { pkg ->
                val console = Console.entries.firstOrNull { it.id == pkg.name.substringBeforeLast('.') }
                assertTrue("${v.id}/${pkg.name} is not in the catalog", console != null && SkinCatalog.parse(catalog, console).any { it.variant == v })
            }
        }
    }

    /** Every id the catalog lists is one this build knows, so none is silently left out of the picker. */
    @Test fun theCatalogListsOnlyKnownVariants() {
        val json = JSONObject(catalog)
        json.keys().forEach { console ->
            val list = json.getJSONArray(console)
            (0 until list.length()).forEach { i ->
                val id = list.getJSONObject(i).getString("id")
                assertTrue("$console lists $id", SkinVariant.entries.any { it.id == id })
            }
        }
    }

    private fun info(pkg: File) = ZipFile(pkg).use { z -> JSONObject(z.getInputStream(z.getEntry("info.json")).bufferedReader().readText()) }

    private fun entries(pkg: File) = ZipFile(pkg).use { z -> z.entries().asSequence().map { it.name }.toSortedSet() }
}
