package com.openemux.mobile.ui

import com.openemux.mobile.data.SkinContrast
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFill
import com.openemux.mobile.data.SkinPalettes
import com.openemux.mobile.data.SkinPart
import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The paint panel's names (issue 786), and its strings in every locale with their arguments. */
class SkinPaintTextTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")
    private val withArgs = mapOf("editor_image_size" to listOf("%1\$d", "%2\$d"))

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_skin_paint.xml")).documentElement
        val nodes = root.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun everyLocaleHasEveryStringWithItsArguments() {
        val english = strings("values")
        assertTrue(english.keys.containsAll(withArgs.keys))
        for (locale in locales) {
            val s = strings("values$locale")
            assertEquals("values$locale", english.keys, s.keys)
            s.forEach { (key, value) ->
                assertTrue("$key in values$locale is empty", value.isNotBlank())
                withArgs[key]?.forEach { arg -> assertTrue("$key in values$locale lacks $arg", arg in value) }
            }
        }
    }

    @Test fun everyPartPaletteFillAndWarningHasAName() {
        assertEquals(SkinPart.entries.size, SkinPart.entries.map { partName(it) }.toSet().size)
        assertEquals(R.string.editor_part_knob, partName(SkinPart.KNOB))
        assertEquals(SkinPalettes.ALL.size, SkinPalettes.ALL.map { paletteName(it) }.toSet().size)
        assertEquals(R.string.editor_palette_basic, paletteName(SkinPalettes.byId("basic")))
        assertEquals(SkinFill.entries.size, SkinFill.entries.map { fillName(it) }.toSet().size)
        assertEquals(SkinContrast.entries.size, SkinContrast.entries.map { contrastText(it) }.toSet().size)
    }

    @Test fun theReliefHasItsNameAndNoWarningOfItsOwn() {
        assertEquals(R.string.editor_part_relief, partName(SkinPart.RELIEF))
        // Nothing is read on a relief: painting it shows whatever warning there is, or none.
        assertEquals(SkinContrast.LETTER, contrastFor(SkinPart.RELIEF, setOf(SkinContrast.LETTER)))
        assertNull(contrastFor(SkinPart.RELIEF, emptySet()))
    }

    @Test fun theWarningShownIsThePartsOwnPair() {
        val all = SkinContrast.entries.toSet()
        assertEquals(SkinContrast.LETTER, contrastFor(SkinPart.BUTTON, all))
        assertEquals(SkinContrast.LETTER, contrastFor(SkinPart.LETTER, all))
        assertEquals(SkinContrast.ICONS, contrastFor(SkinPart.FRAME, all))
        assertEquals(SkinContrast.ICONS, contrastFor(SkinPart.ICONS, all))
        assertEquals(SkinContrast.ACTIVE_ICON, contrastFor(SkinPart.ACTIVE, all))
        // Painting the frame can make the active icon unreadable too: it is still said.
        assertEquals(SkinContrast.ACTIVE_ICON, contrastFor(SkinPart.FRAME, setOf(SkinContrast.ACTIVE_ICON)))
        assertEquals(SkinContrast.LETTER, contrastFor(SkinPart.PAD, setOf(SkinContrast.LETTER)))
        assertNull(contrastFor(SkinPart.BACKGROUND, emptySet()))
    }
}
