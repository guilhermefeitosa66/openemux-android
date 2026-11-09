package com.openemux.mobile.game

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import com.openemux.mobile.ui.SkinEditTarget
import com.openemux.mobile.ui.SkinListState
import com.openemux.mobile.ui.duplicable

/** What "Edit skin" opens for the skin in use (issue 827). */
sealed interface SkinToEdit {
    /** The player's own skin, edited in place. */
    data class Own(val skin: StoredSkin) : SkinToEdit
    /** A bundled skin never changes: a copy of the player's own is edited, as its Edit does outside the game. */
    data class Bundled(val swatch: SkinSwatch) : SkinToEdit
    /** An imported package stays as it is: a copy in OpenEmux's own format is edited, as Duplicate makes one. */
    data class Imported(val skin: StoredSkin) : SkinToEdit
}

/** What [state]'s skin in use can be edited as; null when there is nothing to edit (no art to convert, unreadable). */
fun skinToEdit(state: SkinListState): SkinToEdit? {
    val custom = state.customInUse
    if (custom != null) return when (custom.kind) {
        StoredSkinKind.OWN -> SkinToEdit.Own(custom)
        StoredSkinKind.IMPORTED -> SkinToEdit.Imported(custom).takeIf { duplicable(custom) }
    }
    return state.bundledPick?.takeIf(state::convertible)?.let(SkinToEdit::Bundled)
}

/**
 * One trip to the editor from the pause sheet. [copy]: [target] was made just for it, so an editor left without saving
 * takes it back and the skin in use is as it was ("Cancelling leaves the skin as it was").
 */
class SkinEditSession(
    private val store: SkinStore,
    private val console: Console,
    val target: SkinEditTarget,
    private val copy: Boolean,
) {
    private var saved = false
    private var saveAsNew: String? = null

    /** The editor saved "as new" a skin that was in use: the game plays that one now. */
    fun usedNew(ref: String) {
        saveAsNew = ref
    }

    /** The editor saved: into [target], unless [usedNew] just named the new skin it saved instead. */
    fun changed() {
        if (saveAsNew == null) saved = true
    }

    /** The skin the game should play after the editor closes; null leaves the one in use. */
    fun result(): String? = saveAsNew ?: if (saved) SkinStore.ref(StoredSkinKind.OWN, target.id) else null

    /** A copy made for this trip that nothing was saved into is removed. */
    fun close() {
        if (copy && !saved) store.delete(console, target.id)
    }
}
