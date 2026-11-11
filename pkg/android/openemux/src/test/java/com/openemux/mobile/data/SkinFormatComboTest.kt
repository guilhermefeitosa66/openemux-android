package com.openemux.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Combo buttons in skin.json (issue 842): new optional keys only, read, written and left out by older apps. */
class SkinFormatComboTest {
    private fun skin(combos: String?, placements: String?, other: String? = null) = JSONObject(
        """{"format":1,"console":"SFC","name":"Combo","layouts":{
          "phone-portrait":{"size":{"width":900,"height":1800},"screens":[{"x":0,"y":0,"width":900,"height":700}],
            "elements":[
              {"id":"y","type":"button","bind":"y","x":550,"y":1250,"width":100,"height":100,"label":"Y"},
              {"id":"b","type":"button","bind":"b","x":650,"y":1350,"width":100,"height":100,"label":"B"},
              {"id":"l","type":"button","bind":"l","x":20,"y":720,"width":200,"height":60,"label":"L"},
              {"id":"r","type":"button","bind":"r","x":680,"y":720,"width":200,"height":60,"label":"R"}
            ]},
          "phone-landscape":{"size":{"width":1800,"height":900},"screens":[{"x":450,"y":0,"width":900,"height":700}],
            "elements":[{"id":"y","type":"button","bind":"y","x":1500,"y":500,"width":100,"height":100,"label":"Y"}]}
        }}""",
    ).apply {
        combos?.let { put("combos", JSONArray(it)) }
        placements?.let { getJSONObject("layouts").getJSONObject("phone-portrait").put("combos", JSONArray(it)) }
        other?.let { getJSONObject("layouts").getJSONObject("phone-landscape").put("combos", JSONArray(it)) }
    }.toString()

    private val yb = """[{"id":"combo-1","buttons":["y","b"]}]"""
    private val placed = """[{"id":"combo-1","x":540,"y":1460,"width":100,"height":100,"depth":8,"colors":{"button":"#5A606E"}}]"""

