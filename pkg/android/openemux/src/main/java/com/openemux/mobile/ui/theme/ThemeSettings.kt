package com.openemux.mobile.ui.theme

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.theme.ThemeBase
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.ActiveTheme
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.LocalActions
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OeConfirmDialog
import com.openemux.mobile.ui.OeSheet
import com.openemux.mobile.ui.OeTextField
import com.openemux.mobile.ui.PrimaryButton
import com.openemux.mobile.ui.Routes
import com.openemux.mobile.ui.SecondaryButton
import com.openemux.mobile.ui.SheetBarIcons
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.gamepadFocus
import com.openemux.mobile.ui.themed
import com.retroarch.R
import kotlinx.coroutines.launch

/** "Light base" or "Dark base". */
@Composable
internal fun baseName(base: String) = stringResource(if (base == ThemeBase.DARK) R.string.theme_base_dark else R.string.theme_base_light)

/**
 * The user's themes under the built-in ones (issue 1018): each with its swatches, Edit and a menu (Duplicate, Rename,
 * Share, Save, Delete), then New theme and Import theme file. [background] is the rows' fill where they sit;
 * [onLeave] runs before the editor opens (the phone's sheet closes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserThemes(background: Color, onLeave: () -> Unit = {}) {
    val context = LocalContext.current
    val nav = LocalActions.current.nav
    val scope = rememberCoroutineScope()
    val store = remember { ActiveTheme.store(context) }
    // Bumped after each change on disk, so the list reads it again.
    var version by remember { mutableIntStateOf(0) }
    val themes = remember(version) { store.list() }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf<UserTheme?>(null) }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            ThemeFiles.finishImport(context, ThemeFiles.import(context, uri))
            version++
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemeFiles.MIME)) { uri ->
        val theme = saving
        saving = null
        if (uri != null && theme != null) scope.launch {
            val ok = ThemeFiles.save(context, theme, uri)
            Toast.makeText(context, context.getString(if (ok) R.string.theme_saved_file else R.string.theme_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun edit(id: String) {
        onLeave()
        nav.navigate(Routes.themeEdit(id))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.theme_section_mine).uppercase(), style = Type.SectionCaps, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        themes.forEach { theme ->
            ThemeRow(theme, ThemePref.value == theme.id, background, onPick = { ThemePref.set(context, theme.id) }, onEdit = { edit(theme.id) }, onMore = { menuFor = theme.id })
        }
        ActionRow(Icons.Plus, stringResource(R.string.theme_new), background) { creating = true }
        ActionRow(Icons.Download, stringResource(R.string.theme_import), background) {
            runCatching { open.launch(arrayOf("*/*")) }
        }
    }

    val menuTheme = themes.firstOrNull { it.id == menuFor }
    if (menuTheme != null) {
        OeSheet({ menuFor = null }) {
            SheetBarIcons()
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(menuTheme.name, style = Type.Title.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
                MenuItem(Icons.Copy, stringResource(R.string.theme_duplicate)) {
                    menuFor = null
                    val copy = store.duplicate(menuTheme.id, store.freeName(context.getString(R.string.theme_copy_name, menuTheme.name)))
                    ThemePref.set(context, copy.id)
                    version++
                }
                MenuItem(Icons.Pencil, stringResource(R.string.theme_rename)) { menuFor = null; renaming = menuTheme.id }
                MenuItem(Icons.Share, stringResource(R.string.theme_share)) {
                    menuFor = null
                    scope.launch { runCatching { ThemeFiles.share(context, ThemeFiles.exportToCache(context, menuTheme)) } }
                }
                MenuItem(Icons.Save, stringResource(R.string.theme_save_file)) {
                    menuFor = null
                    saving = menuTheme
                    runCatching { create.launch(ThemeFiles.fileName(menuTheme)) }
                }
                MenuItem(Icons.Trash, stringResource(R.string.theme_delete), Oe.Danger) { menuFor = null; deleting = menuTheme.id }
            }
        }
    }

    themes.firstOrNull { it.id == renaming }?.let { theme ->
        NameSheet(stringResource(R.string.theme_rename), theme.name, stringResource(R.string.theme_rename_save), onClose = { renaming = null }) { name ->
            store.rename(theme.id, store.freeName(name, except = theme.id))
            if (ThemePref.value == theme.id) ActiveTheme.load(context, theme.id)
            renaming = null
            version++
        }
    }

    themes.firstOrNull { it.id == deleting }?.let { theme ->
        val active = ThemePref.value == theme.id
        OeConfirmDialog(
            onDismissRequest = { deleting = null },
            title = stringResource(R.string.theme_delete_title, theme.name),
            text = stringResource(R.string.theme_delete_body) + if (active) " " + stringResource(R.string.theme_delete_active) else "",
            confirm = stringResource(R.string.theme_delete),
            onConfirm = {
                store.delete(theme.id)
                if (active) ThemePref.set(context, ThemePref.SYSTEM)
                deleting = null
                version++
            },
            dismiss = stringResource(R.string.theme_cancel),
        )
    }

    if (creating) {
        NewThemeSheet(onClose = { creating = false }) { name, base ->
            creating = false
            onLeave()
            nav.navigate(Routes.themeNew(name, base))
        }
    }
}

