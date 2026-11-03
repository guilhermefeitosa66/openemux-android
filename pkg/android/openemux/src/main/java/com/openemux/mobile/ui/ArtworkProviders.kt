package com.openemux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.ArtKind
import com.openemux.mobile.data.ArtProvider
import com.openemux.mobile.data.ProviderSetting
import com.openemux.mobile.data.ScreenScraperAccount
import com.openemux.mobile.data.SsAccountReply
import com.retroarch.R
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Settings › Artwork providers (issue 753): the order the search asks the providers in, which of
 * them it and the sync may use, and the optional ScreenScraper accounts.
 */
@Composable
fun ArtworkProvidersScreen(embedded: Boolean = false) {
    if (!embedded) {
        GamepadButtons(hints = listOf("A" to stringResource(R.string.settings_hint_choose), "B" to stringResource(R.string.settings_hint_back)))
        ReturnToSplit(LocalWindowLayout.current.splitSettings, Routes.SETTINGS, SettingsItem.ArtworkProviders.key)
    }
    val a = LocalActions.current
    val store = a.app.providers
    val settings by store.settings.collectAsState()

    MarginFocusScroll(trailing = FocusMargin + hintBarInset) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp)) {
                if (!embedded) BackLink(stringResource(R.string.settings_title)) { a.nav.popBackStack() }
                FitText(stringResource(R.string.providers_title), Type.Display, Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.providers_lead), style = Type.Small.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 8.dp))
            }
            SectionHeader(stringResource(R.string.providers_order))
            Card(Modifier.padding(horizontal = 20.dp)) {
                settings.order.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
                    ProviderRow(
                        s, first = i == 0, last = i == settings.order.lastIndex,
                        onMove = { step -> store.update { it.move(s.provider, step) } },
                        onEnabled = { on -> store.update { it.setEnabled(s.provider, on) } },
                        onKind = { kind, on -> store.update { it.setKind(s.provider, kind, on) } },
                    )
                }
            }
            AccountSection()
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.height(hintBarInset))
        }
    }
}

@Composable
private fun ProviderRow(
    setting: ProviderSetting,
    first: Boolean,
    last: Boolean,
    onMove: (Int) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onKind: (ArtKind, Boolean) -> Unit,
) {
    val name = providerName(setting.provider.id)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            MoveButton(Icons.ChevronUp, stringResource(R.string.providers_move_up, name), enabled = !first) { onMove(-1) }
            MoveButton(Icons.ChevronDown, stringResource(R.string.providers_move_down, name), enabled = !last) { onMove(1) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = Type.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                setting.provider.kinds.sortedBy { it.ordinal }.forEach { kind ->
                    val label = stringResource(if (kind == ArtKind.COVER) R.string.providers_kind_covers else R.string.providers_kind_labels)
                    // Only a provider with more than one kind has a choice to make; the others show what they serve.
                    KindChip(label, kind in setting.kinds, toggles = setting.provider.kinds.size > 1, enabled = setting.enabled) { onKind(kind, it) }
                }
            }
        }
        Box(
            Modifier.gamepadFocus(corner = 18.dp).clip(RoundedCornerShape(18.dp))
                .toggleable(setting.enabled, role = Role.Switch, onValueChange = onEnabled)
                .semantics { contentDescription = name }
                .padding(4.dp),
        ) {
            Switch(
                setting.enabled, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = Oe.Accent, checkedBorderColor = Oe.Accent,
                    uncheckedThumbColor = Oe.Chevron, uncheckedTrackColor = Oe.Raised, uncheckedBorderColor = Oe.Chevron,
                ),
            )
        }
    }
}

@Composable
private fun MoveButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(padSize(36.dp, 40.dp)).let { if (enabled) it.gamepadFocus(corner = 18.dp) else it }.clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, Oe.Muted, 18.dp) }
}

@Composable
private fun KindChip(label: String, on: Boolean, toggles: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.let { if (toggles && enabled) it.gamepadFocus(corner = 999.dp).clip(shape).toggleable(on, role = Role.Checkbox, onValueChange = onChange) else it.clip(shape) }
            .background(if (on && toggles) Oe.AccentSoft else Oe.Surface2).border(1.dp, Oe.Border, shape)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 10.dp, vertical = padSize(3.dp, 6.dp)),
    ) {
        Text(label, style = Type.Caption.copy(fontSize = 12.sp, color = if (on && toggles) Oe.AccentOnSoft else Oe.Muted))
    }
}

/** What the last check said, as the account card shows it. */
private data class CheckMessage(val text: String, val ok: Boolean)