    private fun SkinLayout.combo(id: String = "combo-1") = elements.firstOrNull { it.id == id && it.isCombo }
    private fun OpenEmuxSkin.portrait() = layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)

    private fun refused(error: SkinError, path: String, json: () -> String) {
        try {
            SkinFormat.parse(json())
            fail("expected $error")
        } catch (e: SkinFormatException) {
            assertEquals(e.message, error, e.error)
            assertEquals(path, e.path)
        }
    }

    @Test fun aPlacedComboIsARoundButtonPressingItsInputs() {
        val s = SkinFormat.parse(skin(yb, placed))
        val c = s.portrait().combo()!!
        assertEquals(SkinElementType.BUTTON, c.type)
        assertEquals("y|b", c.bind)
        assertEquals("Y+B", c.label)
        assertEquals(SkinShape.CIRCLE, c.shape)
        assertEquals(SkinRect(540f, 1460f, 100f, 100f), c.rect)
        assertEquals(8f, c.depth)
        assertEquals(0xFF5A606E.toInt(), c.colors["button"])
        assertEquals(listOf(SkinCombo("combo-1", listOf("y", "b"))), s.combos)
        // A layout that does not place it has no combo.
        assertNull(s.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).combo())
    }

    @Test fun aSkinWithCombosRoundTripsAndKeepsThemOutOfElements() {
        val s = SkinFormat.parse(skin(yb, placed, """[{"id":"combo-1","x":1300,"y":750,"width":100,"height":100,"shape":"pill"}]"""))
        val text = SkinFormat.toJson(s)
        assertEquals(s, SkinFormat.parse(text))
        val json = JSONObject(text)
        assertEquals(JSONArray(yb).toString(), json.getJSONArray("combos").toString())
        val portrait = json.getJSONObject("layouts").getJSONObject("phone-portrait")
        // Never an element with a bind or a type an older app refuses.
        for (i in 0 until portrait.getJSONArray("elements").length()) {
            assertFalse('|' in portrait.getJSONArray("elements").getJSONObject(i).getString("bind"))
        }
        val entry = portrait.getJSONArray("combos").getJSONObject(0)
        assertEquals(setOf("id", "x", "y", "width", "height", "depth", "colors"), entry.keys().asSequence().toSet())
        // A shape other than the circle is written; the circle is the default.
        assertEquals("pill", json.getJSONObject("layouts").getJSONObject("phone-landscape").getJSONArray("combos").getJSONObject(0).getString("shape"))
    }

    @Test fun aSkinWithNoCombosWritesNoComboKeys() {
        val text = SkinFormat.toJson(SkinFormat.parse(skin(null, null)))
        assertFalse("combos" in text)
    }

    @Test fun anAppThatReadsNoCombosStillOpensTheSkin() {
        // The rules before issue 842: an element's bind and type checked, every unknown key ignored. Simulated by
        // reading only what such an app reads: the root and layout keys it knows, "combos" being ignored.
        val text = SkinFormat.toJson(SkinFormat.parse(skin(yb, placed)))
        val old = JSONObject(text).apply {
            remove("combos")
            getJSONObject("layouts").keys().forEach { getJSONObject("layouts").getJSONObject(it).remove("combos") }
        }
        val elements = JSONObject(text).getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").toString()
        assertEquals(elements, old.getJSONObject("layouts").getJSONObject("phone-portrait").getJSONArray("elements").toString())
        val read = SkinFormat.parse(old.toString())
        assertTrue(read.combos.isEmpty())
        assertEquals(4, read.portrait().elements.size)
    }

    @Test fun aComboThisAppCannotPressIsLeftOutNotRefused() {
        listOf(
            """[{"id":"combo-1","buttons":["y","up"]}]""",
            """[{"id":"combo-1","buttons":["y"]}]""",
            """[{"id":"combo-1","buttons":["y","b","l","r"]}]""",
            """[{"id":"combo-1","buttons":["y","y"]}]""",
            """[{"id":"combo-1","buttons":["y","warp"]}]""",
        ).forEach { combos ->
            val s = SkinFormat.parse(skin(combos, placed))
            assertTrue(combos, s.combos.isEmpty())
            assertEquals(setOf("combo-1"), s.leftOutCombos)
            assertEquals(4, s.portrait().elements.size)
        }
        // A place for a combo the skin does not have is left out too.
        assertTrue(SkinFormat.parse(skin(null, placed)).combos.isEmpty())
        assertTrue(SkinFormat.parse(skin("[]", placed)).combos.isEmpty())
    }

    @Test fun aBrokenComboIsRefusedLikeAnElement() {
        refused(SkinError.BAD_ID, "combos[0].id") { skin("""[{"id":"Combo 1","buttons":["y","b"]}]""", null) }
        refused(SkinError.DUPLICATE_ID, "combos[1].id") { skin("""[{"id":"c","buttons":["y","up"]},{"id":"c","buttons":["y","b"]}]""", null) }
        refused(SkinError.MISSING_KEY, "combos[0].buttons") { skin("""[{"id":"c"}]""", null) }
        refused(SkinError.BAD_VALUE, "combos[0].buttons[1]") { skin("""[{"id":"c","buttons":["y",2]}]""", null) }
        refused(SkinError.BAD_VALUE, "combos[0]") { skin("""["c"]""", null) }
        refused(SkinError.BAD_VALUE, "combos") { JSONObject(skin(null, null)).put("combos", "y+b").toString() }
        refused(SkinError.BAD_ID, "layouts.phone-portrait.combos[0].id") { skin(yb, """[{"id":"Bad id","x":0,"y":800,"width":10,"height":10}]""") }
        // A combo placed twice on a layout, or with an element's id.
        refused(SkinError.DUPLICATE_ID, "layouts.phone-portrait.combos[1].id") { skin(yb, """[${placed.trim('[', ']')},${placed.trim('[', ']')}]""") }
        refused(SkinError.DUPLICATE_ID, "layouts.phone-portrait.combos[0].id") {
            skin("""[{"id":"y","buttons":["y","b"]}]""", """[{"id":"y","x":0,"y":800,"width":10,"height":10}]""")
        }
        refused(SkinError.OUT_OF_LAYOUT, "layouts.phone-portrait.combos[0]") { skin(yb, """[{"id":"combo-1","x":850,"y":800,"width":100,"height":100}]""") }
        refused(SkinError.BAD_COLOR, "layouts.phone-portrait.combos[0].colors.button") {
            skin(yb, """[{"id":"combo-1","x":0,"y":800,"width":100,"height":100,"colors":{"button":"grey"}}]""")
        }
        refused(SkinError.UNKNOWN_COLOR_PART, "layouts.phone-portrait.combos[0].colors.knob") {
            skin(yb, """[{"id":"combo-1","x":0,"y":800,"width":100,"height":100,"colors":{"knob":"#000000"}}]""")
        }
    }

    @Test fun aPlaceIgnoresTheKeysAComboDoesNotTake() {
        val s = SkinFormat.parse(
            skin(yb, """[{"id":"combo-1","x":0,"y":800,"width":100,"height":100,"type":"stick","bind":"analogStick","image":"pic","art":"face","group":"face","label":"Z"}]"""),
        )
        val c = s.portrait().combo()!!
        assertEquals("y|b", c.bind)
        assertEquals("Y+B", c.label)
        assertNull(c.image)
        assertNull(c.group)
        assertEquals(SkinArt.FULL, c.art)
    }

    @Test fun theLayoutsCombosAreWrittenOnTheirOwn() {
        val s = SkinFormat.parse(skin(yb, placed))
        val text = SkinFormat.layoutCombosText(s.portrait().elements)!!
        assertEquals(1, JSONArray(text).length())
        assertNull(SkinFormat.layoutCombosText(s.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).elements))
        assertEquals(JSONArray(yb).toString(), JSONArray(SkinFormat.combosText(s.combos)!!).toString())
        assertNull(SkinFormat.combosText(emptyList()))
        // A layout written alone (the editor adding one) carries its combos under its own key.
        val layout = JSONObject(SkinFormat.layoutText(s.portrait()))
        assertEquals(1, layout.getJSONArray("combos").length())
        assertEquals(4, layout.getJSONArray("elements").length())
    }

    @Test fun theLabelUsesTheNamesOfAnyLayout() {
        val s = SkinFormat.parse(skin("""[{"id":"combo-1","buttons":["l","r"]}]""", null, """[{"id":"combo-1","x":0,"y":750,"width":100,"height":100}]"""))
        assertEquals("L+R", s.layouts.getValue(SkinLayoutId.PHONE_LANDSCAPE).combo()!!.label)
    }
}
