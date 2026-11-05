package com.openemux.mobile.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.openemux.mobile.OpenEmuxApp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.ImportResult
import com.openemux.mobile.data.RomIntake
import com.retroarch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One ROM import, from the files to the result: the extensions several
 * consoles take are asked first, once each, then everything is copied and the
 * library scanned again. Settings › Import and "Open with"/"Share" both go
 * through it, so they behave the same.
 */
class ImportFlow(private val app: OpenEmuxApp, private val scope: CoroutineScope) {
    var result by mutableStateOf<ImportResult?>(null)
    var working by mutableStateOf(false)
        private set

    /** Extensions several consoles take, still to be answered. */
    var asking by mutableStateOf<List<Pair<String, List<Console>>>>(emptyList())
        private set
    private var choices = emptyMap<String, Console>()
    private var pending: (suspend (Map<String, Console>) -> ImportResult)? = null

    fun run(ambiguities: suspend () -> Map<String, List<Console>>, block: suspend (Map<String, Console>) -> ImportResult) {
        working = true
        scope.launch {
            val open = runCatching { ambiguities() }.getOrDefault(emptyMap())
            if (open.isEmpty()) finish(block)
            else { choices = emptyMap(); pending = block; asking = open.toList() }
        }
    }

    fun files(uris: List<Uri>) = run({ app.importer.ambiguities(uris) }) { app.importer.importRoms(uris, it) }

    fun choose(ext: String, console: Console) {
        choices = choices + (ext to console)
        asking = asking.drop(1)
        if (asking.isEmpty()) pending?.let { finish(it) }
    }

    /** The console question dismissed: nothing is copied. */
    fun cancel() {
        asking = emptyList()
        pending = null
        working = false
    }

    private fun finish(block: suspend (Map<String, Console>) -> ImportResult) {
        val picked = choices
        pending = null
        scope.launch {
            result = runCatching { block(picked) }.getOrDefault(ImportResult(0, 0))
            app.library.scan()
            working = false
        }
    }
}

@Composable
fun rememberImportFlow(): ImportFlow {
    val app = LocalActions.current.app
    val scope = rememberCoroutineScope()
    return remember(app, scope) { ImportFlow(app, scope) }
}

/** "Which console are the .bin files for?", once per extension in the import. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConsoleSheet(flow: ImportFlow) {
    val (ext, candidates) = flow.asking.firstOrNull() ?: return
    OeSheet({ flow.cancel() }) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.settings_import_ext_title, ext), style = Type.Title.copy(fontSize = 22.sp))
            Text(
                stringResource(R.string.settings_import_ext_question, ext),
                style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Card {
                candidates.forEachIndexed { i, c ->
                    ListRow(c.fullName, divider = i > 0, minHeight = 56.dp, leading = { ConsoleIcon(c) }, onClick = { flow.choose(ext, c) })
                }
            }
        }
    }
}

/** The result in one line: what came in, what was skipped, what was already there. */
@Composable
fun importMessage(r: ImportResult): String {
    val duplicates = if (r.duplicates > 0) countString(R.plurals.settings_import_duplicates, r.duplicates, r.duplicates) else null
    val parts = when {
        r.imported > 0 -> listOfNotNull(
            countString(R.plurals.settings_import_games, r.imported, r.imported),
            if (r.skipped > 0) countString(R.plurals.settings_import_skipped, r.skipped, r.skipped) else null,
            duplicates,
        )
        duplicates != null -> listOf(duplicates)
        else -> listOf(stringResource(R.string.settings_import_none))
    }
    return parts.joinToString(" ")
}

/**
 * Files handed over by "Open with" or "Share": imported like Settings ›
 * Import, the result told in a toast and the library opened on the console.
 * Only the activity in front takes them, as with Share nearby's links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalImports() {
    val a = LocalActions.current
    val context = LocalContext.current
    val flow = rememberImportFlow()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refused by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(a.app) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Files that arrive during another import wait for it to end.
            combine(a.app.externalImports, snapshotFlow { flow.working }) { uris, busy -> uris.takeUnless { busy } }.collect { uris ->
                if (uris == null) return@collect
                a.app.externalImports.value = null
                val names = uris.map { a.app.importer.displayName(it) }
                // A lone file no console plays is refused before anything is read or copied.
                val only = names.singleOrNull()
                if (only != null && !RomIntake.isImportable(only)) refused = only
                else flow.files(uris)
            }
        }
    }

    refused?.let { name ->
        val text = stringResource(R.string.import_unsupported, name, RomIntake.extensionOf(name))
        LaunchedEffect(name) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            refused = null // last: clearing it leaves this effect
        }
    }

    ImportConsoleSheet(flow)
    if (flow.working && flow.asking.isEmpty()) {
        // The copy runs to the end: closing this would only hide it.
        OeSheet({}) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Oe.AccentText, strokeWidth = 2.dp)
                Text("  " + stringResource(R.string.settings_import_copying), style = Type.Body)
            }
        }
    }

    flow.result?.let { r ->
        val text = importMessage(r)
        LaunchedEffect(r) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            r.consoles.firstOrNull()?.let { a.nav.navigate(Routes.library(it.id)) { launchSingleTop = true } }
            flow.result = null // last: clearing it leaves this effect
        }
    }
}
