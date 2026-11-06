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

    @Test fun aZipIsAPackageInTheOwnFormat() {
        assertTrue(ImportedSkinRenderer.isOpenEmuxPackage(File("GG.zip")))
        assertTrue(ImportedSkinRenderer.isOpenEmuxPackage(File("PCE.ZIP")))
        assertFalse(ImportedSkinRenderer.isOpenEmuxPackage(File("MD.deltaskin")))
        assertFalse(ImportedSkinRenderer.isOpenEmuxPackage(File("PS.manicskin")))
        assertEquals("GG.zip", ImportedSkinRenderer.bundledName(arrayOf("dark", "FC.deltaskin", "GG.zip"), Console.GG))
    }

    @Test fun theBundledPackageIsTheOneNamedForTheConsole() {
        val names = arrayOf("dark", "FC.deltaskin", "GB.manicskin", "GBA.deltaskin")
        assertEquals("FC.deltaskin", ImportedSkinRenderer.bundledName(names, Console.FC))
        assertEquals("GB.manicskin", ImportedSkinRenderer.bundledName(names, Console.GB))
        assertNull(ImportedSkinRenderer.bundledName(names, Console.GBC))
        assertNull(ImportedSkinRenderer.bundledName(null, Console.FC))
        assertNull(ImportedSkinRenderer.bundledName(arrayOf("FC"), Console.FC))
    }

    private val catalog by lazy { File(assets, SkinCatalog.ASSET).readText() }

    /**
     * Every official color the catalog lists for a console (issue 720) ships as a package in OpenEmux's format, with a
     * swatch, and a console that ships none lists nothing (its same layout is OwnFormatBundledSkinsTest's). Issue 808:
     * every bundled package is a zip, the DS's included.
     */
    @Test fun everyColorVariantInTheCatalogShips() {
        var colors = 0
        Console.entries.forEach { console ->
            val listed = SkinCatalog.parse(catalog, console)
            if (!File(assets, "${SkinVariant.CONSOLE.assetDir}/${console.id}.zip").isFile) {
                assertEquals("${console.id} in the catalog", emptyList<SkinVariant>(), listed.map { it.variant })
                return@forEach
            }
            assertEquals(console.id, listOf(SkinVariant.CONSOLE, SkinVariant.DARK), listed.take(2).map { it.variant })
            listed.drop(2).forEach { swatch ->
                assertTrue("no ${swatch.variant.id} skin for ${console.id}", File(assets, "${swatch.variant.assetDir}/${console.id}.zip").isFile)
                assertTrue("${swatch.variant.id}/${console.id} has no swatch", swatch.body != null && swatch.key != null)
                colors++
            }
        }
        assertEquals(38, colors)
    }

    /** No package of any kind but OpenEmux's ships in any variant folder (issue 808: the DS's was the last). */
    @Test fun noDeltaOrManicPackageShips() {
        val left = assets.resolve("skins").walkTopDown().filter { it.isFile && (it.name.endsWith(".deltaskin") || it.name.endsWith(".manicskin")) }
        assertEquals(emptyList<String>(), left.map { it.path }.toList())
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
}
