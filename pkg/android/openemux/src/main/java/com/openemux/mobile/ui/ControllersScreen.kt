package com.openemux.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroarch.R

/** The interface modes in the order the screen lists them: Automatic, Touch screen, Controller (issue 873). */
internal val INTERFACE_MODES = listOf(
    Triple(ControllerLayoutPref.AUTO, R.string.settings_interface_mode_auto, R.string.settings_interface_mode_auto_desc),
    Triple(ControllerLayoutPref.NEVER, R.string.settings_interface_mode_touch, R.string.settings_interface_mode_touch_desc),
    Triple(ControllerLayoutPref.ALWAYS, R.string.settings_interface_mode_controller, R.string.settings_interface_mode_controller_desc),
)

internal val BUTTON_LABELS = listOf(
    Triple(ButtonLabelsKeys.AUTO, R.string.settings_button_labels_auto, R.string.settings_button_labels_auto_desc),
    Triple(ButtonLabelsKeys.XBOX, R.string.settings_button_labels_xbox, R.string.settings_button_labels_xbox_desc),
    Triple(ButtonLabelsKeys.NINTENDO, R.string.settings_button_labels_nintendo, R.string.settings_button_labels_nintendo_desc),
    Triple(ButtonLabelsKeys.PLAYSTATION, R.string.settings_button_labels_playstation, R.string.settings_button_labels_playstation_desc),
)

/** The settings list's summary of this screen: the interface mode and the button labels picked now. */
@Composable
internal fun controllersSummary(): String =
    stringResource(INTERFACE_MODES.first { it.first == ControllerLayoutPref.value }.second) + " · " +
        stringResource(BUTTON_LABELS.first { it.first == ButtonLabelsPref.value }.second)

/**
 * Settings › Controllers (issue 873): every setting about physical controllers on one screen, a screen of its own on a
 * phone and the pane beside the list on a tablet. Both choices apply at once.
 */
@Composable
fun ControllersScreen(embedded: Boolean = false) {
    val a = LocalActions.current
    val context = LocalContext.current
    if (!embedded) {
        GamepadButtons(hints = listOf("A" to stringResource(R.string.settings_hint_choose), "B" to stringResource(R.string.settings_hint_back)))
        ReturnToSplit(LocalWindowLayout.current.splitSettings, Routes.SETTINGS, SettingsItem.Controllers.key)
    }
    MarginFocusScroll(trailing = FocusMargin + hintBarInset) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp)) {
                if (!embedded) BackLink(stringResource(R.string.settings_title)) { a.nav.popBackStack() }
                FitText(stringResource(R.string.settings_controllers), Type.Display, Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.settings_controllers_lead), style = Type.Small.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 8.dp))
            }

            SectionHeader(stringResource(R.string.settings_interface_mode))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_interface_mode_lead), style = Type.Small, modifier = Modifier.padding(bottom = 4.dp))
                INTERFACE_MODES.forEach { (id, name, blurb) ->
                    RadioCard(ControllerLayoutPref.value == id, stringResource(name), stringResource(blurb), onClick = { ControllerLayoutPref.set(context, id) })
                }
            }

            SectionHeader(stringResource(R.string.settings_button_labels))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_button_labels_lead), style = Type.Small, modifier = Modifier.padding(bottom = 4.dp))
                BUTTON_LABELS.forEach { (id, name, blurb) ->
                    RadioCard(
                        ButtonLabelsPref.value == id, stringResource(name), stringResource(blurb),
                        onClick = { ButtonLabelsPref.set(context, id) },
                        leading = { FaceDiamond(effectiveFamily(id, PadLabels.detected)) },
                    )
                }
            }
            Spacer(Modifier.height(28.dp + hintBarInset))
        }
    }
}
