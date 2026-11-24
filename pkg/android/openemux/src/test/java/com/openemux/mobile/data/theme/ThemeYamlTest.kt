package com.openemux.mobile.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** `theme.yaml` read and written without a YAML library (issue 1018), shaped like the desktop's PyYAML files. */
class ThemeYamlTest {
    @Test fun readsWhatTheDesktopWrites() {
        // yaml.safe_dump(..., sort_keys=False, allow_unicode=True) of a part 1 theme.
        val text = """
            version: 1
            name: 'Mid: night''s #1 – ação'
            base: dark
            library_bg: '#24283b'
            corner_radius: 12.5
            font_scale: 1.0
            density: compact
            library_image: images/library.jpg
        """.trimIndent()
        val map = ThemeYaml.parse(text)
        assertEquals(1, map["version"])
        assertEquals("Mid: night's #1 – ação", map["name"])
        assertEquals("dark", map["base"])
        assertEquals("#24283b", map["library_bg"])
        assertEquals(12.5, map["corner_radius"])
        assertEquals(1.0, map["font_scale"])
        assertEquals("compact", map["density"])
        assertEquals("images/library.jpg", map["library_image"])
    }

    @Test fun scalarsResolveLikeYaml11() {
        val map = ThemeYaml.parse(
            "a: yes\nb: Off\nc: ~\nd:\ne: null\nf: -3\ng: +.5e1\nh: .inf\ni: -.inf\nj: .nan\nk: plain text # comment\n" +
                "l: 99999999999\nm: \"tab\\there \\\"q\\\" \\x41\\u00e9\\U0001F600 \\/ \\\\ \\n\\r\\0\\ \"\n",
        )
        assertEquals(true, map["a"])
        assertEquals(false, map["b"])
        assertNull(map["c"])
        assertTrue(map.containsKey("d"))
        assertNull(map["d"])
        assertNull(map["e"])
        assertEquals(-3, map["f"])
        assertEquals(5.0, map["g"])
        assertEquals(Double.POSITIVE_INFINITY, map["h"])
        assertEquals(Double.NEGATIVE_INFINITY, map["i"])
        assertTrue((map["j"] as Double).isNaN())
        assertEquals("plain text", map["k"])
        assertEquals(99999999999L, map["l"])
        assertEquals("tab\there \"q\" Aé\uD83D\uDE00 / \\ \n\r\u0000 ", map["m"])
    }

    @Test fun skipsCommentsMarkersAndBlankLinesAndFoldsLongScalars() {
        val text = "\uFEFF---\r\n# a theme\r\n\r\nname: 'a long\r\n  name'\r\nother: plain\r\n  goes on\r\nq: \"x\n  y\"\n...\n"
        val map = ThemeYaml.parse(text)
        assertEquals("a long name", map["name"])
        assertEquals("plain goes on", map["other"])
        assertEquals("x y", map["q"])
    }

    @Test fun blocksListsAndFlowValuesComeBackNested() {
        val map = ThemeYaml.parse("block:\n  a: 1\n  b: 2\nlist:\n- 1\n- 2\nflow: [1, 2]\nanchor: &x 1\nlit: |\n  text\nafter: '#ffffff'\nstr: abc\n  - not a list\n")
        assertSame(ThemeYaml.Nested, map["block"])
        assertSame(ThemeYaml.Nested, map["list"])
        assertSame(ThemeYaml.Nested, map["flow"])
        assertSame(ThemeYaml.Nested, map["anchor"])
        assertSame(ThemeYaml.Nested, map["lit"])
        assertEquals("#ffffff", map["after"])
        assertSame(ThemeYaml.Nested, map["str"])
    }

    @Test fun garbageIsAnError() {
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("  indented: first") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("not a mapping") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: 'open") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: 'x' y") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: \"\\q\"") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: \"\\") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: \"\\u12\"") }
        assertThrows(ThemeYamlError::class.java) { ThemeYaml.parse("a: x".padEnd(ThemeYaml.MAX_BYTES + 1, 'x')) }
        assertEquals("x", ThemeYaml.parse("a: 'x' # fine")["a"])
        // An unquoted "#" starts a comment, as PyYAML reads it: no value.
        assertNull(ThemeYaml.parse("accent: #ff0000")["accent"])
    }

    @Test fun writesWhatItReadsBack() {
        val values = linkedMapOf<String, Any>(
            "version" to 1, "big" to 5L, "name" to "It's 'quoted': yes", "base" to "light", "accent" to "#1c71d8",
            "corner_radius" to 12.0, "font_scale" to 1.125, "flag" to true, "f" to 0.5f, "density" to "yes",
        )
        val text = ThemeYaml.write(values)
        assertTrue(text, text.startsWith("version: 1\nbig: 5\nname: 'It''s ''quoted'': yes'\n"))
        assertTrue(text, "corner_radius: 12.0\n" in text)
        assertTrue(text, "font_scale: 1.125\n" in text)
        val back = ThemeYaml.parse(text)
        assertEquals("It's 'quoted': yes", back["name"])
        assertEquals("#1c71d8", back["accent"])
        assertEquals(12.0, back["corner_radius"])
        assertEquals(true, back["flag"])
        assertEquals(0.5, back["f"])
        assertEquals("yes", back["density"])
        assertEquals(5, back["big"])
    }
}
