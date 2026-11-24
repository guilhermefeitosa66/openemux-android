package com.openemux.mobile.data.theme

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The ready-made themes the app ships (issue 1018): Pastel, Dracula, Kitty, Neon and Tokyo Night, with the same
 * ids and colors as on the desktop. Read-only like System, Light and Dark; Duplicate makes an editable copy.
 *
 * Their files live in the APK under `assets/themes/<id>/` (made by tools/gen-ready-themes.py) and are copied once
 * per app version into [root], so the pictures are plain files like a user theme's.
 */
object ReadyThemes {
    val IDS = listOf("pastel", "dracula", "kitty", "neon", "tokyo-night")

    fun isReadyId(id: String?): Boolean = id != null && id in IDS

    /** The theme [id] as installed under [root]; null when it is not there or cannot be read. */
    fun load(root: File, id: String): UserTheme? {
        if (!isReadyId(id)) return null
        val file = File(File(root, id), ThemeStore.THEME_FILE)
        return try {
            UserTheme.parse(ThemeYaml.parse(file.readText()), id)
        } catch (e: Exception) {
            if (e !is IOException && e !is ThemeYamlError && e !is ThemeFileError) throw e
            null
        }
    }

    fun all(root: File): List<UserTheme> = IDS.mapNotNull { load(root, it) }

    /**
     * Copies the bundled files into [root] unless [stamp] (the app's version) already did: [list] names a bundled
     * folder's files, [open] opens one, both relative to `themes/`.
     */
    fun install(root: File, stamp: String, list: (String) -> List<String>, open: (String) -> InputStream) {
        val marker = File(root, ".installed")
        if (marker.isFile && marker.readText() == stamp && IDS.all { File(File(root, it), ThemeStore.THEME_FILE).isFile }) return
        for (id in IDS) {
            val dir = File(root, id).apply { deleteRecursively() }
            for (path in listOf(ThemeStore.THEME_FILE) + list("$id/${ThemeStore.IMAGES}").map { "${ThemeStore.IMAGES}/$it" }) {
                val target = File(dir, path)
                target.parentFile?.mkdirs()
                open("$id/$path").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        marker.writeText(stamp)
    }

    /** The pictures [theme] names, as files under [root]: what a duplicate copies. */
    fun images(root: File, theme: UserTheme): Map<String, File> =
        ThemeKeys.IMAGE_KEYS.mapNotNull { theme.values[it] as? String }.associateWith { File(File(root, theme.id), it) }.filterValues { it.isFile }
}
