package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The player's skins per console (issue 782): listing, rename, duplicate, delete, and the guard on everything else. */
class SkinStoreTest {
    private lateinit var base: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-store").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private fun layout(color: Int? = null, key: Int? = null) = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)), SkinBackground(color),
        elements = key?.let { listOf(SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f), colors = mapOf("button" to it))) }.orEmpty(),
    )

    private fun phoneSkin(name: String) = OpenEmuxSkin(
        Console.SFC, name,
        mapOf(SkinLayoutId.PHONE_PORTRAIT to layout(0xFF112233.toInt(), 0xFFCC0000.toInt()), SkinLayoutId.PHONE_LANDSCAPE to layout()),
    )

    private fun own(id: String, skin: OpenEmuxSkin = phoneSkin(id), console: Console = Console.SFC): File =
        File(store.root(console), id).apply {
            mkdirs()
            File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(skin))
            File(this, "phone-portrait").mkdirs()
            File(this, "phone-portrait/background.png").writeBytes(byteArrayOf(1, 2, 3))
        }

    private fun pkg(name: String, info: String, console: Console = Console.SFC): File =
        File(store.root(console), name).apply {
            parentFile.mkdirs()
            ZipOutputStream(outputStream()).use { z ->
                z.putNextEntry(ZipEntry("__MACOSX/info.json")); z.write("junk".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("skin/info.json")); z.write(info.toByteArray()); z.closeEntry()
            }
        }

    private fun reps(vararg families: String) = families.joinToString(",", "{", "}") { f ->
        """"$f":{"standard":{"portrait":{},"landscape":{}}}"""
    }

    @Test fun nothingThereListsNothing() {
        assertEquals(emptyList<StoredSkin>(), store.list(Console.SFC))
        assertNull(store.legacyPick(Console.SFC))
    }

    @Test fun ownFoldersComeFirstThenPackagesEachByName() {
        own("zeta", phoneSkin("zeta"))
        own("alpha", phoneSkin("Beta"))
        pkg("b.deltaskin", """{"name":"Bravo","representations":${reps("iphone")}}""")
        pkg("a.MANICSKIN", """{"name":"Alpha pack","representations":${reps("ipad")}}""")
        File(store.root(Console.SFC), "notes.txt").writeText("x")
        File(store.root(Console.SFC), ".hidden.tmp").mkdirs()
        val list = store.list(Console.SFC)
        assertEquals(listOf("Beta", "zeta", "Alpha pack", "Bravo"), list.map { it.name })
        assertEquals(listOf(StoredSkinKind.OWN, StoredSkinKind.OWN, StoredSkinKind.IMPORTED, StoredSkinKind.IMPORTED), list.map { it.kind })
        assertEquals("own:alpha", list[0].ref)
        assertEquals("alpha", list[0].id)
        assertEquals("pkg:a.MANICSKIN", list[2].ref)
    }

    @Test fun anOwnSkinsFormsAndColoursComeFromItsLayouts() {
        own("mine")
        val s = store.list(Console.SFC).single()
        assertEquals(setOf(SkinForm.PHONE), s.forms)
        assertEquals(0xFF112233.toInt(), s.body)
        assertEquals(0xFFCC0000.toInt(), s.key)
        assertFalse(s.broken)
    }

    @Test fun anOwnSkinWithNoLayoutsHasNoColours() {
        own("empty", OpenEmuxSkin(Console.SFC, "Empty", emptyMap()))
        val s = store.list(Console.SFC).single()
        assertNull(s.body)
        assertNull(s.key)
    }

    @Test fun anUnreadableFolderIsListedBrokenUnderItsFolderName() {
        File(store.root(Console.SFC), "bad").mkdirs()
        File(store.root(Console.SFC), "bad/skin.json").writeText("{")
        val s = store.list(Console.SFC).single()
        assertTrue(s.broken)
        assertEquals("bad", s.name)
        assertEquals(emptySet<SkinForm>(), s.forms)
    }

    @Test fun anUnreadablePackageIsListedBrokenUnderItsFileName() {
        File(store.root(Console.SFC).apply { mkdirs() }, "junk.deltaskin").writeText("not a zip")
        val s = store.list(Console.SFC).single()
        assertTrue(s.broken)
        assertEquals("junk", s.name)
    }

    @Test fun aPackageWithNoNameIsListedUnderItsFileName() {
        pkg("Cool.deltaskin", """{"representations":${reps("iphone")}}""")
        assertEquals("Cool", store.list(Console.SFC).single().name)
    }

    @Test fun packageFormsFollowItsArt() {
        assertEquals(setOf(SkinForm.PHONE), SkinStore.packageForms(JSONObject("""{"representations":${reps("iphone")}}""")))
        assertEquals(setOf(SkinForm.TABLET), SkinStore.packageForms(JSONObject("""{"representations":${reps("ipad")}}""")))
        assertEquals(SkinForm.entries.toSet(), SkinStore.packageForms(JSONObject("""{"representations":${reps("iphone", "ipad")}}""")))
        // One orientation only is not enough: the overlay needs both.
        assertEquals(emptySet<SkinForm>(), SkinStore.packageForms(JSONObject("""{"representations":{"iphone":{"edgeToEdge":{"portrait":{}}}}}""")))
        assertEquals(setOf(SkinForm.PHONE), SkinStore.packageForms(JSONObject("""{"representations":{"iphone":{"edgeToEdge":{"portrait":{}},"standard":{"landscape":{}}}}}""")))
        assertEquals(emptySet<SkinForm>(), SkinStore.packageForms(JSONObject("{}")))
    }

    @Test fun aPackagesInfoForgivesTrailingCommasAndAByteOrderMark() {
        pkg("t.deltaskin", "\uFEFF{\"name\":\"Trailing\",\"representations\":${reps("iphone")},}")
        val s = store.list(Console.SFC).single()
        assertEquals("Trailing", s.name)
        assertEquals(setOf(SkinForm.PHONE), s.forms)
    }

    @Test fun theLegacyPickIsTheFirstByName() {
        pkg("b.deltaskin", "{}")
        own("c-mine")
        assertEquals("b.deltaskin", store.legacyPick(Console.SFC)?.name)
        own("a-mine")
        assertEquals("a-mine", store.legacyPick(Console.SFC)?.name)
        assertEquals("own:a-mine", SkinStore.refOf(store.legacyPick(Console.SFC)!!))
        assertEquals("pkg:b.deltaskin", SkinStore.refOf(File(store.root(Console.SFC), "b.deltaskin")))
    }

    @Test fun aRefFindsItsSkinOnlyWhileItIsThereAndOfItsKind() {
        own("mine")
        pkg("p.deltaskin", "{}")
        assertEquals("mine", store.file(Console.SFC, "own:mine")?.name)
        assertEquals("p.deltaskin", store.file(Console.SFC, "pkg:p.deltaskin")?.name)
        assertNull(store.file(Console.SFC, null))
        assertNull(store.file(Console.SFC, "own:gone"))
        assertNull(store.file(Console.SFC, "pkg:mine"))
        assertNull(store.file(Console.SFC, "own:p.deltaskin"))
        assertNull(store.file(Console.SFC, "own:../SFC/mine"))
        assertNull(store.file(Console.SFC, "nonsense"))
        assertNull(store.file(Console.SFC, "own:"))
        assertNull(store.file(Console.GBA, "own:mine"))
    }

    @Test fun aRefThatIsALinkIsNotFollowed() {
        val outside = own("elsewhere", console = Console.GBA)
        Files.createSymbolicLink(File(store.root(Console.SFC).apply { mkdirs() }, "link").toPath(), outside.toPath())
        assertNull(store.file(Console.SFC, "own:link"))
        assertEquals(emptyList<StoredSkin>(), store.list(Console.SFC))
    }

    @Test fun renameChangesTheNameAndKeepsTheFolder() {
        val dir = own("mine")
        store.rename(Console.SFC, "mine", "  Night purple  ")
        assertEquals("Night purple", SkinFormat.readFolder(dir).skin.name)
        assertEquals(listOf("mine"), store.root(Console.SFC).list()!!.toList())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(dir, "phone-portrait/background.png").readBytes())
        assertFalse(File(dir, "skin.json.tmp").exists())
    }

    @Test fun aBlankNameIsRefused() {
        val dir = own("mine")
        val before = File(dir, SkinFormat.SKIN_JSON).readText()
        assertThrows(SkinStoreException::class.java) { store.rename(Console.SFC, "mine", "   ") }
        assertEquals(before, File(dir, SkinFormat.SKIN_JSON).readText())
    }

    @Test fun aLongNameIsCut() {
        assertEquals("x".repeat(SkinStore.MAX_NAME), SkinStore.cleanName("x".repeat(100)))
    }

    @Test fun duplicateMakesANamedCopyAndLeavesTheOriginal() {
        val dir = own("mine")
        val original = File(dir, SkinFormat.SKIN_JSON).readBytes()
        val ref = store.duplicate(Console.SFC, "mine", "Mine (copy)")
        assertEquals("own:mine-copy", ref)
        val copy = store.file(Console.SFC, ref)!!
        assertEquals("Mine (copy)", SkinFormat.readFolder(copy).skin.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(copy, "phone-portrait/background.png").readBytes())
        assertArrayEquals(original, File(dir, SkinFormat.SKIN_JSON).readBytes())
        assertEquals(2, store.list(Console.SFC).size)
        assertEquals("own:mine-copy-2", store.duplicate(Console.SFC, "mine", "Mine (copy)"))
    }

    @Test fun aCopyLeavesLinksBehind() {
        val dir = own("mine")
        val secret = File(base, "secret.txt").apply { writeText("s") }
        Files.createSymbolicLink(File(dir, "link.png").toPath(), secret.toPath())
        val copy = store.file(Console.SFC, store.duplicate(Console.SFC, "mine", "Copy"))!!
        assertFalse(File(copy, "link.png").exists())
    }

    @Test fun aNewIdIsMadeOfTheNamesLettersAndNeverEmpty() {
        assertEquals("night-purple", store.freeId(Console.SFC, "Night Purple!"))
        assertEquals("skin", store.freeId(Console.SFC, "★★★"))
        assertEquals(40, store.freeId(Console.SFC, "a".repeat(80)).length)
        File(store.root(Console.SFC), ".skin.tmp").mkdirs()
        assertEquals("skin-2", store.freeId(Console.SFC, "★"))
    }

    @Test fun deleteRemovesTheFolderButNotWhatALinkInsidePointsAt() {
        val dir = own("mine")
        val keep = File(base, "keep").apply { mkdirs(); File(this, "a.png").writeText("a") }
        Files.createSymbolicLink(File(dir, "art").toPath(), keep.toPath())
        store.delete(Console.SFC, "mine")
        assertFalse(dir.exists())
        assertTrue(File(keep, "a.png").isFile)
    }

    @Test fun aBrokenSkinCanStillBeDeleted() {
        File(store.root(Console.SFC), "bad").mkdirs()
        File(store.root(Console.SFC), "bad/skin.json").writeText("{")
        store.delete(Console.SFC, "bad")
        assertEquals(emptyList<StoredSkin>(), store.list(Console.SFC))
    }

    @Test fun nothingButThePlayersOwnFoldersCanBeChanged() {
        own("mine")
        val p = pkg("p.deltaskin", "{}")
        val pBytes = p.readBytes()
        val outside = own("other", console = Console.GBA)
        Files.createSymbolicLink(File(store.root(Console.SFC), "link").toPath(), outside.toPath())
        File(store.root(Console.SFC), "plain").mkdirs()
        val refused = listOf("p.deltaskin", "../GBA/other", "..", ".", "", ".hidden", "a/b", "a\\b", "link", "plain", "gone")
        for (id in refused) {
            assertThrows(id, SkinStoreException::class.java) { store.rename(Console.SFC, id, "X") }
            assertThrows(id, SkinStoreException::class.java) { store.duplicate(Console.SFC, id, "X") }
            assertThrows(id, SkinStoreException::class.java) { store.delete(Console.SFC, id) }
        }
        assertArrayEquals(pBytes, p.readBytes())
        assertEquals("other", SkinFormat.readFolder(outside).skin.name)
    }

    @Test fun aConsoleFolderThatIsALinkOutOfTheSkinsIsRefused() {
        val bundledCopy = Files.createTempDirectory("cache-skins").toFile()
        try {
            val dir = File(bundledCopy, "mine").apply { mkdirs(); File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(phoneSkin("Bundled"))) }
            File(base, "skins").mkdirs()
            Files.createSymbolicLink(store.root(Console.SFC).toPath(), bundledCopy.toPath())
            assertThrows(SkinStoreException::class.java) { store.rename(Console.SFC, "mine", "X") }
            assertThrows(SkinStoreException::class.java) { store.delete(Console.SFC, "mine") }
            assertEquals("Bundled", SkinFormat.readFolder(dir).skin.name)
        } finally {
            bundledCopy.deleteRecursively()
        }
    }

    @Test fun refsRoundTrip() {
        assertEquals(StoredSkinKind.OWN to "a", SkinStore.parse(SkinStore.ref(StoredSkinKind.OWN, "a")))
        assertEquals(StoredSkinKind.IMPORTED to "b.deltaskin", SkinStore.parse(SkinStore.ref(StoredSkinKind.IMPORTED, "b.deltaskin")))
        assertNull(SkinStore.parse(null))
        assertNull(SkinStore.parse("pkg:"))
    }

    @Test fun aRenameKeepsTheKeysThisBuildDoesNotRead() {
        val dir = own("mine")
        val json = File(dir, SkinFormat.SKIN_JSON)
        json.writeText(org.json.JSONObject(json.readText()).put("author", "someone").toString())
        store.rename(Console.SFC, "mine", "Renamed")
        assertEquals("someone", org.json.JSONObject(json.readText()).getString("author"))
        val copy = store.file(Console.SFC, store.duplicate(Console.SFC, "mine", "Copy"))!!
        val copied = org.json.JSONObject(File(copy, SkinFormat.SKIN_JSON).readText())
        assertEquals("someone", copied.getString("author"))
        assertEquals("Copy", SkinFormat.readFolder(copy).skin.name)
    }

    @Test fun theLegacyPickSkipsHiddenAndLinkedEntries() {
        own("b-mine")
        File(store.root(Console.SFC), "._a.deltaskin").writeText("apple double")
        Files.createSymbolicLink(File(store.root(Console.SFC), "a-link.deltaskin").toPath(), File(base, "elsewhere").apply { writeText("x") }.toPath())
        assertEquals("b-mine", store.legacyPick(Console.SFC)?.name)
    }
}
