package com.openemux.mobile.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class BiosDef(
    val console: Console,
    /** Any one of these files satisfies the entry (the desktop's any_of). */
    val files: List<String>,
    val required: Boolean,
    val md5: Set<String> = emptySet(),
) {
    @Composable
    fun fileName(): String = files.joinToString(stringResource(R.string.data_bios_or))
}

private fun bios(console: Console, required: Boolean, vararg files: String) = BiosDef(console, files.toList(), required)

/**
 * The desktop's bios_catalog.py. The import (BiosImport.kt) recognizes a file by
 * its name, or by its MD5 when the entry has one. kronos (Saturn) has no Android core,
 * so its entry is left out.
 */
val KNOWN_BIOS = listOf(
    bios(Console.A5200, true, "5200.rom"),
    bios(Console.A5200, false, "ATARIXL.ROM"),
    bios(Console.A5200, false, "ATARIBAS.ROM"),
    bios(Console.A5200, false, "ATARIOSA.ROM"),
    bios(Console.A5200, false, "ATARIOSB.ROM"),
    bios(Console.LYNX, true, "lynxboot.img"),
    bios(Console.FDS, true, "disksys.rom"),
    bios(Console.FDS, false, "gamegenie.nes"),
    BiosDef(Console.GBA, listOf("gba_bios.bin"), false, setOf("a860e8c0b6d573d191e4ec7db1b1e4f6")),
    bios(Console.GG, false, "bios.gg"),
    bios(Console.INTV, true, "exec.bin"),
    bios(Console.INTV, true, "grom.bin"),
    bios(Console.MCD, true, "bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"),
    bios(Console.MCD, false, "bios_MD.bin"),
    bios(Console.NDS, false, "bios7.bin"),
    bios(Console.NDS, false, "bios9.bin"),
    bios(Console.NDS, false, "firmware.bin"),
    bios(Console.NDS, false, "dsi_bios7.bin"),
    bios(Console.NDS, false, "dsi_bios9.bin"),
    bios(Console.NDS, false, "dsi_firmware.bin"),
    bios(Console.NDS, false, "dsi_nand.bin"),
    bios(Console.O2, true, "o2rom.bin"),
    bios(Console.O2, false, "c52.bin"),
    bios(Console.O2, false, "g7400.bin"),
    bios(Console.O2, false, "jopac.bin"),
    bios(Console.PCECD, true, "syscard3.pce", "syscard2.pce", "syscard1.pce", "gexpress.pce"),
    bios(
        Console.PS, true, "scph5500.bin", "scph5501.bin", "scph5502.bin", "PSXONPSP660.bin",
        "ps1_rom.bin", "scph101.bin", "scph7001.bin", "scph1001.bin",
    ),
    bios(Console.SATURN, true, "sega_101.bin", "mpr-17933.bin"),
    bios(Console.SATURN, false, "mpr-18811-mx.ic1"),
    bios(Console.SATURN, false, "mpr-19367-mx.ic1"),
    bios(Console.SMS, false, "bios.sms"),
    bios(Console.GC, false, "IPL.bin"),
)

private val DISC_SHEETS = setOf("cue", "chd", "pbp", "iso", "cso", "rvz", "gcm", "ciso")

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val names: List<String> = emptyList(),
    /** Games left alone because a file of that name was already in the console's folder. */
    val duplicates: Int = 0,
    /** The consoles that received a game or already had it, in the order met: the library opens on the first. */
    val consoles: List<Console> = emptyList(),
)

/** One entry the user picked: a document with a display name. */
private data class Picked(val uri: Uri, val name: String, val size: Long)

class Importer(private val context: Context, private val library: Library) {
    private val resolver get() = context.contentResolver

    /**
     * Extensions in this batch that more than one console takes, with the
     * candidates, most likely first. The UI asks once per extension, like the
     * desktop's import (rom_importer.py).
     */
    suspend fun ambiguities(uris: List<Uri>): Map<String, List<Console>> = withContext(Dispatchers.IO) {
        ambiguitiesOf(uris.mapNotNull { describe(it) })
    }

    suspend fun ambiguitiesInTree(tree: Uri): Map<String, List<Console>> = withContext(Dispatchers.IO) {
        ambiguitiesOf(walkTree(tree))
    }

    /** The name the sender gives [uri], or its last path segment when it gives none. */
    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching { describe(uri)?.name }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    suspend fun importRoms(uris: List<Uri>, choices: Map<String, Console> = emptyMap()): ImportResult = withContext(Dispatchers.IO) {
        importPicked(uris.mapNotNull { describe(it) }, choices)
    }

    suspend fun importRomTree(tree: Uri, choices: Map<String, Console> = emptyMap()): ImportResult = withContext(Dispatchers.IO) {
        importPicked(walkTree(tree), choices)
    }

    private fun ambiguitiesOf(files: List<Picked>): Map<String, List<Console>> {
        val followers = cueTracks(files)
        return files.filter { it.name !in followers }
            .mapNotNull { key(it)?.let { ext -> ext to Console.forExtension(ext) } }
            .filter { it.second.size > 1 }
            .toMap()
    }

