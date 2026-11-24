package com.openemux.mobile.ui

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openemux.mobile.data.CoreDef
import com.openemux.mobile.data.FilesExport
import com.openemux.mobile.data.GameFiles
import com.openemux.mobile.data.SaveImportCheck
import com.openemux.mobile.data.SaveRefusal
import com.openemux.mobile.data.KeptSave
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.SaveChange
import com.openemux.mobile.game.RaCommand
import com.retroarch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A copy in or out that is running: which game, which row shows it, and how far along (bytes of [total], 0 when unknown). */
private data class FilesJob(val key: String, val row: String, val done: Long, val total: Long)

/** How far an export goes between two redraws of its row: a megabyte. */
private const val PROGRESS_STEP = 1L shl 20

/** The row an import or a restore shows its progress on; an export's is the name of what it writes. */
private const val IMPORT_ROW = "\u0000import"

private const val EXPORT_ROM = "rom"
private const val EXPORT_SAVE = "save"

/**
 * The jobs of the Game files card (issue 1005). They outlive the screen: leaving the detail must not cut an export
 * halfway, so they run on a scope of their own and the card reads their progress from here.
 */
private object GameFilesJobs {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var running by mutableStateOf<FilesJob?>(null)
    /** Bumped when a job changes a game's saves, so the card reads them again. */
    var changes by mutableIntStateOf(0)
}

/** What the card knows of a game's files, read off the main thread. */
private class FilesState(val rom: FilesExport?, val save: FilesExport?, val kept: KeptSave?)

/**
 * The "Game files" section of a game's detail (issue 1005): export the game's file and its save, import a save
 * (another emulator's too) and put back the save an import replaced. Every row is a list row: a stop for the
 * gamepad, read with its value by TalkBack.
 */
