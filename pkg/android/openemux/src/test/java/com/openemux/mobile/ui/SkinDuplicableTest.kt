package com.openemux.mobile.ui

import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Duplicate on an imported package (issue 797) is offered only when there is art to convert. */
class SkinDuplicableTest {
    private fun pkg(forms: Set<SkinForm>, broken: Boolean = false) =
        StoredSkin("pkg:a.deltaskin", StoredSkinKind.IMPORTED, "A", forms, File("a.deltaskin"), broken = broken)

    @Test fun aReadablePackageWithArtCanBeDuplicated() {
        assertTrue(duplicable(pkg(setOf(SkinForm.PHONE))))
        assertTrue(duplicable(pkg(setOf(SkinForm.TABLET))))
    }

    @Test fun anUnreadablePackageOrOneWithNoCompleteArtCannot() {
        assertFalse(duplicable(pkg(emptySet())))
        assertFalse(duplicable(pkg(setOf(SkinForm.PHONE), broken = true)))
    }
}