/** One user theme: the radio dot, three swatches (top bar, card, accent), its name and base, Edit and the menu. */
@Composable
private fun ThemeRow(theme: UserTheme, selected: Boolean, background: Color, onPick: () -> Unit, onEdit: () -> Unit, onMore: () -> Unit) {
    val shape = RoundedCornerShape(16.dp.themed)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .gamepadFocus(corner = 16.dp.themed, background = if (selected) Oe.AccentTint else background).clip(shape)
            .border(1.5.dp, if (selected) Oe.AccentBright else Oe.Border, shape)
            .clickable(role = Role.RadioButton, onClick = onPick).semantics { this.selected = selected }
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Oe.AccentBright else Oe.Chevron, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Oe.AccentBright))
        }
        Row(Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, Oe.Border, RoundedCornerShape(5.dp))) {
            listOf("headerbar_bg", "card_bg", "accent").forEach { key ->
                Box(Modifier.size(width = 12.dp, height = 28.dp).background(Color(theme.color(key))))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(theme.name, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(baseName(theme.base), style = Type.Small)
        }
        IconAction(Icons.Pencil, stringResource(R.string.theme_edit), onEdit)
        IconAction(Icons.Dots, stringResource(R.string.theme_more, theme.name), onMore)
    }
}

@Composable
internal fun IconAction(icon: String, label: String, onClick: () -> Unit, tint: Color = Oe.Muted, enabled: Boolean = true) {
    Box(
        Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (enabled) tint else tint.copy(alpha = 0.35f), 20.dp) }
}

@Composable
private fun ActionRow(icon: String, label: String, background: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).gamepadFocus(corner = 16.dp.themed, background = background).clip(RoundedCornerShape(16.dp.themed))
            .background(background).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, Oe.AccentText, 20.dp)
        Text(label, style = Type.BodyMedium.copy(color = Oe.AccentText))
    }
}

@Composable
private fun MenuItem(icon: String, label: String, color: Color = Oe.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).gamepadFocus(corner = 12.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, color, 20.dp)
        Text(label, style = Type.Body.copy(color = color))
    }
}

/** A name to type, for Rename (and the first half of New theme). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(title: String, initial: String, action: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val clean = UserTheme.cleanName(name)
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Type.Title.copy(fontSize = 22.sp))
            OeTextField(
                stringResource(R.string.theme_name), name, { name = it.take(UserTheme.MAX_NAME_LENGTH) }, Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (clean.isNotEmpty()) onSave(clean) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.theme_cancel), onClose, Modifier.weight(1f))
                PrimaryButton(action, { onSave(clean) }, Modifier.weight(1f), enabled = clean.isNotEmpty())
            }
        }
    }
}

/** New theme: a name and a base, Light or Dark (the one on screen now picked first). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewThemeSheet(onClose: () -> Unit, onCreate: (String, String) -> Unit) {
    val default = stringResource(R.string.theme_default_name)
    var name by rememberSaveable { mutableStateOf(default) }
    var base by rememberSaveable { mutableStateOf(if (Oe.dark) ThemeBase.DARK else ThemeBase.LIGHT) }
    val clean = UserTheme.cleanName(name)
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme_new), style = Type.Title.copy(fontSize = 22.sp))
            OeTextField(stringResource(R.string.theme_name), name, { name = it.take(UserTheme.MAX_NAME_LENGTH) }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.theme_base), style = Type.Caption)
            Segmented(
                listOf(ThemeBase.LIGHT to stringResource(R.string.theme_base_light_short), ThemeBase.DARK to stringResource(R.string.theme_base_dark_short)),
                base, Modifier.fillMaxWidth(),
            ) { base = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.theme_cancel), onClose, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.theme_create), { onCreate(clean, base) }, Modifier.weight(1f), enabled = clean.isNotEmpty())
            }
        }
    }
}

/** A row of choices, one picked (the theme editor's and New theme's). */
@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, value: T, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(Oe.Surface2).padding(3.dp)) {
        options.forEach { (option, label) ->
            val on = option == value
            Box(
                Modifier.weight(1f).height(38.dp).gamepadFocus(corner = 9.dp, background = if (on) Oe.AccentSoft else Color.Transparent).clip(RoundedCornerShape(9.dp))
                    .clickable(role = Role.RadioButton) { onPick(option) }.semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Small.copy(color = if (on) Oe.AccentOnSoft else Oe.Text, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
