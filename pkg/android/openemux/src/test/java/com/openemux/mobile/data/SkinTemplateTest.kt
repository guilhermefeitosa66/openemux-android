package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** "Download as template" (issue 787): the images a layout has, at their sizes, the README and the zip itself. */
class SkinTemplateTest {
    private fun layout(frame: Boolean = true) = SkinLayout(
        SkinSize(1080, 2238), listOf(SkinRect(36f, 36f, 1008f, 756f)), SkinBackground(0xFF101010.toInt()),
        frame = if (frame) SkinFrame(SkinRect(18f, 18f, 1044f, 864f)) else null,
        elements = listOf(
            SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(800f, 1400f, 180f, 180f)),
            SkinElement("key-1", SkinElementType.KEYPAD, "keypad1", SkinRect(10f, 1000f, 90.4f, 60f)),
            SkinElement("save", SkinElementType.ACTION, "quickSave", SkinRect(40f, 800f, 261f, 90f), image = "disk"),
            SkinElement("dpad", SkinElementType.DPAD, "dpad", SkinRect(60f, 1300f, 420f, 400f)),
            SkinElement("stick", SkinElementType.STICK, "analogStick", SkinRect(300f, 1800f, 201f, 240f)),
            SkinElement("tiny", SkinElementType.BUTTON, "b", SkinRect(0f, 0f, 0.2f, 0.4f)),
        ),
    )

    private fun skin(name: String = "Night Purple") = OpenEmuxSkin(
        Console.SFC, name,
        mapOf(SkinLayoutId.PHONE_PORTRAIT to layout(), SkinLayoutId.TABLET_LANDSCAPE to layout(frame = false).copy(screens = listOf(SkinRect(1.5f, 2f, 300f, 200f)))),
    )

    @Test fun everyElementHasItsImagesAtTheirExactSize() {
        val images = SkinTemplate.images(layout())
        assertEquals(
            listOf(
                "background.png" to (1080 to 2238), "frame.png" to (1044 to 864), "a.png" to (180 to 180), "key-1.png" to (90 to 60),
                "disk.png" to (261 to 90), "dpad.png" to (420 to 400), "dpad-ball.png" to (293 to 293),
                "stick-base.png" to (201 to 240), "stick-knob.png" to (101 to 101), "tiny.png" to (1 to 1),
            ),
            images.map { it.file to (it.width to it.height) },
        )
        assertEquals(
            listOf(
                TemplateImageKind.BACKGROUND, TemplateImageKind.FRAME, TemplateImageKind.BUTTON, TemplateImageKind.KEYPAD,
                TemplateImageKind.ACTION, TemplateImageKind.DPAD, TemplateImageKind.DPAD_BALL, TemplateImageKind.STICK_BASE,
                TemplateImageKind.STICK_KNOB, TemplateImageKind.BUTTON,
            ),
            images.map { it.kind },
        )
        assertNull(images[0].element)
        assertEquals("dpad", images[6].element?.id)
    }

    @Test fun aLayoutWithNoFrameHasNoFrameImage() {
        assertTrue(SkinTemplate.images(layout(frame = false)).none { it.kind == TemplateImageKind.FRAME })
    }

    @Test fun theFileIsNamedAfterTheSkin() {
        assertEquals("night-purple.zip", SkinTemplate.fileName("Night Purple"))
        assertEquals("skin.zip", SkinTemplate.fileName("★★"))
    }

    @Test fun theReadmeListsEveryLayoutAndFile() {
        val text = SkinTemplate.readme(skin())
        assertTrue(text.startsWith("Night Purple: Super Nintendo controller skin"))
        assertTrue(text, "phone-portrait/  (1080 x 2238 px)" in text)
        assertTrue(text, "game screen at 36, 36, 1008 x 756 px" in text)
        assertTrue(text, "game screen at 1.5, 2, 300 x 200 px" in text)
        assertTrue(text, "tablet-landscape/" in text)
        assertTrue(text, "dpad-ball.png" in text && "the D-pad's ball" in text && ", bind dpad" in text)
        assertTrue(text, Regex("\\n  background\\.png +1080 x 2238  px  the whole layout, behind everything\\n").containsMatchIn(text))
        listOf("a button", "a keypad key", "a quick action", "the bezel", "the stick's well", "the stick's knob", "the D-pad,")
            .forEach { assertTrue(it, it in text) }
    }

    @Test fun theZipHoldsTheSkinJsonTheReadmeAndEveryLayoutsImagesInOneFolder() {
        val skin = skin()
        val out = ByteArrayOutputStream()
        val asked = mutableListOf<String>()
        SkinTemplate.write(out, skin, "{ \"raw\": true }\n") { id, image ->
            asked += "${id.id}/${image.file}"
            png(image.width, image.height)
        }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            generateSequence { z.nextEntry }.forEach { entries[it.name] = z.readBytes() }
        }
        assertTrue(entries.keys.all { it.startsWith("night-purple/") })
        assertEquals("{ \"raw\": true }\n", String(entries.getValue("night-purple/skin.json")))
        assertEquals(SkinTemplate.readme(skin), String(entries.getValue("night-purple/README.txt")))
        val images = entries.keys.filter { it.endsWith(".png") }.map { it.removePrefix("night-purple/") }
        assertEquals(asked, images)
        assertEquals(19, images.size)
        assertTrue(images.any { it.startsWith("tablet-landscape/") })
        assertEquals(180 to 180, SkinTemplate.pngSize(entries.getValue("night-purple/phone-portrait/a.png")))
    }

    @Test fun aTemplateReadsBackAsTheSameSkin() {
        val skin = skin()
        val dir = Files.createTempDirectory("template").toFile()
        try {
            val zip = File(dir, "t.zip")
            zip.outputStream().use { out -> SkinTemplate.write(out, skin, SkinFormat.toJson(skin)) { _, i -> png(i.width, i.height) } }
            val pkg = SkinFormat.readZip(zip)
            assertEquals(skin, pkg.skin)
            assertEquals("phone-portrait/disk.png", pkg.image(SkinLayoutId.PHONE_PORTRAIT, "disk.png"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aPngSizeComesFromItsHeader() {
        assertEquals(300 to 70000, SkinTemplate.pngSize(png(300, 70000)))
        assertNull(SkinTemplate.pngSize(ByteArray(10)))
        assertNull(SkinTemplate.pngSize(ByteArray(30) { 7 }))
    }

    companion object {
        /** A PNG's signature and IHDR, enough for its size to read. */
        fun png(w: Int, h: Int): ByteArray {
            val head = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13) +
                "IHDR".toByteArray()
            fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
            return head + int(w) + int(h) + byteArrayOf(8, 6, 0, 0, 0)
        }
    }
}
