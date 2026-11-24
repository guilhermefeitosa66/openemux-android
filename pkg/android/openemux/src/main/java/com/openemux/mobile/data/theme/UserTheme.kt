package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath
import java.text.Normalizer
import java.util.Locale

/**
 * A theme the user made (issue 1018): a [base] (light or dark) and the settings they changed. A key that is not in
 * [values] paints as the base does, so a theme with nothing changed looks exactly like its base.
 */
data class UserTheme(val id: String, val name: String, val base: String, val values: Map<String, Any> = emptyMap()) {
    /** What [key] paints with: the theme's own value, else the base's. */
    fun effective(key: String): Any? = values[key] ?: ThemeKeys.BY_NAME.getValue(key).default(base)

    fun color(key: String): Int = effective(key) as Int

    fun number(key: String): Double = effective(key) as Double

    fun choice(key: String): String = effective(key) as String

    fun isChanged(key: String) = key in values

    /** This theme with [key] set to [value] (validated; an unusable value leaves the key as it was). */
    fun with(key: String, value: Any?): UserTheme {
        val spec = ThemeKeys.BY_NAME[key] ?: return this
        val cleaned = ThemeKeys.validate(spec, value) ?: return this
        return copy(values = values + (key to cleaned))
    }

    /** This theme with [key] back to its base. */
    fun without(key: String): UserTheme = copy(values = values - key)

    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_NAME_LENGTH = 64

        /** What a user theme's id looks like; never a built-in's. */
        private val ID = Regex("[a-z0-9][a-z0-9-]{0,47}")
        private const val SLUG_LENGTH = 40
        /** Ids a user theme can never take: the built-in looks and the ready-made themes (issue 1018). */
        val BUILT_IN = setOf("system", "light", "dark") + ReadyThemes.IDS

        fun isCustomId(value: String?): Boolean = value != null && value !in BUILT_IN && ID.matches(value)

        /** A name as stored: one line of printable text, trimmed, not too long. */
        fun cleanName(name: String?): String {
            if (name == null) return ""
            val printable = name.filter { !Character.isISOControl(it) || it.isWhitespace() }
            return printable.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").take(MAX_NAME_LENGTH).trim()
        }

        /** A folder-safe id from [name]; `theme` when nothing Latin is left. */
        fun slugify(name: String): String {
            val ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("[^\\x00-\\x7F]"), "")
            val slug = ascii.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            return slug.take(SLUG_LENGTH).trim('-').ifEmpty { "theme" }
        }

        /** A theme from a parsed `theme.yaml`; [ThemeFileError] when it cannot be one. [dropped] hears ignored keys. */
        fun parse(data: Map<String, Any?>, id: String, dropped: (String) -> Unit = {}): UserTheme {
            val version = data["version"] ?: FORMAT_VERSION
            if (version !is Int || version < 1) throw ThemeFileError("bad version $version")
            val base = data["base"] as? String
            if (base !in ThemeBase.ALL) throw ThemeFileError("bad base $base")
            val name = cleanName(data["name"] as? String).ifEmpty { id }
            return UserTheme(id, name, base!!, ThemeKeys.validateAll(data, dropped))
        }

        /** The file's mapping: version, name, base, then only the changed keys, in the table's order. */
        fun serialize(theme: UserTheme): Map<String, Any> {
            val out = LinkedHashMap<String, Any>()
            out["version"] = FORMAT_VERSION
            out["name"] = theme.name
            out["base"] = theme.base
            for (spec in ThemeKeys.ALL) {
                val value = theme.values[spec.key] ?: continue
                out[spec.key] = if (spec.kind == ThemeKind.COLOR) SkinColorMath.hex(value as Int).lowercase(Locale.ROOT) else value
            }
            return out
        }

        /**
         * Every foreground/background pair under WCAG AA that the theme itself changed, worst first. Pairs it left
         * alone are the app's own and never warn.
         */
        fun contrastProblems(theme: UserTheme): List<ContrastProblem> {
            val out = mutableListOf<ContrastProblem>()
            for ((fg, backgrounds) in ThemeKeys.CONTRAST_PAIRS) {
                for (bg in backgrounds) {
                    if (!theme.isChanged(fg) && !theme.isChanged(bg)) continue
                    // Unset, the console list is each console's own color, not this default: nothing to check.
                    if (bg == "console_list_bg" && !theme.isChanged(bg)) continue
                    val ratio = SkinColorMath.contrast(theme.color(fg), theme.color(bg))
                    if (ratio < ThemeKeys.WCAG_AA) out += ContrastProblem(fg, bg, ratio)
                }
            }
            return out.sortedBy { it.ratio }
        }

        /** The failing pairs [key] takes part in, as (the other key, ratio). */
        fun warningsFor(key: String, problems: List<ContrastProblem>): List<Pair<String, Double>> =
            problems.mapNotNull {
                when (key) {
                    it.fg -> it.bg to it.ratio
                    it.bg -> it.fg to it.ratio
                    else -> null
                }
            }
    }
}

data class ContrastProblem(val fg: String, val bg: String, val ratio: Double)

class ThemeFileError(message: String) : Exception(message)
