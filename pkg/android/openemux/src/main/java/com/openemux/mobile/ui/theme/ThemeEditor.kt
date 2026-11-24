package com.openemux.mobile.ui.theme

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.openemux.mobile.data.SkinColorMath
import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinPalettes
import com.openemux.mobile.data.ArtImages
import com.openemux.mobile.data.theme.ThemeDensity
import com.openemux.mobile.data.theme.ThemeDraft
import com.openemux.mobile.data.theme.ThemeFit
import com.openemux.mobile.data.theme.ThemeGroup
import com.openemux.mobile.data.theme.ThemeKey
import com.openemux.mobile.data.theme.ThemeKeys
import com.openemux.mobile.data.theme.ThemeKind
import com.openemux.mobile.data.theme.ThemeSection
import com.openemux.mobile.data.theme.UserTheme
import com.openemux.mobile.ui.ActiveTheme
import com.openemux.mobile.ui.GamepadButtons
import com.openemux.mobile.ui.HexField
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.LocalActions
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OeConfirmDialog
import com.openemux.mobile.ui.OeSheet
import com.openemux.mobile.ui.PREF_PALETTE
import com.openemux.mobile.ui.PREF_RECENT
import com.openemux.mobile.ui.PaletteList
import com.openemux.mobile.ui.PaletteRow
import com.openemux.mobile.ui.PanelBg
import com.openemux.mobile.ui.PrimaryButton
import com.openemux.mobile.ui.SecondaryButton
import com.openemux.mobile.ui.Swatch
import com.openemux.mobile.ui.TextOff
import com.openemux.mobile.ui.TextOn
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.ColorPicker
import com.openemux.mobile.ui.gamepadFocus
import com.openemux.mobile.ui.paletteName
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * The theme editor (issue 1018): [id] edits a saved theme, else a new one named [name] on [base]. Nothing is written
 * until Save, which selects the theme; Cancel (or Back, after a confirm when something changed) drops everything.
 */
@Composable
fun ThemeEditorScreen(id: String?, name: String?, base: String?) {
    val context = LocalContext.current
    val nav = LocalActions.current.nav
    val scope = rememberCoroutineScope()
    val store = remember { ActiveTheme.store(context) }
    val staging = remember { File(context.cacheDir, "theme-edit").apply { deleteRecursively(); mkdirs() } }
    val folder = remember(id) { id?.takeIf { UserTheme.isCustomId(it) }?.let { File(store.root, it) } }
    var draft by remember {
        mutableStateOf(store.get(id)?.let(ThemeDraft::edit) ?: ThemeDraft.new(name ?: "", base ?: com.openemux.mobile.data.theme.ThemeBase.DARK))
    }
    var details by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var colorFor by remember { mutableStateOf<String?>(null) }
    var imageFor by remember { mutableStateOf<String?>(null) }

    fun close() {
        staging.deleteRecursively()
        nav.popBackStack()
    }

    var saving by remember { mutableStateOf(false) }
    fun save() {
        if (saving) return
        saving = true
        val d = draft
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { if (d.isNew) store.createFrom(d.theme, store.freeName(d.theme.name), d.staged) else store.save(d.theme, d.staged) }.getOrNull()
            }
            saving = false
            if (saved == null) Toast.makeText(context, R.string.theme_save_failed, Toast.LENGTH_LONG).show()
            else {
                ThemePref.set(context, saved.id)
                close()
            }
        }
    }

    fun cancel() = if (draft.dirty && !(draft.isNew && draft.theme.values.isEmpty())) leaving = true else close()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = imageFor
        imageFor = null
        if (uri != null && key != null) scope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = File(staging, "picked.tmp")
                    context.contentResolver.openInputStream(uri)?.use { i -> raw.outputStream().use { SkinImport.copyLimited(i, it, ArtImages.MAX_IMPORT_BYTES.toLong()) } }
                    store.stage(key, raw, staging).also { raw.delete() }
                }.getOrNull()
            }
            if (staged == null) Toast.makeText(context, R.string.theme_image_unreadable, Toast.LENGTH_LONG).show()
            else draft = draft.stageImage(key, staged.first, staged.second)
        }
    }

    BackHandler { cancel() }
    GamepadButtons(
        hints = listOf(
            "◀▶" to stringResource(R.string.theme_hint_change), "A" to stringResource(R.string.theme_hint_select),
            "B" to stringResource(R.string.theme_hint_back), "L1 R1" to stringResource(R.string.theme_hint_sections),
        ),
        l1 = { draft = draft.pick(ThemeSection.entries[(draft.section.ordinal - 1).coerceAtLeast(0)]) },
        r1 = { draft = draft.pick(ThemeSection.entries[(draft.section.ordinal + 1).coerceAtMost(ThemeSection.entries.lastIndex)]) },
        start = { save() },
    )

    val actions = EditorActions(
        onColor = { colorFor = it },
        onSet = { k, v -> draft = draft.set(k, v) },
        onReset = { draft = draft.reset(it) },
        onImage = { k -> imageFor = k; runCatching { pickImage.launch(arrayOf("image/png", "image/jpeg", "image/webp")) } },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Oe.Bg).statusBarsPadding().navigationBarsPadding()) {
        val wide = maxWidth > maxHeight && maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(draft, onCancel = ::cancel, onSave = ::save)
            if (wide) {
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(170.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemeSection.entries.forEach { SectionButton(it, draft, Modifier.fillMaxWidth()) { draft = draft.pick(it) } }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(stringResource(R.string.theme_reset_all), { resetting = true }, Modifier.fillMaxWidth(), compact = true)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Rows(draft, folder, actions)
                    }
                    ThemePreview(draft, folder, details, { details = !details }, Modifier.width(250.dp).fillMaxHeight())
                }
            } else {
                ThemePreview(draft, folder, details, { details = !details }, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(220.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeSection.entries.forEach { SectionButton(it, draft) { draft = draft.pick(it) } }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Rows(draft, folder, actions)
                    SecondaryButton(stringResource(R.string.theme_reset_all), { resetting = true }, Modifier.fillMaxWidth().padding(vertical = 14.dp))
                }
            }
        }
    }

    colorFor?.let { key ->
        ColorSheet(context, draft.theme.color(key), onDone = { colorFor = null }) { argb -> draft = draft.setColor(key, argb) }
    }
    if (leaving) {
        OeConfirmDialog(
            onDismissRequest = { leaving = false },
            title = stringResource(R.string.theme_discard_title),
            text = stringResource(R.string.theme_discard_body),
            confirm = stringResource(R.string.theme_discard),
            onConfirm = { leaving = false; close() },
            dismiss = stringResource(R.string.theme_keep_editing),
        )
    }
    if (resetting) {
        OeConfirmDialog(
            onDismissRequest = { resetting = false },
            title = stringResource(R.string.theme_reset_all_title),
            text = stringResource(R.string.theme_reset_all_body),
            confirm = stringResource(R.string.theme_reset_all),
            onConfirm = { resetting = false; draft = draft.resetAll() },
            dismiss = stringResource(R.string.theme_cancel),
        )
    }
}

