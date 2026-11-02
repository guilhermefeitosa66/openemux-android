package com.openemux.mobile.data

import org.json.JSONObject

/** One choice of a console's skin: the [variant] and its swatch, the shell's color and its buttons' (ARGB). */
data class SkinSwatch(val variant: SkinVariant, val body: Long?, val key: Long?)

/**
 * Which variants each console comes in, in the order the picker lists them (issue 720): the catalog
 * tools/skins/build.py writes next to the packages, `{"GBA": [{"id": "console", "body": "#5B5293", "key": "#C9C9CB"}, ...]}`.
 */
object SkinCatalog {
    const val ASSET = "skins/variants.json"

    /**
     * [console]'s variants in [json], in order. An id this build does not know (a newer catalog's) and a repeated one are
     * left out; a color that is not `#RRGGBB` leaves the swatch without it. Unreadable JSON lists nothing.
     */
    fun parse(json: String?, console: Console): List<SkinSwatch> {
        val list = runCatching { JSONObject(json.orEmpty()).optJSONArray(console.id) }.getOrNull() ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val variant = SkinVariant.entries.firstOrNull { it.id == o.optString("id") } ?: return@mapNotNull null
            SkinSwatch(variant, color(o.optString("body")), color(o.optString("key")))
        }.distinctBy { it.variant }
    }

    /** `#RRGGBB` as an opaque ARGB color; null for anything else. */
    fun color(hex: String): Long? =
        hex.takeIf { it.length == 7 && it[0] == '#' }?.substring(1)
            ?.takeIf { digits -> digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } }
            ?.toLong(16)?.let { 0xFF000000 or it }
}
