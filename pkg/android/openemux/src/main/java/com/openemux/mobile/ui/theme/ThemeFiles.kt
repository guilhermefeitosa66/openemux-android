package com.openemux.mobile.ui.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinImportTooLarge
import com.openemux.mobile.data.theme.ThemeImportError
import com.openemux.mobile.data.theme.ThemeImportFailure
import com.openemux.mobile.data.theme.ThemeStore
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.ActiveTheme
import com.openemux.mobile.ui.ThemePref
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Theme files in and out of the app (issue 1018): the picker, "Open with", Share and Save. */
object ThemeFiles {
    /** The MIME type a theme file is offered as: it has none of its own. */
    const val MIME = "application/octet-stream"

    /** Whether a file named [name] is a theme file (by its extension; its content is checked on import). */
    fun isThemeFile(name: String?): Boolean = name?.lowercase()?.endsWith(".${ThemeStore.EXTENSION}") == true

    /** The name a theme's file is offered under. */
    fun fileName(theme: UserTheme): String = "${UserTheme.slugify(theme.name)}.${ThemeStore.EXTENSION}"

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /**
     * Imports the theme file at [uri]: copied into the cache (no more than a theme file may weigh), checked, then
     * added as a new theme and picked. The theme, or why not.
     */
    suspend fun import(context: Context, uri: Uri): Result<UserTheme> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "theme-import").apply { mkdirs() }
        val copy = File(dir, "import-${System.nanoTime()}.zip")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
            input.use { i -> copy.outputStream().use { SkinImport.copyLimited(i, it, ThemeStore.MAX_ZIP_BYTES) } }
            Result.success(ActiveTheme.store(context).import(copy))
        } catch (e: SkinImportTooLarge) {
            Result.failure(ThemeImportError(ThemeImportFailure.TOO_LARGE, e.message ?: ""))
        } catch (e: ThemeImportError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(ThemeImportError(ThemeImportFailure.NOT_THEME, e.message ?: ""))
        } catch (e: Exception) {
            // A provider that refuses the read (SecurityException), a zip with names that are not UTF-8
            // (IllegalArgumentException): never a crash, the file is simply not a theme.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ThemeImportError(ThemeImportFailure.NOT_THEME, e.toString()))
        } finally {
            copy.delete()
        }
    }

    /** What the person reads after an import from [result]; a theme that came in is picked. */
    fun finishImport(context: Context, result: Result<UserTheme>) {
        result.onSuccess { theme ->
            ThemePref.set(context, theme.id)
            Toast.makeText(context, context.getString(R.string.theme_import_ok, theme.name), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            val reason = (e as? ThemeImportError)?.reason ?: ThemeImportFailure.NOT_THEME
            Toast.makeText(context, context.getString(failureText(reason)), Toast.LENGTH_LONG).show()
        }
    }

    fun failureText(reason: ThemeImportFailure): Int = when (reason) {
        ThemeImportFailure.NOT_THEME -> R.string.theme_import_not_theme
        ThemeImportFailure.DAMAGED -> R.string.theme_import_damaged
        ThemeImportFailure.IMAGE -> R.string.theme_import_image
        ThemeImportFailure.TOO_LARGE -> R.string.theme_import_too_large
    }

    /** [theme] written to the cache as a theme file, for Share. */
    suspend fun exportToCache(context: Context, theme: UserTheme): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "theme-export").apply { deleteRecursively(); mkdirs() }
        File(dir, fileName(theme)).also { file -> file.outputStream().use { ActiveTheme.store(context).export(theme.id, it) } }
    }

    /** [theme] written to the document at [uri] the person created; false when that failed. */
    suspend fun save(context: Context, theme: UserTheme, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { ActiveTheme.store(context).export(theme.id, it) } ?: error("no stream")
        }.isSuccess
    }

    /** The share sheet for [file], through the app's file provider (only the export folder is exposed). */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.themes", file)
        val send = Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.theme_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
