package com.openemux.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.openemux.mobile.data.RenameCheck
import com.openemux.mobile.data.RenameResult
import com.openemux.mobile.data.Rom
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rename… on the detail screen (issue 790): the file's name without its extension, selected, with
 * the extension fixed after it. The name is checked as it is typed; [onRenamed] gets the game under
 * its new name once every file moved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(rom: Rom, onClose: () -> Unit, onRenamed: (Rom) -> Unit) {
    val renamer = LocalActions.current.app.renamer
    val scope = rememberCoroutineScope()
    var name by remember(rom.key) { mutableStateOf(TextFieldValue(rom.baseName, TextRange(0, rom.baseName.length))) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // A refusal at the last moment (something took the name meanwhile) shows until the name is edited.
    var refused by remember { mutableStateOf<RenameCheck?>(null) }
    // The check lists the game's folders: off the main thread, again on every keystroke.
    val check by produceState(RenameCheck.UNCHANGED, name.text) {
        value = withContext(Dispatchers.IO) { renamer.check(rom, name.text) }
    }
    val sharer = remember(rom.key) { renamer.sharer(rom) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ready = check == RenameCheck.OK && !busy
    fun rename() {
        if (!ready) return
        busy = true
        failed = false
        // Not cancelled with the sheet: the game leaves the list under its old key as soon as the
        // rename lands, which takes this screen (and the sheet) away before the result is back.
        scope.launch(NonCancellable) {
            when (val result = withContext(Dispatchers.IO) { renamer.rename(rom, name.text) }) {
                is RenameResult.Done -> onRenamed(result.rom)
                is RenameResult.Refused -> refused = result.check
                RenameResult.Failed -> failed = true
            }
            busy = false
        }
    }
    val error = when {
        failed -> stringResource(R.string.rename_error_failed)
        else -> when (refused ?: check) {
            RenameCheck.EMPTY -> stringResource(R.string.rename_error_empty)
            RenameCheck.BAD_CHARS -> stringResource(R.string.rename_error_chars)
            RenameCheck.LEADING_DOT -> stringResource(R.string.rename_error_dot)
            RenameCheck.TOO_LONG -> stringResource(R.string.rename_error_long)
            RenameCheck.CLASH -> stringResource(R.string.rename_error_clash, rom.console.fullName)
            RenameCheck.LEFTOVERS -> stringResource(R.string.rename_error_leftovers)
            RenameCheck.SHARED -> stringResource(R.string.rename_error_shared, sharer?.file?.name.orEmpty())
            RenameCheck.BUSY -> stringResource(R.string.rename_error_busy)
            RenameCheck.OK, RenameCheck.UNCHANGED -> null
        }
    }
    val extension = rom.file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.rename_title), style = Type.Section)
            OeTextField(
                stringResource(R.string.rename_label), name, { if (it.text != name.text) { failed = false; refused = null }; name = it },
                Modifier.fillMaxWidth(),
                isError = error != null,
                suffix = extension,
                focusRequester = focus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { rename() }),
            )
            Text(error ?: stringResource(R.string.rename_hint), style = Type.Small.copy(color = if (error != null) Oe.Danger else Oe.Muted))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.detail_cancel), onClose, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.rename_action), ::rename, Modifier.weight(1f), enabled = ready)
            }
        }
    }
}
