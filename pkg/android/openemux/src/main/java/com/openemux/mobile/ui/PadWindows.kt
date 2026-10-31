package com.openemux.mobile.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's bottom sheet: a [ModalBottomSheet] whose window answers the pad
 * like the screens do (A, B, the screen buttons, the stick, focus in on
 * open and back to the opener on close).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OeSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val opener = LocalPadScope.current
    val scope = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides scope) {
        ModalBottomSheet(
            onDismissRequest, Modifier.padRoot(scope), sheetState = sheetState, containerColor = Oe.Sheet,
            scrimColor = scrimColor, properties = properties,
        ) {
            PadDialogWindow(scope, opener)
            content()
        }
    }
}

/**
 * The app's confirmation dialog: [confirm] (in the danger colour, it removes
 * or cancels something) and [dismiss], both reachable from the pad, focus
 * landing on [dismiss] first.
 */
@Composable
fun OeConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
) {
    val opener = LocalPadScope.current
    val scope = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides scope) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.padRoot(scope),
            containerColor = Oe.Sheet,
            title = {
                PadDialogWindow(scope, opener)
                Text(title, style = Type.Title.copy(fontSize = 20.sp))
            },
            text = { Text(text, style = Type.Small) },
            confirmButton = {
                TextButton(onConfirm, Modifier.gamepadFocus(corner = 20.dp)) { Text(confirm, style = Type.Section.copy(color = Oe.Danger)) }
            },
            dismissButton = {
                TextButton(onDismissRequest, Modifier.gamepadFocus(corner = 20.dp)) { Text(dismiss, style = Type.Section) }
            },
        )
    }
}