private class EditorActions(
    val onColor: (String) -> Unit,
    val onSet: (String, Any) -> Unit,
    val onReset: (String) -> Unit,
    val onImage: (String) -> Unit,
)

@Composable
private fun TopBar(draft: ThemeDraft, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.heightIn(min = 44.dp).gamepadFocus(corner = 22.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.theme_cancel), style = Type.BodyMedium.copy(color = Oe.Muted)) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(if (draft.isNew) R.string.theme_editor_new else R.string.theme_editor_edit), style = Type.Section.copy(fontSize = 17.sp), maxLines = 1)
            Text(
                stringResource(R.string.theme_editor_subtitle, draft.theme.name, baseName(draft.theme.base)),
                style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        PrimaryButton(stringResource(R.string.theme_save), onSave, height = 44.dp, sidePadding = 20.dp)
    }
}

private fun sectionName(section: ThemeSection) = when (section) {
    ThemeSection.COLORS -> R.string.theme_section_colors
    ThemeSection.TEXT -> R.string.theme_section_text
    ThemeSection.ACCENT -> R.string.theme_section_accent
    ThemeSection.SHAPES -> R.string.theme_section_shapes
    ThemeSection.ICONS -> R.string.theme_section_icons
    ThemeSection.SIZES -> R.string.theme_section_sizes
    ThemeSection.BACKGROUNDS -> R.string.theme_section_backgrounds
}

private fun groupName(group: ThemeGroup) = when (group) {
    ThemeGroup.SURFACES -> R.string.theme_group_surfaces
    ThemeGroup.CONTENT -> R.string.theme_group_content
    ThemeGroup.NAVIGATION -> R.string.theme_group_navigation
    ThemeGroup.ACCENT -> R.string.theme_group_accent
    ThemeGroup.BORDERS -> R.string.theme_group_borders
    ThemeGroup.ICONS -> R.string.theme_group_icons
    ThemeGroup.TEXT_SIZE -> R.string.theme_group_text_size
    ThemeGroup.SPACING -> R.string.theme_group_spacing
    ThemeGroup.LIBRARY_IMAGE -> R.string.theme_group_library_image
    ThemeGroup.NAVIGATION_IMAGE -> R.string.theme_group_navigation_image
}