@Composable
fun GameFilesCard(rom: Rom, core: CoreDef, hasAutoState: Boolean, modifier: Modifier = Modifier) {
    val a = LocalActions.current
    // The jobs outlive the screen: they hold the application, never the activity.
    val context = LocalContext.current.applicationContext
    val files = remember(a.app) { GameFiles(a.app.library.paths) }
    val job = GameFilesJobs.running
    val busy = job != null
    // Read again when the library changes too: a game played for the first time comes back with a save.
    val revision by a.app.library.revision.collectAsState()
    val state by produceState<FilesState?>(null, rom, revision, GameFilesJobs.changes) {
        value = withContext(Dispatchers.IO) { FilesState(files.romExport(rom), files.saveExport(rom), files.kept(rom)) }
    }
    var confirming by remember { mutableStateOf<Pair<Uri, SaveImportCheck>?>(null) }
    var restoring by rememberSaveable { mutableStateOf(false) }

    // A long export must not stop because the screen went to sleep.
    val view = LocalView.current
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    // Which export the save dialog is for, by kind: kept across a recreation (theme, fold) while the dialog is up.
    var exportKind by rememberSaveable { mutableStateOf<String?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val kind = exportKind
        exportKind = null
        if (uri != null) GameFilesJobs.scope.launch {
            val picked = when (kind) { EXPORT_ROM -> files.romExport(rom); EXPORT_SAVE -> files.saveExport(rom); else -> null }
            // Nothing to write after all: the empty document the dialog made goes.
            if (picked == null) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            else export(context, files, rom, picked, uri)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) GameFilesJobs.scope.launch { inspect(context, files, rom, core, uri)?.let { confirming = it } }
    }

    fun progress(row: String?): String? = when {
        job == null || job.key != rom.key || job.row != row -> null
        job.total > 0 -> context.getString(R.string.detail_files_progress, size(context, job.done), size(context, job.total))
        else -> context.getString(R.string.detail_files_working)
    }

    Column(modifier) {
        Text(stringResource(R.string.detail_files_section).uppercase(), style = Type.SectionCaps, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card {
            val romFile = state?.rom
            ListRow(
                stringResource(R.string.detail_files_export_rom),
                value = progress(romFile?.name) ?: romFile?.let { exportSize(context, it) },
                onClick = {
                    if (romFile == null) toast(context, R.string.detail_files_missing_parts)
                    else { exportKind = EXPORT_ROM; create.launch(romFile.name) }
                },
                enabled = !busy && state != null,
            )
            state?.save?.let { save ->
                ListRow(
                    stringResource(R.string.detail_files_export_save),
                    value = progress(save.name) ?: date(context, save.files.maxOf { it.lastModified() }),
                    divider = true,
                    onClick = { exportKind = EXPORT_SAVE; create.launch(save.name) },
                    enabled = !busy,
                )
            }
            if (GameFiles.importSupported(rom.console)) {
                ListRow(
                    stringResource(R.string.detail_files_import_save),
                    subtitle = stringResource(R.string.detail_files_import_save_sub),
                    value = progress(IMPORT_ROW),
                    divider = true, minHeight = 60.dp,
                    onClick = { open.launch(arrayOf("*/*")) },
                    enabled = !busy,
                )
            }
            state?.kept?.let { kept ->
                ListRow(
                    stringResource(R.string.detail_files_restore),
                    subtitle = stringResource(R.string.detail_files_restore_sub, date(context, kept.keptAt)),
                    divider = true, minHeight = 60.dp,
                    onClick = { restoring = true },
                    enabled = !busy,
                )
            }
        }
    }

    confirming?.let { (uri, check) ->
        OeConfirmDialog(
            { confirming = null },
            stringResource(if (check.replaces) R.string.detail_files_import_title else R.string.detail_files_import_title_new),
            importMessage(context, rom, check, hasAutoState),
            stringResource(if (check.replaces) R.string.detail_files_replace else R.string.detail_files_import),
            { confirming = null; import(context, files, rom, core, uri) },
            stringResource(R.string.detail_cancel),
        )
    }
    if (restoring) {
        OeConfirmDialog(
            { restoring = false },
            stringResource(R.string.detail_files_restore_title), stringResource(R.string.detail_files_restore_text),
            stringResource(R.string.detail_files_restore_confirm), { restoring = false; restore(context, files, rom) },
            stringResource(R.string.detail_cancel),
        )
    }
}

/** "16 MB", or "3 files · 512 MB" for a game made of several. */
private fun exportSize(context: Context, export: FilesExport): String =
    if (export.zipped) context.resources.getQuantityString(R.plurals.detail_files_parts, export.files.size, export.files.size, size(context, export.bytes))
    else size(context, export.bytes)

private fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

private fun date(context: Context, time: Long): String =
    DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

private fun toast(context: Context, text: Int, vararg args: Any) {
    val message = context.getString(text, *args)
    GameFilesJobs.scope.launch(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}

/** The confirmation's text: what happens, what is kept, and what may not match. */
private fun importMessage(context: Context, rom: Rom, check: SaveImportCheck, hasAutoState: Boolean): String {
    val lines = mutableListOf(context.getString(R.string.detail_files_import_text, check.name, rom.title))
    if (check.replaces) lines[0] += " " + context.getString(R.string.detail_files_import_kept)
    if (check.sizeMismatch) {
        val expected = check.expected
        val range = check.range
        lines += when {
            expected != null -> context.getString(R.string.detail_files_import_size, size(context, check.size), size(context, expected))
            range != null && range.first == range.last ->
                context.getString(R.string.detail_files_import_exact, size(context, check.size), rom.console.displayName, size(context, range.first))
            else -> context.getString(
                R.string.detail_files_import_range, size(context, check.size), rom.console.displayName,
                size(context, range!!.first), size(context, range.last),
            )
        }
    }
    if (check.unusualExtension) lines += context.getString(R.string.detail_files_import_ext, check.name)
    lines += context.getString(R.string.detail_files_import_other)
    if (hasAutoState) lines += context.getString(R.string.detail_files_import_restart)
    return lines.joinToString("\n\n")
}

private fun export(context: Context, files: GameFiles, rom: Rom, export: FilesExport, uri: Uri) {
    if (GameFilesJobs.running != null) return
    GameFilesJobs.running = FilesJob(rom.key, export.name, 0, export.bytes)
    GameFilesJobs.scope.launch {
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                var shown = 0L
                files.write(export, out) { done ->
                    // A step a megabyte: the row redraws a few times a second, not on every buffer.
                    if (done - shown >= PROGRESS_STEP || done == export.bytes) {
                        shown = done
                        GameFilesJobs.running = FilesJob(rom.key, export.name, done, export.bytes)
                    }
                }
            }
        }.isSuccess
        // A half-written file is worse than none.
        if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        GameFilesJobs.running = null
        if (ok) toast(context, R.string.detail_files_exported, export.name) else toast(context, R.string.detail_files_export_failed)
    }
}

