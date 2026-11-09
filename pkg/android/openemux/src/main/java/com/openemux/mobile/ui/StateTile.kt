package com.openemux.mobile.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroarch.R
import java.io.File

/** Behind the slot tag and the three dots: a grey that also shows on a black bar, where a plain scrim would vanish. */
private val ChipBackground = Color(0xCC2A2A33)

/**
 * A saved state wherever states are listed: its whole picture in a 3:2 tile, the slot over it, the three-dots button,
 * and its age under it. [slot] is negative for the auto state; [ago] tints the age (white over the game). A press and
 * hold and the button both call [onOptions]; [menu] is composed at the button, for a menu anchored there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StateTile(
    slot: Int, thumb: File?, modified: Long, modifier: Modifier = Modifier, ago: Color? = null,
    onClick: () -> Unit, onOptions: () -> Unit, menu: @Composable () -> Unit = {},
) {
    val tag = if (slot < 0) stringResource(R.string.game_state_auto) else stringResource(R.string.game_state_slot, slot)
    val label = stringResource(R.string.game_state_options, tag)
    // The button sits inside the tile, so a spatial search from the tile never finds it: Right goes to it and Left
    // comes back, and from the button Right goes on to the next tile (as on a skin card).
    val tileFocus = remember { FocusRequester() }
    val optionsFocus = remember { FocusRequester() }
    Column(
        Modifier.gamepadFocus(corner = 10.dp).then(modifier)
            .focusRequester(tileFocus).focusProperties { right = optionsFocus }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onOptions),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            // The modified time too: an overwritten slot keeps its file name, and would keep showing the old picture.
            val bmp = remember(thumb, modified) { thumb?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(ChipBackground).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(tag, style = Type.Tag.copy(color = Color.White, fontSize = 10.sp))
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                Box(
                    Modifier.size(40.dp).gamepadFocus(corner = 20.dp).clip(CircleShape)
                        .focusRequester(optionsFocus).focusProperties { left = tileFocus }
                        .clickable(onClickLabel = label, role = Role.Button, onClick = onOptions).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(ChipBackground), contentAlignment = Alignment.Center) {
                        Icon(Icons.Dots, Color.White, 18.dp)
                    }
                }
                menu()
            }
        }
        Text(formatAgo(modified), style = if (ago != null) Type.Caption.copy(color = ago) else Type.Caption, modifier = Modifier.padding(top = 5.dp, start = 2.dp))
    }
}

/** A state's options, as a popup the pad drives on its own: for a window that gets the pad's keys, unlike the pause sheet. */
@Composable
fun StateOptionsMenu(open: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val opener = LocalPadScope.current
    // Not keyed on [open]: a new scope while the menu animates out would land focus in the closing popup.
    val menuScope = remember { PadScope() }
    CompositionLocalProvider(LocalPadScope provides menuScope) {
        DropdownMenu(open, onDismiss, Modifier.padPopup(menuScope, opener, onDismiss), containerColor = Oe.Surface2) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.game_state_delete), style = Type.Body.copy(color = Oe.Danger)) },
                leadingIcon = { Icon(Icons.Trash, Oe.Danger, 20.dp) },
                modifier = Modifier.gamepadFocus(corner = 0.dp, inset = true),
                onClick = { onDismiss(); onDelete() },
            )
        }
    }
}

/** What deleting the state in [slot] removes, for the confirmation; negative for the auto state. */
@Composable
fun stateDeleteBody(slot: Int): String =
    if (slot < 0) stringResource(R.string.game_state_delete_body_auto) else stringResource(R.string.game_state_delete_body, slot)

/** Asks before deleting the state in [slot]; focus lands on Cancel. */
@Composable
fun DeleteStateDialog(slot: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    OeConfirmDialog(
        onDismiss, stringResource(R.string.game_state_delete_title), stateDeleteBody(slot),
        stringResource(R.string.game_state_delete), { onDismiss(); onConfirm() }, stringResource(R.string.detail_cancel),
    )
}
