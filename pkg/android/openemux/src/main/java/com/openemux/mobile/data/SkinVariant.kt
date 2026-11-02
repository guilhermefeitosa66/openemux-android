package com.openemux.mobile.data

import androidx.annotation.StringRes
import com.retroarch.R

/**
 * The bundled skin a console plays with (issue 718): its own colors, or Dark, the same layout in black
 * and dark grey. tools/skins builds one package per console for each, in [assetDir] under the app's assets.
 * A skin the user imported for the console wins over both.
 */
enum class SkinVariant(val id: String, @StringRes val label: Int, @StringRes val description: Int, val assetDir: String) {
    CONSOLE("console", R.string.settings_skin_console, R.string.settings_skin_console_desc, "skins"),
    DARK("dark", R.string.settings_skin_dark, R.string.settings_skin_dark_desc, "skins/dark");

    companion object {
        /** The variant saved as [id]; the console's colors when nothing (or something unknown) was saved. */
        fun byId(id: String?): SkinVariant = entries.firstOrNull { it.id == id } ?: CONSOLE
    }
}
