package com.openemux.mobile.data.theme

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The themes folder (issue 1018): saving, the user's actions, and theme files in and out. */
class ThemeStoreTest {
    private lateinit var root: File
    private lateinit var work: File
    private lateinit var store: ThemeStore
    private val logged = mutableListOf<String>()

    /** "Decodes" anything that starts with the PNG signature, and "re-encodes" it by copying. */
    private val codec = ThemeImageCodec { input, output ->
        val bytes = input.readBytes()
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
            true
        } else false
    }

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    @Before fun setUp() {
        root = Files.createTempDirectory("themes").toFile()
        work = Files.createTempDirectory("theme-work").toFile()
        store = ThemeStore(File(root, "themes"), codec) { logged += it }
    }

    @After fun tearDown() {
        root.deleteRecursively()
        work.deleteRecursively()
    }

    private fun picture(name: String = "pic.png", bytes: ByteArray = png) = File(work, name).apply { writeBytes(bytes) }

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>) = File(work, name).apply {
        ZipOutputStream(outputStream()).use { z ->
            for ((path, bytes) in entries) {
                z.putNextEntry(ZipEntry(path))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }

    private fun failure(file: File) = assertThrows(ThemeImportError::class.java) { store.import(file) }.reason

    @Test fun createListGetRenameDelete() {
        assertTrue(store.list().isEmpty())
        val b = store.create("  Beta ", ThemeBase.LIGHT, mapOf("accent" to "#00ff00", "bogus" to 1))
        val a = store.create("alpha", ThemeBase.DARK)
        assertEquals("beta", b.id)
        assertEquals(mapOf("accent" to 0xFF00FF00.toInt()), b.values)
        assertEquals(listOf("alpha", "Beta"), store.list().map { it.name })
        assertEquals(b, store.get("beta"))
        assertNull(store.get("system"))
        assertNull(store.get(null))
        assertNull(store.get("missing"))
        assertEquals("Gamma", store.rename(a.id, " Gamma ").name)
        assertEquals("alpha", store.get("alpha")!!.id)
        assertThrows(IllegalArgumentException::class.java) { store.rename(a.id, "  ") }
        assertThrows(NoSuchElementException::class.java) { store.rename("nope", "x") }
        assertThrows(IllegalArgumentException::class.java) { store.create(" ", ThemeBase.DARK) }
        assertThrows(IllegalArgumentException::class.java) { store.create("x", "sepia") }
        assertThrows(IllegalArgumentException::class.java) { store.delete("../etc") }
        store.delete("beta")
        assertEquals(listOf("alpha"), store.list().map { it.id })
        assertFalse(File(store.root, ".theme.yaml.tmp").exists())
    }

    @Test fun idsNeverClashAndNamesGetANumber() {
        store.create("Night", ThemeBase.DARK)
        assertEquals("night-2", store.create("Night", ThemeBase.DARK).id)
        assertEquals("night-3", store.newId("night"))
        assertEquals("Night (2)", store.freeName("Night"))
        assertEquals("night (2)", store.freeName("night"))
        store.create("Solo", ThemeBase.DARK)
        assertEquals("Solo", store.freeName("Solo", except = "solo"))
        assertEquals("Fresh", store.freeName("Fresh"))
        store.create("x".repeat(64), ThemeBase.DARK)
        val long = store.freeName("x".repeat(64))
        assertEquals(64, long.length)
        assertTrue(long.endsWith(" (2)"))
        assertNotEquals("system", store.newId("System"))
    }

    @Test fun brokenFoldersAreSkippedAndLogged() {
        store.create("Good", ThemeBase.DARK)
        File(store.root, "bad-yaml").apply { mkdirs() }.resolve("theme.yaml").writeText("a: 'open")
        File(store.root, "bad-base").apply { mkdirs() }.resolve("theme.yaml").writeText("base: sepia\n")
        File(store.root, "huge").apply { mkdirs() }.resolve("theme.yaml").writeText("x".repeat(ThemeYaml.MAX_BYTES + 1))
        File(store.root, "no-file").mkdirs()
        File(store.root, "Not An Id").mkdirs()
        File(store.root, "loose.txt").writeText("x")
        File(store.root, "extra").apply { mkdirs() }.resolve("theme.yaml").writeText("base: dark\nsidebar_width: 300\n")
        assertEquals(listOf("extra", "good"), store.list().map { it.id })
        assertTrue(logged.any { "bad-yaml" in it })
        assertTrue(logged.any { "ignoring sidebar_width" in it })
    }

    @Test fun picturesAreStagedSavedAndPruned() {
        val staging = File(work, "staging")
        val (path, file) = store.stage(ThemeKeys.LIBRARY_IMAGE, picture(), staging, stamp = 35)!!
        assertEquals("images/library-z.jpg", path)
        assertTrue(file.isFile)
        assertNull(store.stage(ThemeKeys.SIDEBAR_IMAGE, picture("bad.png", byteArrayOf(1, 2)), staging))
        assertThrows(IllegalArgumentException::class.java) { store.stage("accent", picture(), staging) }
        val t = store.create("Pics", ThemeBase.DARK, mapOf(ThemeKeys.LIBRARY_IMAGE to path), mapOf(path to file))
        val saved = store.imageFile(t.id, path)!!
        assertArrayEquals(png, saved.readBytes())
        assertNull(store.imageFile("system", path))
        assertNull(store.imageFile(t.id, "../x.png"))
        // The original can go: the theme has its own copy.
        file.delete()
        assertTrue(saved.isFile)
        // Removing the image from the theme removes the copy.
        store.save(t.without(ThemeKeys.LIBRARY_IMAGE))
        assertFalse(saved.exists())
    }

    @Test fun duplicateCopiesPictures() {
        val (path, file) = store.stage(ThemeKeys.SIDEBAR_IMAGE, picture(), File(work, "s"))!!
        val t = store.create("Orig", ThemeBase.LIGHT, mapOf(ThemeKeys.SIDEBAR_IMAGE to path, "accent" to "#123456"), mapOf(path to file))
        // A theme naming a picture that is gone duplicates without it.
        File(store.root, "lost").apply { mkdirs() }.resolve("theme.yaml").writeText("base: dark\nlibrary_image: images/x.jpg\n")
        val copy = store.duplicate(t.id, "Orig (copy)")
        assertEquals(t.values, copy.values)
        assertArrayEquals(png, store.imageFile(copy.id, path)!!.readBytes())
        store.delete(t.id)
        assertTrue(store.imageFile(copy.id, path)!!.isFile)
        assertFalse(store.duplicate("lost", "L").values.containsKey(ThemeKeys.LIBRARY_IMAGE))
        assertThrows(NoSuchElementException::class.java) { store.duplicate("nope", "x") }
    }

    @Test fun exportThenImportIsTheSameTheme() {
        val (path, file) = store.stage(ThemeKeys.LIBRARY_IMAGE, picture(), File(work, "s"))!!
        val t = store.create("Midnight", ThemeBase.DARK, mapOf(ThemeKeys.LIBRARY_IMAGE to path, "library_image_dim" to 0.7, "accent" to "#78aeed", "density" to "compact"), mapOf(path to file))
        val out = File(work, "midnight.openemux-theme")
        out.outputStream().use { store.export(t.id, it) }
        val names = ZipFile(out).use { z -> z.entries().asSequence().map { it.name }.toList() }
        assertEquals(listOf("theme.yaml", path), names)
        assertThrows(NoSuchElementException::class.java) { store.export("nope", ByteArrayOutputStream()) }

        val again = store.import(out)
        assertEquals("Midnight (2)", again.name)
        assertEquals(t.values - ThemeKeys.LIBRARY_IMAGE, again.values - ThemeKeys.LIBRARY_IMAGE)
        assertArrayEquals(png, store.imageFile(again.id, again.values[ThemeKeys.LIBRARY_IMAGE] as String)!!.readBytes())
        store.delete(t.id)
        store.delete(again.id)
        assertEquals("Midnight", store.import(out).name)
        assertTrue(store.root.listFiles()!!.none { it.name.startsWith(".import") })
    }

    @Test fun importsADesktopFileInATopFolder() {
        val yaml = "version: 1\nname: Desk\nbase: light\nwindow_bg: '#fafafa'\nsidebar_width: 280\nfont_family: Cantarell\n".toByteArray()
        val t = store.import(zip("d.openemux-theme", "Desk/theme.yaml" to yaml, "Desk/notes.txt" to byteArrayOf(1), "__MACOSX/._x" to byteArrayOf(0)))
        assertEquals("Desk", t.name)
        assertEquals(mapOf("window_bg" to 0xFFFAFAFA.toInt()), t.values)
    }

    @Test fun hostileFilesAreRefusedAndWriteNothing() {
        val yaml = "base: dark\nname: H\n".toByteArray()
        assertEquals(ThemeImportFailure.NOT_THEME, failure(File(work, "x.openemux-theme").apply { writeText("not a zip") }))
        assertEquals(ThemeImportFailure.NOT_THEME, failure(zip("e.zip", "readme.txt" to byteArrayOf(1))))
        assertEquals(ThemeImportFailure.DAMAGED, failure(zip("t.zip", "../theme.yaml" to yaml)))
        assertEquals(ThemeImportFailure.DAMAGED, failure(zip("a.zip", "theme.yaml" to yaml, "/abs/x" to byteArrayOf(1))))
        assertEquals(ThemeImportFailure.DAMAGED, failure(zip("y.zip", "theme.yaml" to "a: 'open".toByteArray())))
        assertEquals(ThemeImportFailure.DAMAGED, failure(zip("b.zip", "theme.yaml" to "base: sepia\n".toByteArray())))
        assertEquals(ThemeImportFailure.IMAGE, failure(zip("m.zip", "theme.yaml" to "base: dark\nlibrary_image: images/a.png\n".toByteArray())))
        assertEquals(ThemeImportFailure.IMAGE, failure(zip("u.zip", "theme.yaml" to "base: dark\nsidebar_image: images/a.png\n".toByteArray(), "images/a.png" to byteArrayOf(1, 2, 3))))
        val many = (0..ThemeStore.MAX_ENTRIES).map { "images/f$it.png" to png }.toTypedArray()
        assertEquals(ThemeImportFailure.TOO_LARGE, failure(zip("n.zip", "theme.yaml" to yaml, *many)))
        // A zip bomb: a small file that inflates past the limit, caught by counting what is actually read.
        assertEquals(ThemeImportFailure.TOO_LARGE, failure(zip("bomb.zip", "theme.yaml" to ByteArray(ThemeYaml.MAX_BYTES + 10) { 'a'.code.toByte() })))
        assertEquals(ThemeImportFailure.TOO_LARGE, failure(zip("big.zip", "theme.yaml" to yaml, "images/a.png" to ByteArray((ThemeStore.MAX_IMAGE_BYTES + 1).toInt()))))
        val huge = File(work, "huge.zip")
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(ThemeStore.MAX_ZIP_BYTES + 1) }
        assertEquals(ThemeImportFailure.TOO_LARGE, failure(huge))
        assertTrue(store.list().isEmpty())
        assertTrue(store.root.listFiles()!!.none { it.name.startsWith(".import") })
    }

    @Test fun aFailedWriteLeavesNoTempFile() {
        val t = store.create("W", ThemeBase.DARK)
        // A folder where the file should go: the rename fails, the temp file goes.
        val target = File(store.root, "w/theme.yaml")
        target.delete()
        target.mkdirs()
        File(target, "child").writeText("x")
        assertThrows(java.io.IOException::class.java) { store.save(t) }
        assertFalse(File(store.root, "w/.theme.yaml.tmp").exists())
    }
}
