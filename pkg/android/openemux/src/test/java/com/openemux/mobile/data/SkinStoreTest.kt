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

    /** A source skin outside the store: [skin]'s skin.json plus one image, in a folder. */
    private fun sourceFolder(skin: OpenEmuxSkin = phoneSkin("Bundled")): File =
        File(base, "source-${skin.name}").apply {
            mkdirs()
            File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(skin))
            File(this, "phone-portrait").mkdirs()
            File(this, "phone-portrait/background.png").writeBytes(byteArrayOf(1, 2, 3))
        }

    /** [dir]'s files zipped, at the zip's root or under [folder]. */
    private fun zipOf(dir: File, folder: String = ""): File = File(base, "${dir.name}.zip").apply {
        ZipOutputStream(outputStream()).use { z ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(folder + f.relativeTo(dir).invariantSeparatorsPath)); z.write(f.readBytes()); z.closeEntry()
            }
        }
    }

    private fun filesOf(dir: File) = dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes().toList() }

    private fun assertCopyOf(source: File, ref: String, name: String) {
        val copy = store.file(Console.SFC, ref)!!
        val read = SkinFormat.readFolder(copy).skin
        assertEquals(name, read.name)
        assertEquals(SkinFormat.readFolder(source).skin.copy(name = name), read)
        assertEquals(filesOf(source).keys, filesOf(copy).keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(copy, "phone-portrait/background.png").readBytes())
        assertEquals(listOf(name), store.list(Console.SFC).map { it.name })
    }

    @Test fun createMakesAnOwnSkinFromAZip() {
        val source = sourceFolder()
        val zip = zipOf(source)
        val before = zip.readBytes()
        val ref = store.create(Console.SFC, zip, "  Mine  ")
        assertEquals(StoredSkinKind.OWN to "mine", SkinStore.parse(ref))
        assertCopyOf(source, ref, "Mine")
        assertArrayEquals("the source is only read", before, zip.readBytes())
    }

    @Test fun createReadsAZipWhoseFilesAreInOneFolder() {
        val source = sourceFolder()
        assertCopyOf(source, store.create(Console.SFC, zipOf(source, "skin/"), "Mine"), "Mine")
    }

    @Test fun createMakesAnOwnSkinFromAFolderAndLeavesLinksBehind() {
        val source = sourceFolder()
        Files.createSymbolicLink(File(source, "link.png").toPath(), File(base, "outside.png").apply { writeText("x") }.toPath())
        val ref = store.create(Console.SFC, source, "Mine")
        val copy = store.file(Console.SFC, ref)!!
        assertFalse(File(copy, "link.png").exists())
        assertEquals("Bundled", SkinFormat.readFolder(source).skin.name)
        assertEquals("Mine", SkinFormat.readFolder(copy).skin.name)
    }

    @Test fun createKeepsTheKeysThisBuildDoesNotReadAndMakesTheIdUnique() {
        val source = sourceFolder()
        val json = File(source, SkinFormat.SKIN_JSON)
        json.writeText(JSONObject(json.readText()).put("author", "someone").toString())
        val first = store.create(Console.SFC, zipOf(source), "Mine")
        val second = store.create(Console.SFC, source, "Mine")
        assertEquals("own:mine", first)
        assertEquals("own:mine-2", second)
        assertEquals("someone", JSONObject(File(store.file(Console.SFC, second), SkinFormat.SKIN_JSON).readText()).getString("author"))
        assertEquals(listOf("Mine", "Mine"), store.list(Console.SFC).map { it.name })
    }

    @Test fun createRefusesAnotherConsolesSkinABlankNameAndAnUnreadableSourceLeavingNothing() {
        val zip = zipOf(sourceFolder())
        assertThrows(SkinStoreException::class.java) { store.create(Console.MD, zip, "Mine") }
        assertThrows(SkinStoreException::class.java) { store.create(Console.SFC, zip, "   ") }
        val empty = File(base, "empty").apply { mkdirs() }
        assertThrows(SkinFormatException::class.java) { store.create(Console.SFC, empty, "Mine") }
        val notZip = File(base, "not.zip").apply { writeText("nope") }
        assertThrows(java.io.IOException::class.java) { store.create(Console.SFC, notZip, "Mine") }
        assertTrue(store.root(Console.SFC).list().orEmpty().isEmpty())
        assertTrue(store.root(Console.MD).list().orEmpty().isEmpty())
    }

    /** A source that reads like [inner] but fails on [failing], as a copy cut short does. */
    private class FailingSource(private val inner: SkinSource, private val failing: String) : SkinSource {
        override val files get() = inner.files
        override fun open(path: String) = if (path == failing) throw java.io.IOException("cut short") else inner.open(path)
    }

    @Test fun aCreateCutShortLeavesNoHalfMadeSkin() {
        val source = FailingSource(FolderSkinSource(sourceFolder()), "phone-portrait/background.png")
        assertThrows(java.io.IOException::class.java) { store.create(Console.SFC, source, "Mine") }
        assertTrue(store.root(Console.SFC).list().orEmpty().isEmpty())
        assertTrue(store.list(Console.SFC).isEmpty())
    }

    /** A source whose file names climb out of the skin, which [ZipSkinSource] would have filtered. */
    private class ClimbingSource(private val inner: SkinSource) : SkinSource {
        override val files get() = inner.files + "../escaped.png"
        override fun open(path: String) = inner.open(path) ?: "x".byteInputStream()
    }

    @Test fun aFileThatWouldLandOutsideTheNewSkinIsRefused() {
        val source = ClimbingSource(FolderSkinSource(sourceFolder()))
        assertThrows(SkinStoreException::class.java) { store.create(Console.SFC, source, "Mine") }
        assertFalse(File(store.root(Console.SFC), "escaped.png").exists())
        assertTrue(store.root(Console.SFC).list().orEmpty().isEmpty())
    }

    @Test fun aCreateThatCannotBeRenamedIntoPlaceLeavesNothing() {
        val source = sourceFolder()
        val root = store.root(Console.SFC).apply { mkdirs() }
        // A read-only console folder: the hidden folder cannot even be made, nor anything renamed into place.
        root.setWritable(false)
        try {
            if (root.canWrite()) return // running as root: nothing can be refused this way
            assertThrows(java.io.IOException::class.java) { store.create(Console.SFC, source, "Mine") }
        } finally {
            root.setWritable(true)
        }
        assertTrue(root.list().orEmpty().isEmpty())
    }

    @Test fun createConvertedWritesTheImagesThenTheSkinNamedAndReadsItBack() {
        val ref = store.createConverted(Console.SFC, "  Converted  ") { dir ->
            File(dir, "phone-portrait").mkdirs()
            File(dir, "phone-portrait/a.png").writeBytes(byteArrayOf(9))
            phoneSkin("Ignored")
        }
        assertEquals("own:converted", ref)
        val dir = store.file(Console.SFC, ref)!!
        val read = SkinFormat.readFolder(dir).skin
        assertEquals(phoneSkin("Converted"), read)
        assertArrayEquals(byteArrayOf(9), File(dir, "phone-portrait/a.png").readBytes())
        assertEquals(listOf("converted"), store.root(Console.SFC).list()!!.toList())
    }

    @Test fun createConvertedLeavesNothingWhenItFailsAnywhere() {
        // The conversion throws, makes another console's skin, or a skin the reader refuses.
        assertThrows(IllegalStateException::class.java) { store.createConverted(Console.SFC, "A") { error("cut short") } }
        assertThrows(SkinStoreException::class.java) { store.createConverted(Console.SFC, "B") { phoneSkin("B").copy(console = Console.GBA) } }
        val bad = phoneSkin("C").let { s -> s.copy(layouts = s.layouts.mapValues { (_, l) -> l.copy(screens = emptyList()) }) }
        assertThrows(SkinFormatException::class.java) { store.createConverted(Console.SFC, "C") { bad } }
        assertThrows(SkinStoreException::class.java) { store.createConverted(Console.SFC, "  ") { phoneSkin("D") } }
        assertTrue(store.root(Console.SFC).list().orEmpty().isEmpty())
    }

    @Test fun anImportedPackageIsOnlyReachedWhenItIsOne() {
        val root = store.root(Console.SFC).apply { mkdirs() }
        val file = File(root, "Mine.manicskin").apply { writeText("x") }
        assertEquals(file, store.importedPackage(Console.SFC, "Mine.manicskin"))
        own("mine")
        File(root, "dir.deltaskin").mkdirs()
        for (id in listOf("mine", "dir.deltaskin", "none.deltaskin", "..", "a/b.deltaskin", ".x.deltaskin")) {
            assertThrows(id, SkinStoreException::class.java) { store.importedPackage(Console.SFC, id) }
        }
        Files.createSymbolicLink(File(root, "link.deltaskin").toPath(), file.toPath())
        assertThrows(SkinStoreException::class.java) { store.importedPackage(Console.SFC, "link.deltaskin") }
        assertTrue(SkinStore.isPackageName("A.DeltaSkin"))
        assertFalse(SkinStore.isPackageName("a.zip"))
    }
}