/** Reads the picked file's name and size and checks it; refusals end here, with a toast. Blocks: call off the main thread. */
private fun inspect(context: Context, files: GameFiles, rom: Rom, core: CoreDef, uri: Uri): Pair<Uri, SaveImportCheck>? {
    val (name, size) = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "") to (if (c.isNull(1)) -1L else c.getLong(1)) else null
        }
    }.getOrNull() ?: ("" to -1L)
    val check = files.inspect(rom, core, name.ifEmpty { uri.lastPathSegment.orEmpty() }, size)
    when (check.refusal) {
        SaveRefusal.EMPTY -> toast(context, R.string.detail_files_refused_empty)
        SaveRefusal.TOO_BIG -> toast(context, R.string.detail_files_refused_big)
        SaveRefusal.NOT_A_SAVE -> toast(context, R.string.detail_files_refused_not_save)
        null -> return if (running(context, rom)) { toast(context, R.string.detail_files_running); null } else uri to check
    }
    return null
}

/**
 * Whether this game is up in the engine; a save must never change under a running core. An engine that does not
 * answer counts as running while the game's process is alive. Blocks.
 */
private fun running(context: Context, rom: Rom): Boolean {
    val alive = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
        .orEmpty().any { it.processName.endsWith(":game") }
    return GameFiles.runningGame(RaCommand.ask("GET_STATUS", 1000), rom.baseName, alive)
}

private fun import(context: Context, files: GameFiles, rom: Rom, core: CoreDef, uri: Uri) {
    if (GameFilesJobs.running != null) return
    GameFilesJobs.running = FilesJob(rom.key, IMPORT_ROW, 0, 0)
    GameFilesJobs.scope.launch {
        // Checked again: the game may have been opened while the dialog was up.
        val result = if (running(context, rom)) null else runCatching {
            context.contentResolver.openInputStream(uri)!!.use { files.import(rom, core, it) }
        }.getOrDefault(SaveChange.FAILED)
        GameFilesJobs.running = null
        GameFilesJobs.changes++
        when (result) {
            null -> toast(context, R.string.detail_files_running)
            SaveChange.DONE -> toast(context, R.string.detail_files_imported)
            SaveChange.TOO_BIG -> toast(context, R.string.detail_files_refused_big)
            SaveChange.FAILED -> toast(context, R.string.detail_files_import_failed)
        }
    }
}

private fun restore(context: Context, files: GameFiles, rom: Rom) {
    if (GameFilesJobs.running != null) return
    GameFilesJobs.running = FilesJob(rom.key, IMPORT_ROW, 0, 0)
    GameFilesJobs.scope.launch {
        val result = if (running(context, rom)) null else files.restore(rom)
        GameFilesJobs.running = null
        GameFilesJobs.changes++
        when (result) {
            null -> toast(context, R.string.detail_files_running)
            SaveChange.DONE -> toast(context, R.string.detail_files_restored)
            else -> toast(context, R.string.detail_files_restore_failed)
        }
    }
}
