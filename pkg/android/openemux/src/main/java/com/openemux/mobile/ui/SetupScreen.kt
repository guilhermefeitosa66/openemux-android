package com.openemux.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.Setup
import com.retroarch.R
import java.util.Locale

/**
 * The emulator downloads on the shelf (issue 922): a pill while anything is left to fetch (a core comes down the
 * first time its console is played; "Download all" fetches the rest), and the sheet it opens.
 */
@Composable
fun SetupPill(modifier: Modifier = Modifier) {
    val app = LocalActions.current.app
    val state by app.setup.state.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val running = state as? Setup.State.Running
    val failed = state as? Setup.State.Failed
    val idle = state as? Setup.State.Idle
    // How many cores are missing is counted off the main thread, once the pill is on screen.
    LaunchedEffect(idle?.missing) { if (idle != null && idle.missing < 0) withContext(Dispatchers.IO) { app.setup.refresh() } }
    val shown = state !is Setup.State.Done && (idle == null || idle.missing != 0)
    AnimatedVisibility(shown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val text = when {
            running != null -> stringResource(R.string.settings_setup_pill_running, running.done, running.total)
            failed != null -> stringResource(R.string.settings_setup_pill_failed)
            else -> stringResource(R.string.settings_setup_pill_idle)
        }
        Box(
            modifier.fillMaxWidth().heightIn(min = 44.dp).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp))
                .background(if (failed != null) Oe.WarnBg else Oe.Surface)
                .border(1.dp, if (failed != null) Oe.Warn else Oe.Border, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { sheet = true },
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running != null) CircularProgressIndicator(Modifier.size(18.dp), color = Oe.AccentBright, trackColor = Oe.Raised, strokeWidth = 2.dp)
                else Icon(if (failed != null) Icons.Alert else Icons.Download, if (failed != null) Oe.Warn else Oe.AccentText, 18.dp)
                Text(text, style = Type.BodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(Icons.Chevron, Oe.Chevron, 18.dp)
            }
            if (running != null && running.total > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(running.done.toFloat() / running.total).height(3.dp).background(Oe.AccentBright))
            }
        }
    }
    if (sheet) SetupSheet(state, onStart = { app.startSetup() }) { sheet = false }
}

/** The "Download all emulators" row's second line in the settings (issue 922). */
@Composable
fun setupSummary(state: Setup.State): String = when (state) {
    is Setup.State.Running -> stringResource(R.string.settings_setup_pill_running, state.done, state.total)
    is Setup.State.Failed -> state.message()
    Setup.State.Done -> stringResource(R.string.settings_setup_all_done)
    is Setup.State.Idle -> stringResource(R.string.settings_setup_all_desc)
}

/** What the downloads are, how far they are, and the way to fetch everything now. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(state: Setup.State, onStart: () -> Unit, onClose: () -> Unit) {
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SetupIntro(wide = false)
            SetupProgress(state, onStart)
            SecondaryButton(stringResource(R.string.settings_setup_later), onClose, Modifier.fillMaxWidth().padding(top = 10.dp).gamepadFocus(corner = 26.dp))
        }
    }
}

/** The logo, the title and what is about to happen. */
@Composable
private fun SetupIntro(wide: Boolean) {
    Image(painterResource(R.drawable.oe_logo), contentDescription = null, modifier = Modifier.height(56.dp))
    Text(
        stringResource(R.string.settings_setup_title),
        style = if (wide) Type.Display.copy(fontSize = 40.sp, lineHeight = 44.sp) else Type.Display.copy(fontSize = 28.sp, lineHeight = 32.sp),
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        stringResource(R.string.settings_setup_intro),
        style = if (wide) Type.Small.copy(fontSize = 17.sp, lineHeight = 24.sp) else Type.Small.copy(fontSize = 15.sp, lineHeight = 21.sp),
        modifier = Modifier.padding(top = 10.dp).then(if (wide) Modifier.widthIn(max = 440.dp) else Modifier),
    )
}

/** The steps, the progress, what went wrong and the button. */
@Composable
private fun SetupProgress(state: Setup.State, onStart: () -> Unit) {
    val running = state as? Setup.State.Running
    val failed = state as? Setup.State.Failed
    val done = running?.done ?: failed?.done ?: 0
    val total = running?.total ?: failed?.total ?: 0
    val idle = state as? Setup.State.Idle

    Card(Modifier.padding(top = 28.dp)) {
        Setup.Step.entries.forEachIndexed { i, step ->
            val current = running?.step ?: failed?.step
            val status = if (current == null) 1 else step.ordinal.compareTo(current.ordinal)
            StepRow(stringResource(step.label), status, failed = failed != null && status == 0, divider = i > 0)
        }
    }
    if (idle != null && idle.missing > 0) {
        Text(stringResource(R.string.settings_setup_missing, idle.missing), style = Type.Caption, modifier = Modifier.padding(top = 12.dp))
    }

    if (running != null || failed != null) {
        val fraction by animateFloatAsState(if (total > 0) done.toFloat() / total else 0f, label = "setup")
        Row(Modifier.padding(top = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_setup_percent, (fraction * 100).toInt()), style = Type.Section, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.settings_count_of, done, total), style = Type.Caption)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Oe.Raised)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(if (failed != null) Oe.Warn else Oe.AccentBright))
        }
        if (running != null) {
            Text(
                stringResource(R.string.settings_setup_downloading, running.itemName()) + if (running.bytes > 0) " · ${megabytes(running.bytes)}" else "",
                style = Type.Caption, modifier = Modifier.padding(top = 8.dp),
            )
            Text(stringResource(R.string.settings_setup_background_note), style = Type.Caption.copy(color = Oe.Faint), modifier = Modifier.padding(top = 2.dp))
        }
    }

    if (failed != null) {
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.WarnBg).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Alert, Oe.Warn, 22.dp)
            Column {
                Text(failed.message(), style = Type.BodyMedium.copy(color = Oe.WarnText))
                Text(stringResource(R.string.settings_setup_resume_note), style = Type.Small, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.settings_setup_source), style = Type.Caption.copy(color = Oe.Faint), modifier = Modifier.padding(top = 20.dp))
    if (running == null && state !is Setup.State.Done) {
        PrimaryButton(
            stringResource(if (failed != null) R.string.settings_setup_retry else R.string.settings_setup_all), onStart,
            Modifier.fillMaxWidth().padding(top = 24.dp).gamepadFocus(corner = 26.dp)
                .restorableFocus("start", androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }, initial = true),
            icon = Icons.Download,
        )
    }
}

/** [status]: below 0 done, 0 running, above 0 waiting. */
@Composable
private fun StepRow(label: String, status: Int, failed: Boolean, divider: Boolean) {
    if (divider) androidx.compose.material3.HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                status < 0 -> Box(Modifier.size(24.dp).clip(CircleShape).background(Oe.OkBg), contentAlignment = Alignment.Center) { Icon(Icons.Check, Oe.Ok, 14.dp, stroke = 2.6f) }
                failed -> Icon(Icons.Alert, Oe.Warn, 22.dp)
                status == 0 -> CircularProgressIndicator(Modifier.size(20.dp), color = Oe.AccentBright, trackColor = Oe.Raised, strokeWidth = 2.5.dp)
                else -> Box(Modifier.size(20.dp).clip(CircleShape).background(Oe.Raised))
            }
        }
        Text(label, style = Type.Body.copy(color = if (status > 0) Oe.Muted else Oe.Text), textAlign = TextAlign.Start)
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
