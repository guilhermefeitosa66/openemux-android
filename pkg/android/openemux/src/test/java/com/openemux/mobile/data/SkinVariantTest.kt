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

    @Test fun eachColorVariantIsNamedAfterItsColorAndHasNoDescription() {
        assertEquals(SkinVariant.CLEAR_BLUE, SkinVariant.byId("clear-blue"))
        assertEquals("skins/clear-blue", SkinVariant.CLEAR_BLUE.assetDir)
        SkinVariant.entries.drop(2).forEach {
            assertEquals("skins/${it.id}", it.assetDir)
            assertEquals(null, it.description)
        }
        assertEquals(SkinVariant.entries.size, SkinVariant.entries.map { it.label }.toSet().size)
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

    @Test fun anUnknownSavedSkinIsKeptAndPlaysInTheConsolesColors() {
        val odd = JSONObject("""{"core":"mgba","shader":"none","color":"default","skin":"neon"}""")
        val read = ConsoleSettings.fromJson(odd)
        assertEquals("neon", read.skin)
        assertEquals("neon", read.toJson().getString("skin"))
        assertEquals(SkinVariant.CONSOLE, SkinVariant.byId(read.skin))
    }
}
