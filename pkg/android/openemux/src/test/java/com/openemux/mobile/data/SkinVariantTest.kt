package com.openemux.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SkinVariantTest {
    @Test fun aSavedIdNamesItsVariant() {
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId("console"))
        assertEquals(SkinVariant.DARK, SkinVariant.byId("dark"))
    }

    @Test fun nothingOrSomethingUnknownSavedPlaysInTheConsolesColors() {
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId(null))
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId(""))
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId("Dark"))
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId("neon"))
    }

    @Test fun eachVariantHasAnAssetsFolderOfItsOwn() {
        assertEquals("skins", SkinVariant.CONSOLE.assetDir)
        assertEquals("skins/dark", SkinVariant.DARK.assetDir)
        assertEquals(SkinVariant.entries.size, SkinVariant.entries.map { it.assetDir }.toSet().size)
    }

    @Test fun aConsoleSettingsDefaultsToTheConsolesColors() {
        assertEquals("console", ConsoleSettings("fceumm", "none").skin)
    }

    @Test fun theSkinIsSavedAndReadBackPerConsole() {
        val dark = ConsoleSettings("snes9x", "crt", "red", "dark")
        val json = dark.toJson()
        assertEquals("dark", json.getString("skin"))
        assertEquals(dark, ConsoleSettings.fromJson(JSONObject(json.toString())))
        val plain = ConsoleSettings("fceumm", "none")
        assertEquals(plain, ConsoleSettings.fromJson(plain.toJson()))
    }

    @Test fun settingsSavedBeforeTheSkinExistedKeepTheConsolesColors() {
        val old = JSONObject("""{"core":"mgba","shader":"none","color":"default"}""")
        assertEquals(ConsoleSettings("mgba", "none", "default", "console"), ConsoleSettings.fromJson(old))
        val noColor = JSONObject("""{"core":"mgba","shader":"none"}""")
        assertEquals(ConsoleSettings("mgba", "none"), ConsoleSettings.fromJson(noColor))
    }

    @Test fun anUnknownSavedSkinIsReadAsTheConsolesColors() {
        val odd = JSONObject("""{"core":"mgba","shader":"none","color":"default","skin":"neon"}""")
        assertEquals("console", ConsoleSettings.fromJson(odd).skin)
    }
}
