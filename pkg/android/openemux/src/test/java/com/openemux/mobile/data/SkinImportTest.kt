package com.openemux.mobile.data

import com.openemux.mobile.data.SkinTemplateTest.Companion.png
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** "Import .zip" (issue 787): what a picked file is, and how the store writes it, all or nothing. */
class SkinImportTest {
    private lateinit var base: File
    private lateinit var work: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-import").toFile()
        work = Files.createTempDirectory("skin-import-zips").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
        work.deleteRecursively()
    }

    private fun layout(color: Int = 0xFF112233.toInt()) = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)), SkinBackground(color),
        frame = SkinFrame(SkinRect(0f, 0f, 100f, 70f)),
        elements = listOf(
            SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f), colors = mapOf("button" to 0xFFCC0000.toInt())),
            SkinElement("b", SkinElementType.BUTTON, "b", SkinRect(40f, 100f, 20f, 20f)),
        ),
    )

    private fun skin(name: String = "Night", console: Console = Console.SFC, ids: List<SkinLayoutId> = listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE, SkinLayoutId.TABLET_PORTRAIT)) =
        OpenEmuxSkin(console, name, ids.associateWith { layout() })

    /** A zip of [files] (name to bytes), under [root] when it is not empty. */
    private fun zip(name: String, files: Map<String, ByteArray>, root: String = "night/"): File = File(work, name).apply {
        ZipOutputStream(outputStream()).use { z ->
            files.forEach { (path, bytes) -> z.putNextEntry(ZipEntry(root + path)); z.write(bytes); z.closeEntry() }
        }
    }

    private fun skinZip(skin: OpenEmuxSkin = skin(), images: Map<String, ByteArray> = mapOf("phone-portrait/a.png" to IMG_A), name: String = "t.zip", extra: Map<String, ByteArray> = emptyMap()) =
        zip(name, mapOf(SkinFormat.SKIN_JSON to SkinFormat.toJson(skin).toByteArray(), SkinTemplate.README to "hi".toByteArray()) + images + extra)

    private fun own(id: String, skin: OpenEmuxSkin = skin(id), images: Map<String, ByteArray> = emptyMap()): File =
        File(store.root(Console.SFC), id).apply {
            mkdirs()
            File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(skin))
            images.forEach { (path, bytes) -> File(this, path).apply { parentFile.mkdirs() }.writeBytes(bytes) }
        }

    private fun info(id: String? = "com.rileytestut.delta.game.snes", name: String? = "Clear Blue") =
        JSONObject().apply {
            id?.let { put("gameTypeIdentifier", it) }
            name?.let { put("name", it) }
            put("representations", JSONObject("""{"iphone":{"standard":{"portrait":{},"landscape":{}}}}"""))
        }.toString().toByteArray()

    private fun leftovers(console: Console = Console.SFC) = store.root(console).list().orEmpty().filter { it.startsWith(".") }

    // --- inspect -------------------------------------------------------------------------------------------------

    @Test fun aSkinForThisConsoleIsReadyWithTheFormsOfTheFoldersItHas() {
        val check = SkinImport.inspect(skinZip(), Console.SFC, "night.zip") as ImportCheck.Own
        assertEquals("Night", check.skin.name)
        assertEquals(setOf(SkinForm.PHONE), check.forms)
    }

    @Test fun aSkinWithNoLayoutFolderAtAllKeepsEveryForm() {
        val check = SkinImport.inspect(skinZip(images = emptyMap()), Console.SFC, null) as ImportCheck.Own
        assertEquals(setOf(SkinForm.PHONE, SkinForm.TABLET), check.forms)
    }

    @Test fun aSkinForAnotherConsoleIsRefusedNamingIt() {
        assertEquals(ImportCheck.Refused(ImportRefusal.OTHER_CONSOLE, Console.GBA), SkinImport.inspect(skinZip(skin(console = Console.GBA)), Console.SFC, null))
    }

    @Test fun aBrokenSkinJsonIsRefusedWithItsError() {
        val bad = zip("bad.zip", mapOf(SkinFormat.SKIN_JSON to "{\"format\": 9}".toByteArray()))
        assertEquals(ImportCheck.Refused(ImportRefusal.BROKEN, detail = "UNSUPPORTED_FORMAT"), SkinImport.inspect(bad, Console.SFC, null))
    }

    @Test fun aFileThatIsNotAZipOrHasNoSkinIsNotASkin() {
        val text = File(work, "a.txt").apply { writeText("hello") }
        assertEquals(ImportCheck.Refused(ImportRefusal.NOT_A_SKIN), SkinImport.inspect(text, Console.SFC, "a.txt"))
        val other = zip("o.zip", mapOf("photo.png" to IMG_A, "__MACOSX/info.json" to "{}".toByteArray()), root = "")
        assertEquals(ImportCheck.Refused(ImportRefusal.NOT_A_SKIN), SkinImport.inspect(other, Console.SFC, "o.zip"))
    }

    @Test fun anUnsafeEntryRefusesTheWholeZip() {
        listOf("../evil.png", "/abs.png", "a\\b.png", "x/../../y").forEach { bad ->
            val z = zip("u.zip", mapOf(SkinFormat.SKIN_JSON to SkinFormat.toJson(skin()).toByteArray(), bad to IMG_A), root = "")
            assertEquals(bad, ImportCheck.Refused(ImportRefusal.UNSAFE), SkinImport.inspect(z, Console.SFC, null))
        }
        assertFalse(SkinImport.unsafe("__MACOSX/night/._a.png"))
        assertFalse(SkinImport.unsafe("night/phone-portrait/a..b.png"))
    }

    @Test fun tooManyEntriesOrTooManyBytesAreTooLarge() {
        val many = zip("many.zip", (0..SkinImport.MAX_ENTRIES).associate { "f$it" to ByteArray(0) })
        assertEquals(ImportCheck.Refused(ImportRefusal.TOO_LARGE), SkinImport.inspect(many, Console.SFC, null))
        val big = File(work, "big.zip").apply { RandomAccessFile(this, "rw").use { it.setLength(SkinImport.MAX_BYTES + 1) } }
        assertEquals(ImportCheck.Refused(ImportRefusal.TOO_LARGE), SkinImport.inspect(big, Console.SFC, null))
        assertThrows(SkinImportTooLarge::class.java) { SkinImport.guard(big) }
        assertThrows(SkinImportTooLarge::class.java) { SkinImport.guard(many) }
        assertThrows(SkinStoreException::class.java) { SkinImport.guard(skinZip(extra = mapOf("../x" to IMG_A), name = "g.zip")) }
        SkinImport.guard(skinZip())
    }

    @Test fun aDeltaPackageForThisConsoleIsReadyAsAPackage() {
        val z = zip("p.zip", mapOf("info.json" to info()), root = "")
        assertEquals(ImportCheck.Package("Clear Blue", setOf(SkinForm.PHONE), SkinImport.DELTA), SkinImport.inspect(z, Console.SFC, "clear.deltaskin"))
    }

    @Test fun aDeltaPackageForAnotherConsoleIsRefused() {
        val z = zip("p.zip", mapOf("info.json" to info("com.rileytestut.delta.game.gba")), root = "")
        assertEquals(ImportCheck.Refused(ImportRefusal.OTHER_CONSOLE, Console.GBA), SkinImport.inspect(z, Console.SFC, null))
        val gbc = zip("q.zip", mapOf("info.json" to info("com.rileytestut.delta.game.gbc")), root = "")
        assertTrue(SkinImport.inspect(gbc, Console.GB, null) is ImportCheck.Package)
    }

    @Test fun aPackageOfAnUnknownGameGoesToTheOpenConsoleNamedByItsFile() {
        val z = zip("p.zip", mapOf("skin/info.json" to info("com.manic.whatever", name = null)), root = "")
        assertEquals(ImportCheck.Package("My Pack", setOf(SkinForm.PHONE), SkinImport.MANIC), SkinImport.inspect(z, Console.SFC, "content/My Pack.manicskin"))
        val noName = zip("n.zip", mapOf("info.json" to info(null, name = " ")), root = "")
        assertEquals(ImportCheck.Package("Skin", setOf(SkinForm.PHONE), SkinImport.DELTA), SkinImport.inspect(noName, Console.SFC, null))
    }

    @Test fun anUnreadableInfoJsonIsBroken() {
        val z = zip("p.zip", mapOf("info.json" to "not json".toByteArray()), root = "")
        assertEquals(ImportCheck.Refused(ImportRefusal.BROKEN, detail = "info.json"), SkinImport.inspect(z, Console.SFC, null))
    }

    @Test fun deltaGameTypesMapToConsoles() {
        assertEquals(setOf(Console.FC), SkinImport.deltaConsoles(JSONObject("""{"gameTypeIdentifier":"com.rileytestut.delta.game.nes"}""")))
        assertEquals(setOf(Console.MD), SkinImport.deltaConsoles(JSONObject("""{"gameTypeIdentifier":"com.rileytestut.delta.game.genesis"}""")))
        assertEquals(emptySet<Console>(), SkinImport.deltaConsoles(JSONObject("""{"gameTypeIdentifier":"com.rileytestut.delta.game.psx"}""")))
        assertEquals(emptySet<Console>(), SkinImport.deltaConsoles(JSONObject("{}")))
    }

    @Test fun aLimitedCopyStopsOneByteOver() {
        val out = ByteArrayOutputStream()
        assertEquals(5L, SkinImport.copyLimited(ByteArray(5).inputStream(), out, 5))
        assertThrows(SkinImportTooLarge::class.java) { SkinImport.copyLimited(ByteArray(6).inputStream(), ByteArrayOutputStream(), 5) }
    }

    // --- importOwn: a new skin -----------------------------------------------------------------------------------

    @Test fun importingAsNewWritesTheNameAndOnlyTheFilesTheSkinNames() {
        val z = skinZip(
            images = mapOf("phone-portrait/a.png" to IMG_A, "phone-landscape/background.png" to IMG_B),
            extra = mapOf("phone-portrait/notes.png" to IMG_B, "sources/phone-portrait.svg" to IMG_B),
        )
        val ref = store.importOwn(Console.SFC, z, "  Repainted  ")
        assertEquals("own:repainted", ref)
        val dir = File(store.root(Console.SFC), "repainted")
        val read = SkinFormat.readFolder(dir).skin
        assertEquals("Repainted", read.name)
        // tablet-portrait had no folder in the zip: the skin no longer offers tablets.
        assertEquals(listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE), read.layouts.keys.toList())
        assertEquals(listOf("phone"), JSONObject(File(dir, SkinFormat.SKIN_JSON).readText()).getJSONArray("forms").toList())
        assertArrayEquals(IMG_A, File(dir, "phone-portrait/a.png").readBytes())
        assertArrayEquals(IMG_B, File(dir, "phone-landscape/background.png").readBytes())
        assertEquals(setOf("skin.json", "phone-portrait/a.png", "phone-landscape/background.png"), files(dir))
        assertEquals(listOf("Repainted"), store.list(Console.SFC).map { it.name })
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test fun importingAsNewWithNoNameKeepsTheZipsNameAndAFreeId() {
        own("night")
        assertEquals("own:night-2", store.importOwn(Console.SFC, skinZip(), null))
        assertThrows(SkinStoreException::class.java) { store.importOwn(Console.SFC, skinZip(name = "b.zip"), "   ") }
    }

    @Test fun aSkinForAnotherConsoleIsNotImported() {
        assertThrows(SkinStoreException::class.java) { store.importOwn(Console.SFC, skinZip(skin(console = Console.GBA)), "x") }
        assertEquals(emptyList<String>(), store.root(Console.SFC).list().orEmpty().toList())
    }

    @Test fun anImageOverTheLimitLeavesNothingBehind() {
        val huge = zip("huge.zip", mapOf(SkinFormat.SKIN_JSON to SkinFormat.toJson(skin()).toByteArray(), "phone-portrait/a.png" to ByteArray((SkinImport.MAX_IMAGE + 1).toInt())))
        assertThrows(SkinImportTooLarge::class.java) { store.importOwn(Console.SFC, huge, "Huge") }
        assertEquals(emptyList<String>(), store.root(Console.SFC).list().orEmpty().toList())
    }

    @Test fun aBrokenZipIsNotImported() {
        assertThrows(SkinFormatException::class.java) { store.importOwn(Console.SFC, zip("bad.zip", mapOf(SkinFormat.SKIN_JSON to "{".toByteArray())), "x") }
        assertEquals(emptyList<String>(), store.root(Console.SFC).list().orEmpty().toList())
    }

    // --- importOwn: replacing --------------------------------------------------------------------------------------

    @Test fun replacingKeepsTheIdAndNameTakesTheZipAndFallsBackToTheOldImages() {
        val old = own(
            "mine", skin("My skin"),
            mapOf("phone-portrait/a.png" to IMG_B, "phone-portrait/b.png" to IMG_B, "phone-landscape/frame.png" to IMG_B, "phone-portrait/stale.png" to IMG_B),
        )
        val moved = skin("Zip name", ids = listOf(SkinLayoutId.PHONE_PORTRAIT, SkinLayoutId.PHONE_LANDSCAPE)).let { s ->
            s.copy(layouts = s.layouts.mapValues { (_, l) -> l.copy(elements = l.elements.map { it.copy(rect = it.rect.copy(x = it.rect.x + 5f)) }) })
        }
        val z = skinZip(moved, images = mapOf("phone-portrait/a.png" to IMG_A, "phone-landscape/background.png" to IMG_A))
        assertEquals("own:mine", store.importOwn(Console.SFC, z, "ignored", replace = "mine"))
        val read = SkinFormat.readFolder(old).skin
        assertEquals("My skin", read.name)
        assertEquals(15f, read.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).elements[0].rect.x)
        assertArrayEquals(IMG_A, File(old, "phone-portrait/a.png").readBytes())
        assertArrayEquals(IMG_B, File(old, "phone-portrait/b.png").readBytes())
        assertArrayEquals(IMG_B, File(old, "phone-landscape/frame.png").readBytes())
        assertFalse(File(old, "phone-portrait/stale.png").exists())
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test fun replacingABrokenSkinTakesTheZipsName() {
        val dir = own("mine")
        File(dir, SkinFormat.SKIN_JSON).writeText("{")
        store.importOwn(Console.SFC, skinZip(), null, replace = "mine")
        assertEquals("Night", SkinFormat.readFolder(dir).skin.name)
    }

    @Test fun onlyThePlayersOwnSkinsCanBeReplaced() {
        File(store.root(Console.SFC), "pack.deltaskin").apply { parentFile.mkdirs() }.writeBytes(IMG_A)
        listOf("pack.deltaskin", "../x", "missing", ".hidden").forEach { id ->
            assertThrows(id, SkinStoreException::class.java) { store.importOwn(Console.SFC, skinZip(name = "$id.zip".replace('/', '_')), null, replace = id) }
        }
    }

    @Test fun aFailedReplaceLeavesTheOldSkinAsItWas() {
        val old = own("mine", skin("My skin"), mapOf("phone-portrait/a.png" to IMG_B))
        val before = File(old, SkinFormat.SKIN_JSON).readText()
        val huge = zip("huge.zip", mapOf(SkinFormat.SKIN_JSON to SkinFormat.toJson(skin()).toByteArray(), "phone-portrait/a.png" to ByteArray((SkinImport.MAX_IMAGE + 1).toInt())))
        assertThrows(SkinImportTooLarge::class.java) { store.importOwn(Console.SFC, huge, null, replace = "mine") }
        assertEquals(before, File(old, SkinFormat.SKIN_JSON).readText())
        assertArrayEquals(IMG_B, File(old, "phone-portrait/a.png").readBytes())
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test fun aTemplateOfASkinImportsBackAsTheSameSkin() {
        val original = skin("Round trip")
        own("trip", original, mapOf("phone-portrait/a.png" to png(20, 20)))
        val (pkg, json) = store.template(Console.SFC, "trip")
        val zip = File(work, "trip.zip")
        zip.outputStream().use { out ->
            SkinTemplate.write(out, pkg.skin, json) { id, image -> pkg.image(id, image.path)?.let { File(store.root(Console.SFC), "trip/$it").readBytes() } ?: png(image.width, image.height) }
        }
        assertTrue(SkinImport.inspect(zip, Console.SFC, "trip.zip") is ImportCheck.Own)
        store.importOwn(Console.SFC, zip, null, replace = "trip")
        assertEquals(original, SkinFormat.readFolder(File(store.root(Console.SFC), "trip")).skin)
        assertThrows(SkinStoreException::class.java) { store.template(Console.SFC, "nope") }
    }

    @Test fun aKeypadPagesImagesAreImported() {
        val page = SkinKeypadPage(
            listOf(SkinElement("key-1", SkinElementType.KEYPAD, "keypad1", SkinRect(10f, 100f, 20f, 20f))),
            background = SkinBackground(0xFF000000.toInt()),
        )
        val skin = OpenEmuxSkin(Console.SFC, "Keys", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout().copy(keypad = page)))
        val zip = skinZip(skin, mapOf("phone-portrait/a.png" to IMG_A, "phone-portrait/keypad/key-1.png" to IMG_B, "phone-portrait/keypad/background.png" to IMG_A))
        val dir = File(store.root(Console.SFC), store.importOwn(Console.SFC, zip, null).substringAfter(':'))
        assertArrayEquals(IMG_B, File(dir, "phone-portrait/keypad/key-1.png").readBytes())
        assertArrayEquals(IMG_A, File(dir, "phone-portrait/keypad/background.png").readBytes())
        assertEquals(page, SkinFormat.readFolder(dir).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT).keypad)
    }

    // --- importPackage -------------------------------------------------------------------------------------------

    @Test fun aPackageLandsUnderImportedWithAFreeName() {
        val z = zip("p.zip", mapOf("info.json" to info()), root = "")
        assertEquals("pkg:clear-blue.deltaskin", store.importPackage(Console.SFC, z, "Clear Blue", SkinImport.DELTA))
        assertEquals("pkg:clear-blue-2.deltaskin", store.importPackage(Console.SFC, z, "Clear Blue", SkinImport.DELTA))
        assertEquals("pkg:skin.manicskin", store.importPackage(Console.SFC, z, "★", SkinImport.MANIC))
        val listed = store.list(Console.SFC)
        assertEquals(listOf(StoredSkinKind.IMPORTED), listed.map { it.kind }.distinct())
        assertEquals(3, listed.size)
        assertArrayEquals(z.readBytes(), File(store.root(Console.SFC), "clear-blue.deltaskin").readBytes())
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test fun onlyPackageExtensionsAndSafeZipsAreTaken() {
        val z = zip("p.zip", mapOf("info.json" to info()), root = "")
        assertThrows(SkinStoreException::class.java) { store.importPackage(Console.SFC, z, "x", "zip") }
        val unsafe = zip("u.zip", mapOf("info.json" to info(), "../x" to IMG_A), root = "")
        assertThrows(SkinStoreException::class.java) { store.importPackage(Console.SFC, unsafe, "x", SkinImport.DELTA) }
        assertEquals(emptyList<String>(), store.root(Console.SFC).list().orEmpty().toList())
    }

    private fun files(dir: File) = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    private fun org.json.JSONArray.toList() = (0 until length()).map { get(it) }

    companion object {
        private val IMG_A = byteArrayOf(1, 2, 3)
        private val IMG_B = byteArrayOf(9, 9)
    }
}