@Composable
private fun AccountSection() {
    val a = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts = a.app.accounts
    val saved by accounts.account.collectAsState()
    var user by remember(saved.user) { mutableStateOf(saved.user) }
    // Not saveable: a password must not reach the saved-instance bundle.
    var password by remember(saved.password) { mutableStateOf(saved.password) }
    var devId by remember(saved.devId) { mutableStateOf(saved.devId) }
    var devPassword by remember(saved.devPassword) { mutableStateOf(saved.devPassword) }
    var revealed by remember { mutableStateOf(false) }
    var developerOpen by rememberSaveable { mutableStateOf(saved.hasDeveloper) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckMessage?>(null) }
    val embedded = a.app.screenScraper.embedded

    fun check(account: ScreenScraperAccount) {
        if (checking) return
        accounts.save(account)
        checking = true
        result = null
        scope.launch {
            val reply = try { a.app.screenScraper.check() } finally { checking = false }
            val number = NumberFormat.getIntegerInstance()
            result = when (reply) {
                is SsAccountReply.User -> CheckMessage(
                    if (reply.used != null && reply.max != null) context.getString(R.string.providers_signed_in_quota, number.format(reply.used), number.format(reply.max))
                    else context.getString(R.string.providers_signed_in, reply.name),
                    ok = true,
                )
                SsAccountReply.Developer -> CheckMessage(context.getString(R.string.providers_developer_ok), ok = true)
                SsAccountReply.Refused -> CheckMessage(context.getString(R.string.artwork_msg_refused), ok = false)
                SsAccountReply.NoCredentials -> CheckMessage(context.getString(R.string.artwork_msg_no_credentials), ok = false)
                SsAccountReply.Quota -> CheckMessage(context.getString(R.string.artwork_msg_quota), ok = false)
                is SsAccountReply.Unreachable -> CheckMessage(
                    context.getString(if (reply.network) R.string.artwork_msg_offline else R.string.artwork_msg_ss_unreachable), ok = false,
                )
            }
        }
    }

    SectionHeader(stringResource(R.string.providers_account))
    Card(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (embedded) R.string.providers_account_intro else R.string.providers_account_intro_none),
                style = Type.Small.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
            OeTextField(
                stringResource(R.string.providers_user), user, { user = it }, readOnly = checking, contentType = ContentType.Username,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
            OeTextField(
                stringResource(R.string.providers_password), password, { password = it }, readOnly = checking, password = true, revealed = revealed,
                onReveal = { revealed = !revealed }, revealLabel = stringResource(if (revealed) R.string.ra_hide_password else R.string.ra_show_password),
                contentType = ContentType.Password,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            )
            result?.let { CheckBanner(it) }
            PrimaryButton(
                stringResource(if (checking) R.string.providers_checking else R.string.providers_save_check),
                { check(saved.copy(user = user, password = password)) }, Modifier.fillMaxWidth(), height = 50.dp, enabled = !checking,
            )
            if (saved.user.isNotEmpty() || saved.password.isNotEmpty()) {
                SecondaryButton(stringResource(R.string.providers_remove_account), {
                    user = ""
                    password = ""
                    result = null
                    accounts.save(saved.copy(user = "", password = ""))
                }, Modifier.fillMaxWidth(), height = 48.dp, busy = checking)
            }
        }
    }

    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth().height(52.dp).gamepadFocus(corner = 14.dp).clip(RoundedCornerShape(14.dp))
            .background(Oe.Surface).border(1.dp, Oe.Border, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { developerOpen = !developerOpen }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.providers_developer), style = Type.Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        Icon(if (developerOpen) Icons.ChevronUp else Icons.ChevronDown, Oe.Chevron, 16.dp)
    }
    if (developerOpen) {
        Card(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.providers_developer_intro), style = Type.Small.copy(fontSize = 13.sp, lineHeight = 18.sp))
                OeTextField(
                    stringResource(R.string.providers_dev_id), devId, { devId = it }, readOnly = checking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                )
                OeTextField(
                    stringResource(R.string.providers_dev_password), devPassword, { devPassword = it }, readOnly = checking, password = true, revealed = revealed,
                    onReveal = { revealed = !revealed }, revealLabel = stringResource(if (revealed) R.string.ra_hide_password else R.string.ra_show_password),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
                PrimaryButton(
                    stringResource(if (checking) R.string.providers_checking else R.string.providers_save_check),
                    { check(saved.copy(devId = devId, devPassword = devPassword)) }, Modifier.fillMaxWidth(), height = 50.dp, enabled = !checking,
                )
                if (saved.devId.isNotEmpty() || saved.devPassword.isNotEmpty()) {
                    SecondaryButton(stringResource(R.string.providers_remove_developer), {
                        devId = ""
                        devPassword = ""
                        result = null
                        accounts.save(saved.copy(devId = "", devPassword = ""))
                    }, Modifier.fillMaxWidth(), height = 48.dp, busy = checking)
                }
            }
        }
    }
}

@Composable
private fun CheckBanner(message: CheckMessage) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (message.ok) Oe.OkBg else Oe.DangerBg)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (message.ok) Icons.Check else Icons.Alert, if (message.ok) Oe.Ok else Oe.Danger, 18.dp)
        Text(message.text, style = Type.Body.copy(fontSize = 14.sp, color = if (message.ok) Oe.OkText else Oe.Danger))
    }
}
