package com.openemux.mobile.game

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.OpenEmuxSkin
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinSize
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import com.openemux.mobile.ui.SkinChoice
import com.openemux.mobile.ui.SkinEditTarget
import com.openemux.mobile.ui.SkinListState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** "Edit skin" from the pause sheet (issue 827): what it opens, and what a trip to the editor leaves behind. */
class SkinEditSessionTest {
    private lateinit var base: File
    private lateinit var store: SkinStore
    private val console = Console.SFC

    private val console0 = SkinSwatch(SkinVariant.CONSOLE, null, null)
    private val dark = SkinSwatch(SkinVariant.DARK, null, null)

    @Before fun setUp() {
        base = Files.createTempDirectory("skin-session").toFile()
        store = SkinStore(base)
    }

    @After fun tearDown() {
        base.deleteRecursively()
    }

    private fun stored(ref: String, kind: StoredSkinKind, forms: Set<SkinForm> = setOf(SkinForm.PHONE), broken: Boolean = false) =
        StoredSkin(ref, kind, "Skin", forms, File(ref.substringAfter(':')), broken = broken)

    private fun state(
        own: List<StoredSkin> = emptyList(), imported: List<StoredSkin> = emptyList(),
        bundledId: String = SkinVariant.CONSOLE.id, custom: String? = null, convertible: Set<String> = setOf(SkinVariant.CONSOLE.id),
    ) = SkinListState(SkinChoice(listOf(console0, dark)), own, imported, bundledId, custom, convertible)

    private fun own(id: String): File = File(store.root(console), id).apply {
        mkdirs()
        val layout = SkinLayout(
            SkinSize(100, 200), listOf(SkinRect(0f, 0f, 100f, 60f)),
            elements = listOf(SkinElement("a", SkinElementType.BUTTON, "a", SkinRect(10f, 100f, 20f, 20f))),
        )
        File(this, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(OpenEmuxSkin(console, id, mapOf(SkinLayoutId.PHONE_PORTRAIT to layout))))
    }

    private fun session(id: String, copy: Boolean) = SkinEditSession(store, console, SkinEditTarget(id, id, inUse = true), copy)

    @Test fun theirOwnSkinIsEditedInPlace() {
        val mine = stored("own:mine", StoredSkinKind.OWN)
        assertEquals(SkinToEdit.Own(mine), skinToEdit(state(own = listOf(mine), custom = "own:mine")))
    }

    @Test fun aBundledSkinGetsACopy() {
        assertEquals(SkinToEdit.Bundled(dark), skinToEdit(state(bundledId = SkinVariant.DARK.id, convertible = setOf(SkinVariant.DARK.id))))
    }

    @Test fun aBundledSkinWithNothingToConvertCannotBeEdited() {
        assertNull(skinToEdit(state(convertible = emptySet())))
    }

    @Test fun anImportedPackageGetsACopy() {
        val pkg = stored("pkg:a.deltaskin", StoredSkinKind.IMPORTED)
        assertEquals(SkinToEdit.Imported(pkg), skinToEdit(state(imported = listOf(pkg), custom = "pkg:a.deltaskin")))
    }

    @Test fun anImportedPackageWithNoArtCannotBeEdited() {
        val pkg = stored("pkg:a.deltaskin", StoredSkinKind.IMPORTED, forms = emptySet())
        assertNull(skinToEdit(state(imported = listOf(pkg), custom = "pkg:a.deltaskin")))
    }

    @Test fun aSkinInUseThatIsGoneFallsBackToTheBundledOne() {
        assertEquals(SkinToEdit.Bundled(console0), skinToEdit(state(custom = "own:gone")))
    }

    @Test fun leavingWithoutSavingChangesNothing() {
        val dir = own("mine")
        val s = session("mine", copy = false)
        assertNull(s.result())
        s.close()
        assertTrue(dir.exists())
    }

    @Test fun savingAnOwnSkinDrawsItAgain() {
        val s = session("mine", copy = false)
        s.changed()
        assertEquals("own:mine", s.result())
    }

    @Test fun aCopyLeftWithoutSavingIsRemoved() {
        val dir = own("copy")
        val s = session("copy", copy = true)
        assertNull(s.result())
        s.close()
        assertFalse(dir.exists())
    }

    @Test fun aSavedCopyStaysAndIsTheSkinInUse() {
        val dir = own("copy")
        val s = session("copy", copy = true)
        s.changed()
        s.close()
        assertTrue(dir.exists())
        assertEquals("own:copy", s.result())
    }

    @Test fun savingACopyAsNewPlaysTheNewOneAndRemovesTheCopy() {
        val dir = own("copy")
        val s = session("copy", copy = true)
        s.usedNew("own:other")
        s.changed()
        s.close()
        assertFalse(dir.exists())
        assertEquals("own:other", s.result())
    }

    @Test fun aCopySavedThenSavedAsNewIsKept() {
        val dir = own("copy")
        val s = session("copy", copy = true)
        s.changed()
        s.usedNew("own:other")
        s.changed()
        s.close()
        assertTrue(dir.exists())
        assertEquals("own:other", s.result())
    }
}
