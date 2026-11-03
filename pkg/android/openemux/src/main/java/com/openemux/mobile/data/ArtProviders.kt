package com.openemux.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where artwork comes from (issue 753). [id] is what the source records and the settings store. */
enum class ArtProvider(val id: String, val kinds: Set<ArtKind>) {
    LIBRETRO("libretro", setOf(ArtKind.COVER)),
    SCREENSCRAPER(ArtSources.PROVIDER_SCREENSCRAPER, setOf(ArtKind.COVER, ArtKind.LABEL)),
    MIRROR(ArtSources.PROVIDER_MIRROR, setOf(ArtKind.COVER));

    companion object {
        fun of(id: String?): ArtProvider? = entries.firstOrNull { it.id == id }
    }
}

/** One provider's place in the settings: on or off, and which of its kinds it serves. */
data class ProviderSetting(val provider: ArtProvider, val enabled: Boolean = true, val kinds: Set<ArtKind> = provider.kinds) {
    fun serves(kind: ArtKind) = enabled && kind in kinds
}

/**
 * The "Artwork providers" settings (issue 753): the order the search asks the providers in, and
 * which of them it and the automatic sync may use. The default is the order the app always had:
 * the OpenEmux mirror first for box art, ScreenScraper for labels, libretro as the fallback.
 */
data class ProviderSettings(val order: List<ProviderSetting> = DEFAULT) {
    /** The providers that serve [kind], in order. */
    fun serving(kind: ArtKind): List<ArtProvider> = order.filter { it.serves(kind) }.map { it.provider }

    fun serves(provider: ArtProvider, kind: ArtKind) = order.any { it.provider == provider && it.serves(kind) }

    fun setEnabled(provider: ArtProvider, on: Boolean) = edit(provider) { it.copy(enabled = on) }

    /** Turns [kind] on or off for [provider]; a kind the provider has not got stays off. */
    fun setKind(provider: ArtProvider, kind: ArtKind, on: Boolean) = edit(provider) {
        if (kind !in provider.kinds) it else it.copy(kinds = if (on) it.kinds + kind else it.kinds - kind)
    }

    /** Moves [provider] one place up ([step] -1) or down (+1); at either end nothing moves. */
    fun move(provider: ArtProvider, step: Int): ProviderSettings {
        val i = order.indexOfFirst { it.provider == provider }
        val j = i + step
        if (i < 0 || j !in order.indices) return this
        return copy(order = order.toMutableList().apply { add(j, removeAt(i)) })
    }

    private fun edit(provider: ArtProvider, change: (ProviderSetting) -> ProviderSetting) =
        copy(order = order.map { if (it.provider == provider) change(it) else it })

    fun toJson(): String = JSONArray().apply {
        order.forEach { s ->
            put(JSONObject().apply {
                put("id", s.provider.id)
                put("enabled", s.enabled)
                put("kinds", JSONArray(s.kinds.map { it.name.lowercase() }.sorted()))
            })
        }
    }.toString()

    companion object {
        val DEFAULT = listOf(
            ProviderSetting(ArtProvider.MIRROR),
            ProviderSetting(ArtProvider.SCREENSCRAPER),
            ProviderSetting(ArtProvider.LIBRETRO),
        )

        /**
         * Reads what [toJson] wrote. An unknown provider is dropped, a missing one is added at the
         * end with its defaults, and anything unreadable gives the defaults: a damaged setting
         * must never leave the search with nothing to ask.
         */
        fun parse(raw: String?): ProviderSettings {
            if (raw.isNullOrBlank()) return ProviderSettings()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return ProviderSettings()
            val read = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val provider = ArtProvider.of(o.optString("id")) ?: return@mapNotNull null
                val kinds = o.optJSONArray("kinds")?.let { k ->
                    (0 until k.length()).mapNotNull { n -> ArtKind.entries.firstOrNull { it.name.lowercase() == k.optString(n) } }
                        .filter { it in provider.kinds }.toSet()
                } ?: provider.kinds
                ProviderSetting(provider, o.optBoolean("enabled", true), kinds)
            }.distinctBy { it.provider }
            val missing = DEFAULT.filter { d -> read.none { it.provider == d.provider } }
            return ProviderSettings(read + missing)
        }
    }
}

/** The provider settings, kept in a preferences file of their own. */
class ProviderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(ProviderSettings.parse(prefs.getString(KEY, null)))
    val settings: StateFlow<ProviderSettings> = _settings.asStateFlow()

    fun update(change: (ProviderSettings) -> ProviderSettings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY, next.toJson()).apply()
    }

    private companion object {
        const val PREFS = "artwork"
        const val KEY = "providers"
    }
}
