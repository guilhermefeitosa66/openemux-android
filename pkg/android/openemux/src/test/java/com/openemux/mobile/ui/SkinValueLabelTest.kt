package com.openemux.mobile.ui

import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import com.retroarch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the skin row says and which skin the list marks in use (issues 718, 720, 782). */
class SkinValueLabelTest {
    private fun choice(vararg v: SkinVariant) = SkinChoice(v.map { SkinSwatch(it, null, null) })

    private fun own(id: String, name: String = id, forms: Set<SkinForm> = setOf(SkinForm.PHONE), broken: Boolean = false) =
        StoredSkin(SkinStore.ref(StoredSkinKind.OWN, id), StoredSkinKind.OWN, name, forms, File(id), broken = broken)

    private fun pkg(file: String) =
        StoredSkin(SkinStore.ref(StoredSkinKind.IMPORTED, file), StoredSkinKind.IMPORTED, file, SkinForm.entries.toSet(), File(file))

    private fun state(
        c: SkinChoice, bundled: String = "console", custom: String? = null, own: List<StoredSkin> = emptyList(), imported: List<StoredSkin> = emptyList(),
    ) = SkinListState(c, own, imported, bundled, custom)

    @Test fun nothingIsShownUntilTheListIsKnown() {
        assertNull(skinValue(null))
    }

    @Test fun aConsoleWithVariantsShowsThePickedOne() {
        val c = choice(SkinVariant.CONSOLE, SkinVariant.DARK, SkinVariant.WHITE)
        assertEquals(SkinValue.Label(R.string.settings_skin_console), skinValue(state(c, "console")))
        assertEquals(SkinValue.Label(R.string.settings_skin_dark), skinValue(state(c, "dark")))
        assertEquals(SkinValue.Label(R.string.settings_skin_color_white), skinValue(state(c, "white")))
    }

    @Test fun aSavedVariantTheConsoleDoesNotComeInShowsItsOwnColors() {
        val c = choice(SkinVariant.CONSOLE, SkinVariant.DARK)
        assertEquals(SkinValue.Label(R.string.settings_skin_console), skinValue(state(c, "teal")))
    }

    @Test fun thePlayersSkinInUseIsNamed() {
        val mine = own("mine", "My skin")
        val s = state(choice(SkinVariant.CONSOLE, SkinVariant.DARK), "dark", mine.ref, own = listOf(mine))
        assertEquals(SkinValue.Named("My skin"), skinValue(s))
        assertTrue(s.inUse(mine))
        assertFalse(s.inUse(s.bundled.options[1]))
        assertEquals(SkinVariant.DARK, s.bundledPick?.variant)
    }

    @Test fun anImportedPackageInUseIsNamedToo() {
        val p = pkg("a.deltaskin")
        assertEquals(SkinValue.Named("a.deltaskin"), skinValue(state(choice(SkinVariant.CONSOLE), custom = p.ref, imported = listOf(p))))
    }

    @Test fun aSkinThatIsGoneOrBrokenLeavesTheBundledOneInUse() {
        val broken = own("bad", broken = true)
        val c = choice(SkinVariant.CONSOLE, SkinVariant.DARK)
        val gone = state(c, "dark", "own:gone", own = listOf(broken))
        assertNull(gone.customInUse)
        assertTrue(gone.inUse(c.options[1]))
        assertNull(state(c, "dark", broken.ref, own = listOf(broken)).customInUse)
    }

    @Test fun aConsoleWithNothingToPickHasNoLabel() {
        assertFalse(state(choice(SkinVariant.CONSOLE)).shown)
        assertNull(skinValue(state(choice(SkinVariant.CONSOLE), "dark")))
    }

    @Test fun aBundledSkinToCopyIsEnoughToShowTheList() {
        // Issue 798: a console with only its drawn skin still lists it, for "Create copy and edit".
        val s = state(choice(SkinVariant.CONSOLE)).copy(convertible = setOf(SkinVariant.CONSOLE.id))
        assertTrue(s.shown)
        assertEquals(SkinValue.Label(R.string.settings_skin_console), skinValue(s))
    }

    @Test fun oneSkinOfThePlayersIsEnoughToShowTheList() {
        val s = state(choice(SkinVariant.CONSOLE), own = listOf(own("mine")))
        assertTrue(s.shown)
        assertEquals(SkinValue.Label(R.string.settings_skin_console), skinValue(s))
    }

    @Test fun withoutTheConsolesOwnPackageAnUnknownPickNamesNoOption() {
        val c = choice(SkinVariant.DARK, SkinVariant.WHITE)
        assertNull(c.option("teal"))
        assertNull(skinValue(state(c, "teal")))
    }

    @Test fun aSkinIsPickableOnlyOnTheFormsItWasMadeFor() {
        val phone = own("p", forms = setOf(SkinForm.PHONE))
        assertTrue(madeFor(phone, SkinForm.PHONE))
        assertFalse(madeFor(phone, SkinForm.TABLET))
        assertFalse(madeFor(own("b", forms = SkinForm.entries.toSet(), broken = true), SkinForm.PHONE))
    }

    @Test fun eachFormHasItsLabelAndItsReason() {
        assertEquals(R.string.skins_not_for_phones, notMadeFor(SkinForm.PHONE))
        assertEquals(R.string.skins_not_for_tablets, notMadeFor(SkinForm.TABLET))
        assertEquals(R.string.skins_not_for_folds, notMadeFor(SkinForm.FOLD))
        assertEquals(R.string.skins_form_phone, formLabel(SkinForm.PHONE))
        assertEquals(R.string.skins_form_tablet, formLabel(SkinForm.TABLET))
        assertEquals(R.string.skins_form_fold, formLabel(SkinForm.FOLD))
    }

    @Test fun aNewSkinsNameIsNeverOneTheListAlreadyHas() {
        assertEquals("Red - edited", freeSkinName("Red - edited", emptyList()))
        assertEquals("Red - edited", freeSkinName("  Red - edited ", listOf("Blue", "Red")))
        // A second Edit of the same bundled skin: a number after the name, the first one free.
        assertEquals("Red - edited 2", freeSkinName("Red - edited", listOf("Red - edited")))
        assertEquals("Red - edited 3", freeSkinName("Red - edited", listOf("Red - edited", "Red - edited 2")))
        assertEquals("Red - edited 2", freeSkinName("Red - edited", listOf("Red - edited 3", "Red - edited")))
        // Two names that differ only by case are one name to whoever reads the list.
        assertEquals("Red - edited 2", freeSkinName("Red - edited", listOf("RED - EDITED")))
        assertEquals("red - edited 3", freeSkinName("red - edited", listOf("Red - Edited", "RED - EDITED 2")))
    }

    @Test fun aLongNameIsCutToMakeRoomForItsNumber() {
        val max = SkinStore.MAX_NAME
        assertEquals("x".repeat(max), freeSkinName("x".repeat(max + 10), emptyList()))
        val second = freeSkinName("x".repeat(max + 10), listOf("x".repeat(max)))
        assertEquals("x".repeat(max - 2) + " 2", second)
        // Cut at a space: no two spaces before the number.
        val spaced = "x".repeat(max - 3) + " yz"
        assertEquals("x".repeat(max - 3) + " 2", freeSkinName(spaced, listOf(spaced)))
        // What it gives is a name the store takes as it is.
        assertEquals(second, SkinStore.cleanName(second))
    }
}