/** Each setting's label. */
internal fun keyName(key: String): Int = when (key) {
    "window_bg" -> R.string.theme_key_window_bg
    "headerbar_bg" -> R.string.theme_key_headerbar_bg
    "sidebar_bg" -> R.string.theme_key_sidebar_bg
    "library_bg" -> R.string.theme_key_library_bg
    "card_bg" -> R.string.theme_key_card_bg
    "console_list_bg" -> R.string.theme_key_console_list_bg
    "popover_bg" -> R.string.theme_key_popover_bg
    "details_bg" -> R.string.theme_key_details_bg
    "text_primary" -> R.string.theme_key_text_primary
    "text_secondary" -> R.string.theme_key_text_secondary
    "sidebar_text_primary" -> R.string.theme_key_sidebar_text_primary
    "sidebar_text_secondary" -> R.string.theme_key_sidebar_text_secondary
    "accent" -> R.string.theme_key_accent
    "selection_bg" -> R.string.theme_key_selection_bg
    "accent_button_bg" -> R.string.theme_key_accent_button_bg
    "accent_button_fg" -> R.string.theme_key_accent_button_fg
    "corner_radius" -> R.string.theme_key_corner_radius
    "border_width" -> R.string.theme_key_border_width
    "border_color" -> R.string.theme_key_border_color
    "shadow_color" -> R.string.theme_key_shadow_color
    "shadow_strength" -> R.string.theme_key_shadow_strength
    "icon_scale" -> R.string.theme_key_icon_scale
    "icon_color" -> R.string.theme_key_icon_color
    "font_scale" -> R.string.theme_key_font_scale
    "density" -> R.string.theme_key_density
    "grid_gap" -> R.string.theme_key_grid_gap
    "card_scale" -> R.string.theme_key_card_scale
    "library_image", "sidebar_image" -> R.string.theme_key_image
    "library_image_fit", "sidebar_image_fit" -> R.string.theme_key_fit
    "library_image_dim", "sidebar_image_dim" -> R.string.theme_key_dim
    else -> R.string.theme_key_blur
}

@Composable
private fun SectionButton(section: ThemeSection, draft: ThemeDraft, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val on = draft.section == section
    val warn = draft.hasWarning(section)
    val warnLabel = stringResource(R.string.theme_has_warning)
    Row(
        modifier.height(40.dp).gamepadFocus(corner = 20.dp, background = if (on) Oe.Accent else Color.Transparent).clip(RoundedCornerShape(20.dp))
            .then(if (on) Modifier.background(Oe.Accent) else Modifier.border(1.dp, Oe.Border, RoundedCornerShape(20.dp)))
            .clickable(role = Role.Tab, onClick = onClick).semantics { selected = on }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (warn) Box(Modifier.semantics { contentDescription = warnLabel }) { Icon(Icons.Warn, if (on) Oe.OnAccent else Oe.Warn, 14.dp) }
        Text(stringResource(sectionName(section)), style = Type.BodyMedium.copy(color = if (on) Oe.OnAccent else Oe.Text, fontSize = 14.sp), maxLines = 1)
    }
}

/** The picked section's rows, in groups, with its contrast warnings on top. */
@Composable
private fun ColumnScope.Rows(draft: ThemeDraft, folder: File?, actions: EditorActions) {
    val rows = draft.rows(draft.section)
    val warnings = rows.filter { it.kind == ThemeKind.COLOR }.flatMap { spec -> draft.warnings(spec.key).map { spec.key to it } }
        .filter { (key, _) -> ThemeKeys.CONTRAST_PAIRS.any { it.first == key } }
    if (warnings.isNotEmpty()) {
        val (fg, pair) = warnings.minBy { it.second.second }
        Row(
            Modifier.padding(top = 4.dp, bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Oe.WarnBg).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Warn, Oe.Warn, 18.dp)
            Column {
                Text(stringResource(R.string.theme_contrast_warning, stringResource(keyName(pair.first)), ratio(pair.second)), style = Type.BodyMedium.copy(color = Oe.WarnText, fontSize = 14.sp))
                Text(stringResource(R.string.theme_contrast_hint, stringResource(keyName(fg))), style = Type.Small.copy(color = Oe.WarnText))
            }
        }
    }
    rows.groupBy { it.group }.forEach { (group, specs) ->
        Text(stringResource(groupName(group)).uppercase(), style = Type.SectionCaps, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Surface)) {
            specs.forEachIndexed { i, spec ->
                if (i > 0) HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
                when (spec.kind) {
                    ThemeKind.COLOR -> ColorRow(spec, draft, actions)
                    ThemeKind.NUMBER -> NumberRow(spec, draft, actions)
                    ThemeKind.CHOICE -> ChoiceRow(spec, draft, actions)
                    ThemeKind.IMAGE -> ImageRow(spec, draft, folder, actions)
                }
            }
        }
    }
}