    /** The extension that decides a file's console; for a zip, its main entry's. */
    private fun key(picked: Picked): String? {
        val ext = picked.name.substringAfterLast('.', "").lowercase()
        if (ext != "zip") return ext.takeIf { Console.forExtension(it).isNotEmpty() }
        val exts = resolver.openInputStream(picked.uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.filter { !it.isDirectory }
                    .map { it.name.substringAfterLast('.', "").lowercase() }
                    .filter { Console.forExtension(it).isNotEmpty() }.toList()
            }
        }.orEmpty()
        // A disc's sheet names the console better than its tracks do.
        return exts.firstOrNull { it in DISC_SHEETS } ?: exts.firstOrNull { it != "bin" } ?: exts.firstOrNull()
    }

    private fun consoleFor(ext: String, choices: Map<String, Console>): Console? {
        val candidates = Console.forExtension(ext)
        return if (candidates.size > 1) choices[ext] ?: candidates.first() else candidates.firstOrNull()
    }

    /** Track files named by a .cue in the batch: they go wherever their sheet goes. */
    private fun cueTracks(files: List<Picked>): Map<String, String> {
        val tracks = mutableMapOf<String, String>()
        for (cue in files.filter { it.name.endsWith(".cue", ignoreCase = true) }) {
            val text = resolver.openInputStream(cue.uri)?.use { it.readBytes().decodeToString() } ?: continue
            CUE_FILE.findAll(text).forEach { tracks[it.groupValues[1].ifEmpty { it.groupValues[2] }.trim()] = cue.name }
        }
        return tracks
    }

    private val _biosRevision = MutableStateFlow(0)

    /** Bumped after every BIOS import: whatever shows BIOS state keys on it, so it reads the disk again. */
    val biosRevision: StateFlow<Int> = _biosRevision.asStateFlow()

    /** Imports the picked [uris] as [console]'s BIOS files; see [importBiosFiles]. */
    suspend fun importBios(console: Console, uris: List<Uri>): BiosImportReport = withContext(Dispatchers.IO) {
        val unreadable = mutableListOf<BiosImportReport.Rejected>()
        val sources = uris.mapNotNull { uri ->
            val picked = runCatching { describe(uri) }.getOrNull()
            if (picked == null) unreadable += BiosImportReport.Rejected(uri.lastPathSegment ?: "?", BiosImportReport.Reason.IO)
            picked?.let { BiosSource(it.name, it.size, { resolver.openInputStream(it.uri) }) }
        }
        try {
            val report = importBiosFiles(console, sources, library.paths.system)
            report.copy(rejected = report.rejected + unreadable)
        } finally {
            _biosRevision.value++
        }
    }

    fun biosPresent(def: BiosDef): Boolean = def.files.any { File(library.paths.system, it).exists() }

    private fun importPicked(files: List<Picked>, choices: Map<String, Console>): ImportResult {
        var imported = 0
        var skipped = 0
        var duplicates = 0
        val consoles = LinkedHashSet<Console>()
        val followers = cueTracks(files)
        val cueConsole = mutableMapOf<String, Console?>()
        // Sheets first, so their tracks know where to go.
        for (picked in files.sortedBy { if (it.name.endsWith(".cue", ignoreCase = true)) 0 else 1 }) {
            val ext = RomIntake.extensionOf(picked.name)
            var console: Console? = null
            // One unreadable file is that file's failure, not the whole batch's.
            val placed = runCatching {
                if (ext == "zip") {
                    console = key(picked)?.let { consoleFor(it, choices) }
                    console?.let { c -> resolver.openInputStream(picked.uri)?.use { extractZip(it, c) } }
                } else {
                    console = followers[picked.name]?.let { cueConsole[it] } ?: consoleFor(ext, choices)
                    if (ext == "cue") cueConsole[picked.name] = console
                    console?.let { c -> resolver.openInputStream(picked.uri)?.use { RomIntake.place(library.paths.roms(c), picked.name, it) } }
                }
            }.getOrNull()
            if (placed != null) console?.let { consoles += it }
            // A disc's track files are part of the game, not games of their own.
            when {
                placed == null -> skipped++
                picked.name in followers -> Unit
                placed == RomIntake.Placed.COPIED -> imported++
                else -> duplicates++
            }
        }
        return ImportResult(imported, skipped, duplicates = duplicates, consoles = consoles.toList())
    }

    /** COPIED when any entry was new, DUPLICATE when every one was already there, null when none fits [console]. */
    private fun extractZip(input: InputStream, console: Console): RomIntake.Placed? {
        var result: RomIntake.Placed? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                val ext = RomIntake.extensionOf(name)
                // Disc tracks travel with their sheet even where .bin is not a game of its own.
                if (ext !in console.extensions && !(ext == "bin" && "cue" in console.extensions)) continue
                val placed = RomIntake.place(library.paths.roms(console), name, zip)
                if (result != RomIntake.Placed.COPIED) result = placed
            }
        }
        return result
    }

    private fun describe(uri: Uri): Picked? {
        // A file:// path (rare: other apps may not send them since Android 7) has no provider to ask.
        if (uri.scheme == "file") return uri.path?.let(::File)?.takeIf { it.isFile }?.let { Picked(uri, it.name, it.length()) }
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) Picked(uri, c.getString(0) ?: return null, if (c.isNull(1)) -1 else c.getLong(1)) else null }
    }

    private fun walkTree(tree: Uri): List<Picked> {
        val out = mutableListOf<Picked>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val pending = ArrayDeque(listOf(rootId))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        var visited = 0
        while (pending.isNotEmpty() && visited < 5000) {
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            resolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    visited++
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) pending += id
                    else out += Picked(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.getLong(3))
                }
            }
        }
        return out
    }
}
