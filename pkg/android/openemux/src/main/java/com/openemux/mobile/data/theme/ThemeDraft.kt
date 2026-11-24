package com.openemux.mobile.data.theme

import com.openemux.mobile.data.SkinColorMath
import java.io.File

/**
 * What the theme editor holds while it is open (issue 1018): the theme as it was ([original], null for a new one),
 * the [theme] being edited, and the pictures picked but not saved yet ([staged], by image path). Immutable: each
 * change returns a new draft, so Cancel is simply dropping it.
 */
data class ThemeDraft(
    val original: UserTheme?,
    val theme: UserTheme,
    val staged: Map<String, File> = emptyMap(),
    val section: ThemeSection = ThemeSection.COLORS,
) {
    val isNew: Boolean get() = original == null

    /** Something to save: a new theme always is; an edited one when it differs from what was saved. */
    val dirty: Boolean get() = original == null || original != theme

    val problems: List<ContrastProblem> by lazy { UserTheme.contrastProblems(theme) }

    fun warnings(key: String): List<Pair<String, Double>> = UserTheme.warningsFor(key, problems)

    fun hasWarning(section: ThemeSection): Boolean =
        ThemeKeys.inSection(section).any { warnings(it.key).isNotEmpty() && (it.kind == ThemeKind.COLOR) }

    /** The rows [section] shows: a picture's fit, dim and blur only while it has a picture. */
    fun rows(section: ThemeSection): List<ThemeKey> =
        ThemeKeys.inSection(section).filter { it.image == null || theme.values.containsKey(it.image) }

    fun pick(section: ThemeSection) = copy(section = section)

    fun set(key: String, value: Any?) = copy(theme = theme.with(key, value))

    fun setColor(key: String, argb: Int) = set(key, SkinColorMath.hex(argb))

    fun reset(key: String): ThemeDraft {
        val image = ThemeKeys.BY_NAME[key]?.takeIf { it.kind == ThemeKind.IMAGE }
        return if (image != null) removeImage(key) else copy(theme = theme.without(key))
    }

    /** Every setting back to the base, pictures included. */
    fun resetAll() = copy(theme = theme.copy(values = emptyMap()), staged = emptyMap())

    /** A picture picked for [key], already re-encoded into [file] at [path]. */
    fun stageImage(key: String, path: String, file: File) =
        copy(theme = theme.with(key, path), staged = staged + (path to file))

    /** [key]'s picture and its fit, dim and blur gone. */
    fun removeImage(key: String): ThemeDraft {
        val path = theme.values[key] as? String
        var t = theme.without(key)
        ThemeKeys.ALL.filter { it.image == key }.forEach { t = t.without(it.key) }
        return copy(theme = t, staged = if (path != null) staged - path else staged)
    }

    /** Where [key]'s picture is right now: just picked (staged), or the saved theme's own (under [folder]). */
    fun imageFile(key: String, folder: File?): File? {
        val path = theme.values[key] as? String ?: return null
        return staged[path] ?: folder?.let { File(it, path) }
    }

    companion object {
        fun new(name: String, base: String) = ThemeDraft(null, UserTheme("new", UserTheme.cleanName(name), base))

        fun edit(theme: UserTheme) = ThemeDraft(theme, theme)
    }
}