private fun ratio(r: Double) = String.format(java.util.Locale.ROOT, "%.1f:1", r)

@Composable
private fun ResetButton(spec: ThemeKey, draft: ThemeDraft, actions: EditorActions) {
    IconAction(Icons.Undo, stringResource(R.string.theme_reset), { actions.onReset(spec.key) }, enabled = draft.theme.isChanged(spec.key))
}

@Composable
private fun ColorRow(spec: ThemeKey, draft: ThemeDraft, actions: EditorActions) {
    val color = draft.theme.color(spec.key)
    val warnings = draft.warnings(spec.key)
    val label = stringResource(keyName(spec.key))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).gamepadFocus(corner = 0.dp).clickable(onClickLabel = label, role = Role.Button) { actions.onColor(spec.key) }
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(2.dp, Oe.Border, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body)
            if (warnings.isNotEmpty()) {
                val (other, r) = warnings.minBy { it.second }
                Text(stringResource(R.string.theme_contrast_warning, stringResource(keyName(other)), ratio(r)), style = Type.Small.copy(color = Oe.WarnText))
            } else {
                Text(if (draft.theme.isChanged(spec.key)) SkinColorMath.hex(color) else stringResource(R.string.theme_same_as_base), style = Type.Small)
            }
        }
        ResetButton(spec, draft, actions)
    }
}

/** How a number shows: dp, a percentage or a plain count. */
private fun numberText(spec: ThemeKey, value: Double): String = when (spec.key) {
    "corner_radius", "grid_gap" -> "${value.roundToInt()} dp"
    "border_width" -> String.format(java.util.Locale.ROOT, "%.1f dp", value)
    "library_image_blur", "sidebar_image_blur" -> value.roundToInt().toString()
    else -> "${(value * 100).roundToInt()}%"
}

/** A number's step for the D-pad and the slider's snap. */
private fun step(spec: ThemeKey): Double = when (spec.key) {
    "corner_radius", "grid_gap", "library_image_blur", "sidebar_image_blur" -> 1.0
    "border_width" -> 0.5
    else -> 0.05
}

@Composable
private fun NumberRow(spec: ThemeKey, draft: ThemeDraft, actions: EditorActions) {
    val value = draft.theme.number(spec.key)
    val label = stringResource(keyName(spec.key))
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Body, modifier = Modifier.weight(1f))
            Text(if (draft.theme.isChanged(spec.key)) numberText(spec, value) else stringResource(R.string.theme_same_as_base_value, numberText(spec, value)), style = Type.Small)
            ResetButton(spec, draft, actions)
        }
        ThemeSlider(value, spec.min, spec.max, step(spec), label, Modifier.padding(end = 10.dp)) { actions.onSet(spec.key, it) }
    }
}

@Composable
private fun ChoiceRow(spec: ThemeKey, draft: ThemeDraft, actions: EditorActions) {
    val value = draft.theme.choice(spec.key)
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(keyName(spec.key)), style = Type.Body, modifier = Modifier.weight(1f))
            ResetButton(spec, draft, actions)
        }
        Segmented(spec.choices.map { it to stringResource(choiceName(it)) }, value, Modifier.fillMaxWidth().padding(end = 10.dp)) { actions.onSet(spec.key, it) }
    }
}

private fun choiceName(choice: String) = when (choice) {
    ThemeFit.COVER -> R.string.theme_fit_cover
    ThemeFit.CONTAIN -> R.string.theme_fit_contain
    ThemeFit.TILE -> R.string.theme_fit_tile
    ThemeDensity.COMPACT -> R.string.theme_density_compact
    ThemeDensity.COMFORTABLE -> R.string.theme_density_comfortable
    else -> R.string.theme_density_default
}

