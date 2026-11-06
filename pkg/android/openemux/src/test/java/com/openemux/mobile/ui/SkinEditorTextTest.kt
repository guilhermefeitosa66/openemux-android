package com.openemux.mobile.ui

import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SnapAxis
import com.openemux.mobile.data.SnapGuide
import com.openemux.mobile.data.SnapKind
import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The skin editor's names and labels (issue 784), and its strings in every locale with their arguments. */
class SkinEditorTextTest {
    private val res = File("src/main/res")
    private val locales = listOf("", "-de", "-es", "-fr", "-ja", "-pt-rBR", "-ta", "-zh-rCN")
    private val withArgs = mapOf(
        "editor_layout" to listOf("%1\$s", "%2\$s"),
        "editor_no_layout" to listOf("%1\$s"),
        "editor_percent" to listOf("%1\$d"),
        "editor_guide_align" to listOf("%1\$s"),
        "editor_guide_edge" to listOf("%1\$s"),
        "editor_guide_mirror" to listOf("%1\$s"),
        "editor_leave_title" to listOf("%1\$s"),
        "editor_leave_body" to listOf("%1\$s"),
    )

    private fun strings(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings_skin_editor.xml")).documentElement
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

    @Test fun groupsHaveTheirNames() {
        assertEquals(R.string.editor_group_face, groupName("face"))
        assertEquals(R.string.editor_group_dpad, groupName("dpad"))
        assertEquals(R.string.editor_group_center, groupName("center"))
        assertEquals(R.string.editor_group_shoulders, groupName("shoulders"))
        assertEquals(R.string.editor_group_shoulders, groupName("shoulder"))
        assertEquals(R.string.editor_group_actions, groupName("actions"))
        assertEquals(R.string.editor_group_menu, groupName("menu"))
        assertEquals(R.string.editor_group_keypad, groupName("keypad"))
        assertEquals(R.string.editor_group, groupName("mine"))
    }

    @Test fun aControlIsNamedByItsLabelElseByWhatItIs() {
        val names = mapOf(R.string.editor_group_dpad to "D-pad", R.string.editor_stick to "Stick", R.string.editor_action to "Quick action")
        fun name(type: SkinElementType, label: String? = null, id: String = "x") =
            elementName(SkinElement(id, type, "a", SkinRect(0f, 0f, 1f, 1f), label = label)) { names.getValue(it) }
        assertEquals("A", name(SkinElementType.BUTTON, "A"))
        assertEquals("D-pad", name(SkinElementType.DPAD))
        assertEquals("Stick", name(SkinElementType.STICK, " "))
        assertEquals("Quick action", name(SkinElementType.ACTION))
        assertEquals("SELECT", name(SkinElementType.BUTTON, id = "select"))
        assertEquals("K1", name(SkinElementType.KEYPAD, id = "k1"))
    }

    @Test fun aGuideSaysWhatItSnappedTo() {
        fun text(kind: SnapKind) = guideText(SnapGuide(SnapAxis.X, 0f, kind, "dpad"), "D-pad") { res, arg -> "$res:$arg" }
        assertEquals("${R.string.editor_guide_center}:null", text(SnapKind.CENTER))
        assertEquals("${R.string.editor_guide_align}:D-pad", text(SnapKind.ALIGN))
        assertEquals("${R.string.editor_guide_edge}:D-pad", text(SnapKind.EDGE))
        assertEquals("${R.string.editor_guide_mirror}:D-pad", text(SnapKind.MIRROR))
    }

    @Test fun orientationsHaveTheirNames() {
        assertEquals(R.string.editor_upright, orientationName(SkinOrientation.PORTRAIT))
        assertEquals(R.string.editor_sideways, orientationName(SkinOrientation.LANDSCAPE))
    }
}
