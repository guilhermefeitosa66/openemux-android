package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinVariant
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertNull(ImportedSkinRenderer.imported(base, Console.SFC))
        val dir = File(base, "skins/SFC").apply { mkdirs() }
        File(dir, "notes.txt").writeText("not a skin")
        File(dir, "folder.deltaskin").mkdirs()
        assertNull(ImportedSkinRenderer.imported(base, Console.SFC))
        File(dir, "b.manicskin").writeText("")
        File(dir, "a.DELTASKIN").writeText("")
        assertEquals("a.DELTASKIN", ImportedSkinRenderer.imported(base, Console.SFC)?.name)
        assertNull(ImportedSkinRenderer.imported(base, Console.FC))
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
        val own = File(assets, SkinVariant.CONSOLE.assetDir).listFiles { f -> f.isFile }.orEmpty()
        assertEquals(12, own.size)
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

    private fun info(pkg: File) = ZipFile(pkg).use { z -> JSONObject(z.getInputStream(z.getEntry("info.json")).bufferedReader().readText()) }

    private fun entries(pkg: File) = ZipFile(pkg).use { z -> z.entries().asSequence().map { it.name }.toSortedSet() }
}
