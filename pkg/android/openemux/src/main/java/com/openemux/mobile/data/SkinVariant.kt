package com.openemux.mobile.data

import androidx.annotation.StringRes
import com.retroarch.R

/**
 * The bundled skin a console plays with: its own colors, Dark, the same layout in black and dark grey
 * (issue 718), or one of the official colors the console was sold in (issue 720). tools/skins builds one
 * package per console for each variant it comes in, in [assetDir] under the app's assets; which consoles
 * come in which colors is the catalog the build writes (skins/variants.json, read by [SkinCatalog]).
 * A skin the user imported for the console wins over all of them.
 *
 * [label] is the plain color word the picker shows; [description], when there is one, the line under it.
 */
enum class SkinVariant(val id: String, @StringRes val label: Int, @StringRes val description: Int?, val assetDir: String) {
    CONSOLE("console", R.string.settings_skin_console, R.string.settings_skin_console_desc, "skins"),
    DARK("dark", R.string.settings_skin_dark, R.string.settings_skin_dark_desc, "skins/dark"),
    WHITE("white", R.string.settings_skin_color_white),
    BLACK("black", R.string.settings_skin_color_black),
    GREY("grey", R.string.settings_skin_color_grey),
    SILVER("silver", R.string.settings_skin_color_silver),
    GOLD("gold", R.string.settings_skin_color_gold),
    RED("red", R.string.settings_skin_color_red),
    PINK("pink", R.string.settings_skin_color_pink),
    ORANGE("orange", R.string.settings_skin_color_orange),
    YELLOW("yellow", R.string.settings_skin_color_yellow),
    GREEN("green", R.string.settings_skin_color_green),
    TEAL("teal", R.string.settings_skin_color_teal),
    TURQUOISE("turquoise", R.string.settings_skin_color_turquoise),
    BLUE("blue", R.string.settings_skin_color_blue),
    PURPLE("purple", R.string.settings_skin_color_purple),
    CLEAR("clear", R.string.settings_skin_color_clear),
    CLEAR_BLUE("clear-blue", R.string.settings_skin_color_clear_blue),
    CLEAR_PINK("clear-pink", R.string.settings_skin_color_clear_pink);

    /** A color variant: its packages in a folder named after it. */
    constructor(id: String, @StringRes label: Int) : this(id, label, null, "skins/$id")

    companion object {
        /** The variant saved as [id]; the console's colors when nothing (or something unknown) was saved. */
        fun byId(id: String?): SkinVariant = entries.firstOrNull { it.id == id } ?: CONSOLE
    }
}