@Composable
private fun ImageRow(spec: ThemeKey, draft: ThemeDraft, folder: File?, actions: EditorActions) {
    val file = draft.imageFile(spec.key, folder)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeBackdrop(
            file?.let { com.openemux.mobile.data.theme.ThemeImage(it.name, ThemeFit.COVER, 0f, 0f) }, file?.parentFile, Color.Transparent,
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Oe.Surface2),
        ) { if (file == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Image, Oe.Muted, 20.dp) } }
        Column(Modifier.weight(1f)) {
            Text(stringResource(keyName(spec.key)), style = Type.Body)
            Text(stringResource(if (file != null) R.string.theme_image_info else R.string.theme_image_none), style = Type.Small, maxLines = 2)
        }
        Box(
            Modifier.heightIn(min = 40.dp).gamepadFocus(corner = 20.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Oe.Border, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button) { actions.onImage(spec.key) }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(if (file != null) R.string.theme_image_change else R.string.theme_image_choose), style = Type.BodyMedium.copy(color = Oe.AccentText, fontSize = 13.sp)) }
        if (file != null) IconAction(Icons.Trash, stringResource(R.string.theme_image_remove), { actions.onReset(spec.key) })
    }
}

/** A value between [min] and [max]: dragged, tapped, or stepped by [step] with the D-pad's left and right. */
@Composable
private fun ThemeSlider(value: Double, min: Double, max: Double, step: Double, label: String, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    val share = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    val latest = rememberUpdatedState(onChange)
    fun snap(v: Double) = (Math.round((v - min) / step) * step + min).coerceIn(min, max)
    val track = Oe.Surface2
    val fill = Oe.Accent
    val knob = Oe.AccentBright
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat()) }
            .gamepadFocus(corner = 12.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> latest.value(snap(value - step))
                    Key.DirectionRight -> latest.value(snap(value + step))
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(min, max, step) {
                fun at(x: Float) = latest.value(snap(min + (x / size.width).coerceIn(0f, 1f) * (max - min)))
                detectDragGestures(onDragStart = { at(it.x) }) { change, _ -> at(change.position.x) }
            }
            .pointerInput(min, max, step) {
                detectTapGestures { p -> latest.value(snap(min + (p.x / size.width).coerceIn(0f, 1f) * (max - min))) }
            },
    ) {
        val bar = 4.dp.toPx()
        val r = 10.dp.toPx()
        val y = size.height / 2f
        val usable = size.width - 2 * r
        drawRoundRect(track, Offset(r, y - bar / 2f), Size(usable, bar), CornerRadius(bar / 2f))
        drawRoundRect(fill, Offset(r, y - bar / 2f), Size(usable * share, bar), CornerRadius(bar / 2f))
        drawCircle(knob, r, Offset(r + usable * share, y))
    }
}

/**
 * The color picker for one setting, the skin editor's own (issue 983): the square and hue, the hex, the palette's
 * colors and the recent ones, shared with the skin editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(context: Context, color: Int, onDone: () -> Unit, onColor: (Int) -> Unit) {
    val prefs = remember { context.getSharedPreferences("openemux", Context.MODE_PRIVATE) }
    var palette by remember { mutableStateOf(SkinPalettes.byId(prefs.getString(PREF_PALETTE, null))) }
    var recent by remember { mutableStateOf(SkinPalettes.recentFrom(prefs.getString(PREF_RECENT, null))) }
    var palettes by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(color) }
    fun pick(c: Int, commit: Boolean) {
        current = c
        onColor(c)
        if (commit) {
            recent = SkinPalettes.recent(recent, c)
            prefs.edit { putString(PREF_RECENT, SkinPalettes.recentText(recent)) }
        }
    }
    OeSheet(onDone, scrimColor = Color.Black.copy(alpha = 0.2f)) {
        Column(Modifier.fillMaxWidth().background(PanelBg).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (palettes) {
                PaletteList(palette, { palettes = false }) { p ->
                    palette = p
                    prefs.edit { putString(PREF_PALETTE, p.id) }
                    palettes = false
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.theme_color), Modifier.weight(1f), style = Type.Body.copy(color = TextOn, fontSize = 16.sp))
                    PrimaryButton(stringResource(R.string.theme_done), onDone, height = 40.dp, sidePadding = 16.dp)
                }
                PaletteRow(palette, current, { pick(it, true) }) { palettes = true }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ColorPicker(current, ::pick) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.editor_hex), style = Type.Small.copy(color = TextOff, fontSize = 12.sp))
                        HexField(current) { pick(it, true) }
                        if (recent.isNotEmpty()) {
                            Text(stringResource(R.string.editor_recent), style = Type.Small.copy(color = TextOff, fontSize = 12.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                recent.take(4).forEach { c -> Swatch(c, 26, c == current, SkinColorMath.hex(c)) { pick(c, true) } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
