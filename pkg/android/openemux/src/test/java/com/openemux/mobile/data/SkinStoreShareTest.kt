package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/** What Share nearby asks of the skin store: a skin written as a plain zip, read back whole, under a name that is free. */
class SkinStoreShareTest {
    private lateinit var base: File
    private lateinit var work: File
    private lateinit var store: SkinStore

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-share").toFile()
        work = Files.createTempDirectory("skin-share-zips").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
        work.deleteRecursively()
    }

    private fun layout(keypad: Boolean = false) = SkinLayout(
        SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)), SkinBackground(0xFF112233.toInt()),
        frame = SkinFrame(SkinRect(0f, 0f, 100f, 70f)),
        elements = listOf(
            SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f), colors = mapOf("button" to 0xFFCC0000.toInt())),
            SkinElement("pad", SkinElementType.DPAD, "dpad", SkinRect(40f, 100f, 40f, 40f)),
        ) + if (keypad) listOf(SkinElement("keys", SkinElementType.BUTTON, SkinFormat.KEYPAD_TOGGLE, SkinRect(70f, 150f, 20f, 20f))) else emptyList(),
        keypad = if (keypad) {
            SkinKeypadPage(
                listOf(
                    SkinElement("k1", SkinElementType.KEYPAD, "keypad1", SkinRect(10f, 100f, 20f, 20f)),
                    SkinElement("controls", SkinElementType.BUTTON, SkinFormat.KEYPAD_TOGGLE, SkinRect(40f, 100f, 20f, 20f)),
                ),
            )
        } else {
            null
        },
    )

    private fun skin(name: String = "Night", console: Console = Console.SFC) = OpenEmuxSkin(
        console, name,
        mapOf(SkinLayoutId.PHONE_PORTRAIT to layout(), SkinLayoutId.PHONE_LANDSCAPE to layout(), SkinLayoutId.TABLET_PORTRAIT to layout()),
    )

    private fun own(id: String, skin: OpenEmuxSkin = skin(), files: Map<String, ByteArray> = emptyMap(), json: String = SkinFormat.toJson(skin)): File =
        File(store.root(skin.console), id).apply {
            mkdirs()
            File(this, SkinFormat.SKIN_JSON).writeText(json)
            files.forEach { (path, bytes) -> File(this, path).apply { parentFile.mkdirs() }.writeBytes(bytes) }
        }

    private fun export(id: String, console: Console = Console.SFC): File =
        File(work, "$id.zip").apply { outputStream().use { store.export(console, id, it) } }

    private fun entries(zip: File): Map<String, ByteArray> = ZipFile(zip).use { z ->
        z.entries().asSequence().associate { it.name to z.getInputStream(it).readBytes() }
    }

    private fun files(dir: File) = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    // --- export ---------------------------------------------------------------------------------------------------

    @Test fun theZipHoldsTheSkinsOwnJsonAndTheImagesItsLayoutsHave() {
        // A key this build does not model must survive: the file goes as it is, not as the model writes it.
        val json = JSONObject(SkinFormat.toJson(skin())).put("author", "someone").toString(2)
        own(
            "night", json = json,
            files = mapOf(
                "phone-portrait/a.png" to A, "phone-portrait/pad.png" to B, "phone-portrait/pad-ball.png" to A,
                "phone-landscape/background.webp" to B, "phone-landscape/frame.png" to A,
                // Nothing the format names: left behind.
                "README.txt" to B, "phone-portrait/notes.png" to B, "sources/art.svg" to B, "fold-portrait/a.png" to B,
            ),
        )
        File(store.root(Console.SFC), ".night.draft.json").writeText("{}")
        val got = entries(export("night"))
        assertEquals(
            setOf(
                "skin.json", "phone-portrait/a.png", "phone-portrait/pad.png", "phone-portrait/pad-ball.png",
                "phone-landscape/background.webp", "phone-landscape/frame.png",
            ),
            got.keys,
        )
        assertEquals(json, String(got.getValue("skin.json")))
        assertArrayEquals(B, got.getValue("phone-landscape/background.webp"))
        assertArrayEquals(A, got.getValue("phone-portrait/pad-ball.png"))
    }

    @Test fun aKeypadPagesImagesGoToo() {
        val keyed = OpenEmuxSkin(Console.CV, "Keys", mapOf(SkinLayoutId.PHONE_PORTRAIT to layout(keypad = true)))
        own("keys", keyed, files = mapOf("phone-portrait/keypad/k1.png" to A, "phone-portrait/keys.png" to B))
        assertEquals(setOf("skin.json", "phone-portrait/keypad/k1.png", "phone-portrait/keys.png"), entries(export("keys", Console.CV)).keys)
    }

    @Test fun aSkinDrawnFromItsColoursAloneIsItsJson() {
        own("plain")
        assertEquals(setOf("skin.json"), entries(export("plain")).keys)
    }

    @Test fun nothingBehindALinkLeavesTheDevice() {
        val outside = Files.createTempDirectory("skin-share-outside").toFile()
        try {
            File(outside, "a.png").writeBytes(B)
            File(outside, "background.png").writeBytes(B)
            val dir = own("night", files = mapOf("phone-portrait/background.png" to A))
            Files.createSymbolicLink(File(dir, "phone-portrait/a.png").toPath(), File(outside, "a.png").toPath())
            Files.createSymbolicLink(File(dir, "phone-landscape").toPath(), outside.toPath())
            assertEquals(setOf("skin.json", "phone-portrait/background.png"), entries(export("night")).keys)

            val linked = File(store.root(Console.SFC), "linked").apply { mkdirs() }
            Files.createSymbolicLink(File(linked, "skin.json").toPath(), File(dir, "skin.json").toPath())
            assertThrows(SkinStoreException::class.java) { store.export(Console.SFC, "linked", ByteArrayOutputStream()) }
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test fun onlyAReadableSkinOfThePlayersOwnIsExported() {
        File(store.root(Console.SFC), "broken").apply { mkdirs() }.let { File(it, "skin.json").writeText("{") }
        File(store.root(Console.SFC), "pack.deltaskin").writeBytes(A)
        own("gba", skin(console = Console.GBA))
        val out = ByteArrayOutputStream()
        assertThrows(SkinFormatException::class.java) { store.export(Console.SFC, "broken", out) }
        for (id in listOf("missing", "pack.deltaskin", "../GBA/gba", ".hidden", "")) {
            assertThrows(id, SkinStoreException::class.java) { store.export(Console.SFC, id, out) }
        }
        assertEquals(0, out.size())
    }

    // --- the way back ---------------------------------------------------------------------------------------------

    @Test fun theExportedZipImportsAsTheSameSkinWithEveryLayout() {
        // Images in one layout only: the two others are drawn from their colours, and have no folder in the zip.
        val dir = own("night", files = mapOf("phone-portrait/a.png" to A, "phone-portrait/pad.png" to B))
        val zip = export("night")
        val other = SkinStore(Files.createTempDirectory("skin-share-other").toFile())
        val ref = other.importOwn(Console.SFC, zip, null, allLayouts = true)
        val arrived = other.file(Console.SFC, ref)!!
        assertEquals(SkinFormat.readFolder(dir).skin, SkinFormat.readFolder(arrived).skin)
        assertEquals(files(dir), files(arrived))
        assertArrayEquals(B, File(arrived, "phone-portrait/pad.png").readBytes())
        assertEquals(listOf("phone", "tablet"), JSONObject(File(arrived, "skin.json").readText()).getJSONArray("forms").let { a -> (0 until a.length()).map(a::getString) })
        // Imported as a template would be, the layouts without a folder are taken for deleted ones (issue 666).
        val asTemplate = other.importOwn(Console.SFC, zip, "As template")
        assertEquals(listOf(SkinLayoutId.PHONE_PORTRAIT), SkinFormat.readFolder(other.file(Console.SFC, asTemplate)!!).skin.layouts.keys.toList())
    }

    // --- a name that is free ----------------------------------------------------------------------------------------

    @Test fun aNameNoSkinShowsIsKept() {
        assertEquals("Night", store.freeName(Console.SFC, "  Night  "))
        own("gba", skin("Night", Console.GBA))
        assertEquals("Night", store.freeName(Console.SFC, "Night"))
    }

    @Test fun aNameThatIsTakenGetsTheNextNumber() {
        own("night")
        assertEquals("Night (2)", store.freeName(Console.SFC, "Night"))
        // Whatever the letters' case, and whatever folder holds it.
        assertEquals("NIGHT (2)", store.freeName(Console.SFC, "NIGHT"))
        own("other", skin("night (2)"))
        assertEquals("Night (3)", store.freeName(Console.SFC, "Night"))
    }

    @Test fun aNumberedNameStaysWithinTheLimit() {
        val long = "N".repeat(SkinStore.MAX_NAME)
        own("long", skin(long))
        val free = store.freeName(Console.SFC, long + "tail")
        assertEquals("N".repeat(SkinStore.MAX_NAME - 4) + " (2)", free)
        assertEquals(SkinStore.MAX_NAME, free.length)
    }

    @Test fun aBlankNameIsRefused() {
        assertThrows(SkinStoreException::class.java) { store.freeName(Console.SFC, "   ") }
    }

    private companion object {
        val A = byteArrayOf(1, 2, 3)
        val B = byteArrayOf(9, 9)
    }
}
