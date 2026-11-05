package com.openemux.mobile.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinCatalog
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import com.openemux.mobile.launch.ImportedSkinRenderer
import com.openemux.mobile.launch.skinForm
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The bundled skins a console offers: [options], the variants the app ships a package of for it, in the picker's
 * order (its own colors, Dark, issue 718, and its official colors, issue 720). A console with no package offers its
 * drawn skin, in its own colors.
 */
data class SkinChoice(val options: List<SkinSwatch>) {
    /** More than the console's own colors: there is something to pick among the bundled ones. */
    val pickable: Boolean get() = options.size > 1

    /** The option [skin] (a saved id) names; the console's own colors when it names none of them. */
    fun option(skin: String): SkinSwatch? = options.firstOrNull { it.variant.id == skin } ?: options.firstOrNull { it.variant == SkinVariant.CONSOLE }

    companion object {
        /**
         * The choice for [console], from the [catalog] (its JSON), keeping the variants [hasPackage] finds a package of.
         * No catalog lists the variants in their own order, without swatches.
         */
        fun of(catalog: String?, console: Console, hasPackage: (SkinVariant) -> Boolean): SkinChoice {
            val listed = SkinCatalog.parse(catalog, console).ifEmpty { SkinVariant.entries.map { SkinSwatch(it, null, null) } }
            return SkinChoice(listed.filter { hasPackage(it.variant) }.ifEmpty { listOf(SkinSwatch(SkinVariant.CONSOLE, null, null)) })
        }
    }
}

/**
 * A console's skin list (issue 782): the [bundled] ones, the player's [own] and the [imported] packages, with the
 * bundled variant saved ([bundledId], the one to go back to) and the player's skin in use ([custom], a ref).
 */
data class SkinListState(
    val bundled: SkinChoice,
    val own: List<StoredSkin>,
    val imported: List<StoredSkin>,
    val bundledId: String,
    val custom: String?,
) {
    /** The player's skin in use: the one [custom] names, when it is still there and readable. */
    val customInUse: StoredSkin? get() = (own + imported).firstOrNull { it.ref == custom && !it.broken }

    /** The bundled skin used, or gone back to. */
    val bundledPick: SkinSwatch? get() = bundled.option(bundledId)

    fun inUse(skin: StoredSkin) = customInUse?.ref == skin.ref

    fun inUse(swatch: SkinSwatch) = customInUse == null && bundledPick == swatch

    /** More than one skin in all: the list has something to pick. */
    val shown: Boolean get() = bundled.options.size + own.size + imported.size > 1
}

/** Whether [skin] can be used on a device of [form]: readable and with a layout for it. */
fun madeFor(skin: StoredSkin, form: SkinForm) = !skin.broken && form in skin.forms

/** The line a skin with no layout for [form] shows. */
@StringRes
fun notMadeFor(form: SkinForm): Int = when (form) {
    SkinForm.PHONE -> R.string.skins_not_for_phones
    SkinForm.TABLET -> R.string.skins_not_for_tablets
    SkinForm.FOLD -> R.string.skins_not_for_folds
}

@StringRes
fun formLabel(form: SkinForm): Int = when (form) {
    SkinForm.PHONE -> R.string.skins_form_phone
    SkinForm.TABLET -> R.string.skins_form_tablet
    SkinForm.FOLD -> R.string.skins_form_fold
}

/** What the skin row says: the name of the player's skin in use, else the bundled variant's label (a string id). */
sealed interface SkinValue {
    data class Named(val name: String) : SkinValue
    data class Label(@StringRes val id: Int) : SkinValue
}

/** The value the skin row shows for a console; null while unknown or when there is nothing to pick. */
fun skinValue(state: SkinListState?): SkinValue? = when {
    state == null -> null
    state.customInUse != null -> SkinValue.Named(state.customInUse!!.name)
    state.shown -> state.bundledPick?.variant?.label?.let { SkinValue.Label(it) }
    else -> null
}

@Composable
fun SkinValue.text(): String = when (this) {
    is SkinValue.Named -> name
    is SkinValue.Label -> stringResource(id)
}

/** [SkinListState] for [console], read off the main thread; null until it is known. Bump [revision] to read it again. */
@Composable
fun rememberSkinList(library: Library, console: Console, revision: Int = 0): SkinListState? {
    val context = LocalContext.current
    val meta by library.meta.collectAsState()
    val settings = meta.consoles[console.id]
    val listed by produceState<Triple<SkinChoice, List<StoredSkin>, List<StoredSkin>>?>(null, console, revision) {
        value = withContext(Dispatchers.IO) {
            val choice = SkinChoice.of(skinCatalog(context), console) {
                ImportedSkinRenderer.bundledName(context.assets.list(it.assetDir), console) != null
            }
            val stored = library.skins.list(console)
            Triple(choice, stored.filter { it.kind == StoredSkinKind.OWN }, stored.filter { it.kind == StoredSkinKind.IMPORTED })
        }
    }
    val (choice, own, imported) = listed ?: return null
    return SkinListState(choice, own, imported, settings?.skin ?: SkinVariant.CONSOLE.id, remember(meta) { library.customSkin(console) })
}

/** The catalog's JSON, read from the APK once per process: it only changes with an update, which restarts the app. */
@Volatile
private var catalogJson: String? = null

private fun skinCatalog(context: Context): String? = catalogJson
    ?: runCatching { context.assets.open(SkinCatalog.ASSET).bufferedReader().use { it.readText() } }.getOrNull()?.also { catalogJson = it }

/** The device form the list is shown on: the window's, as the game draws it. */
@Composable
fun currentSkinForm(): SkinForm {
    val window = LocalWindowLayout.current
    return skinForm(window.tablet, window.fold)
}

/** A small controller in a skin's colours: [body] the shell, [key] the buttons. */
@Composable
fun MiniSkin(body: Color, key: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(body).border(1.dp, Oe.Border, RoundedCornerShape(10.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The D-pad, the screen and the face buttons.
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp, 6.dp).clip(RoundedCornerShape(2.dp)).background(key))
                Box(Modifier.size(6.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(key))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF050507)))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(key))
                Row { Box(Modifier.size(7.dp).clip(CircleShape).background(key)); Spacer(Modifier.width(7.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(key)) }
                Box(Modifier.size(7.dp).clip(CircleShape).background(key))
            }
        }
    }
}

private fun swatchColors(swatch: SkinSwatch?): Pair<Color, Color> =
    Color(swatch?.body ?: 0xFF2A2A33) to Color(swatch?.key ?: 0xFF6E6E7C)

private fun storedColors(skin: StoredSkin): Pair<Color, Color> =
    Color(skin.body?.toLong()?.and(0xFFFFFFFFL) ?: 0xFF24242C) to Color(skin.key?.toLong()?.and(0xFFFFFFFFL) ?: 0xFF6E6E7C)

/** Phone, Tablet and Fold: lit for the forms in [forms], grey and dashed for the others. */
@Composable
fun FormBadges(forms: Set<SkinForm>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SkinForm.entries.forEach { form ->
            val on = form in forms
            val label = stringResource(formLabel(form))
            val said = stringResource(if (on) R.string.skins_form_made else R.string.skins_form_missing, label)
            Box(
                Modifier.semantics { contentDescription = said }.clip(RoundedCornerShape(6.dp))
                    .background(if (on) Oe.AccentSoft else Color.Transparent)
                    .let { if (on) it.border(1.dp, Oe.AccentText.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else it.dashedBorder(Oe.Chevron, 6.dp) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text(label, style = Type.Tag.copy(color = if (on) Oe.AccentOnSoft else Oe.Faint, fontSize = 10.sp)) }
        }
    }
}

/**
 * The console's skins in three groups (issue 782), each skin with its form badges; tapping one uses it for every game
 * of [console]. The player's skins and the bundled ones open an actions sheet from their menu button.
 */
@Composable
fun SkinList(library: Library, console: Console, state: SkinListState, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val form = currentSkinForm()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ownSheet by remember { mutableStateOf<StoredSkin?>(null) }
    var bundledSheet by remember { mutableStateOf<SkinSwatch?>(null) }
    var renaming by remember { mutableStateOf<StoredSkin?>(null) }
    var deleting by remember { mutableStateOf<StoredSkin?>(null) }

    fun store(action: () -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(action).isSuccess }
            if (!ok) Toast.makeText(context, R.string.skins_error, Toast.LENGTH_SHORT).show()
            onChanged()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.own.isNotEmpty()) {
            SkinGroup(stringResource(R.string.skins_yours), count = state.own.size) {
                CardGrid(state.own) { skin ->
                    val (body, key) = storedColors(skin)
                    SkinCard(
                        skin.name, body, key, skin.forms, state.inUse(skin), madeFor(skin, form),
                        reason = stringResource(if (skin.broken) R.string.skins_broken else notMadeFor(form)),
                        onPick = { library.useCustomSkin(console, skin.ref) },
                        onMenu = { ownSheet = skin },
                    )
                }
            }
        }
        SkinGroup(stringResource(R.string.skins_bundled), locked = true) {
            CardGrid(state.bundled.options) { swatch ->
                val (body, key) = swatchColors(swatch)
                SkinCard(
                    stringResource(swatch.variant.label), body, key, SkinForm.entries.toSet(), state.inUse(swatch), true, "",
                    onPick = { library.useBundledSkin(console, swatch.variant.id) },
                    onMenu = { bundledSheet = swatch },
                )
            }
        }
        if (state.imported.isNotEmpty()) {
            SkinGroup(stringResource(R.string.skins_imported)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.imported.forEach { skin ->
                        ImportedRow(skin, state.inUse(skin), madeFor(skin, form), stringResource(if (skin.broken) R.string.skins_broken else notMadeFor(form))) {
                            library.useCustomSkin(console, skin.ref)
                        }
                    }
                }
            }
        }
    }

    ownSheet?.let { skin ->
        val back = state.bundledPick?.variant?.label?.let { stringResource(it) }.orEmpty()
        SkinActionsSheet(skin.name, storedColors(skin), skin.forms, state.inUse(skin), onClose = { ownSheet = null }) {
            if (!state.inUse(skin) && madeFor(skin, form)) ActionRow(Icons.Check, stringResource(R.string.skins_use)) {
                ownSheet = null; library.useCustomSkin(console, skin.ref)
            }
            if (!skin.broken) {
                ActionRow(Icons.List, stringResource(R.string.skins_rename)) { ownSheet = null; renaming = skin }
                ActionRow(Icons.Copy, stringResource(R.string.skins_duplicate)) {
                    ownSheet = null
                    store { library.skins.duplicate(console, skin.id, context.getString(R.string.skins_copy_name, skin.name).take(SkinStore.MAX_NAME)) }
                }
            }
            ActionRow(
                Icons.Trash, stringResource(R.string.skins_delete), danger = true,
                sub = if (state.inUse(skin)) stringResource(R.string.skins_delete_sub, back) else null,
            ) { ownSheet = null; deleting = skin }
        }
    }
    bundledSheet?.let { swatch ->
        SkinActionsSheet(stringResource(swatch.variant.label), swatchColors(swatch), SkinForm.entries.toSet(), state.inUse(swatch), onClose = { bundledSheet = null }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Lock, Oe.Muted, 18.dp)
                Text(stringResource(R.string.skins_bundled_locked), style = Type.Small)
            }
            HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
            ActionRow(Icons.Check, stringResource(R.string.skins_use_as_is)) {
                bundledSheet = null; library.useBundledSkin(console, swatch.variant.id)
            }
        }
    }
    renaming?.let { skin ->
        RenameSkinSheet(skin.name, onClose = { renaming = null }) { name ->
            renaming = null
            store { library.skins.rename(console, skin.id, name) }
        }
    }
    deleting?.let { skin ->
        OeConfirmDialog(
            onDismissRequest = { deleting = null },
            title = stringResource(R.string.skins_delete_title, skin.name),
            text = stringResource(R.string.skins_delete_body),
            confirm = stringResource(R.string.skins_delete),
            onConfirm = {
                deleting = null
                store {
                    library.skins.delete(console, skin.id)
                    library.forgetCustomSkin(console, skin.ref)
                }
            },
            dismiss = stringResource(R.string.detail_cancel),
        )
    }
}

@Composable
private fun SkinGroup(title: String, count: Int? = null, locked: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (locked) Icon(Icons.Lock, Oe.Muted, 14.dp)
            Text(title.uppercase(), style = Type.Eyebrow, modifier = Modifier.weight(1f))
            if (count != null) Text(count.toString(), style = Type.Small)
        }
        content()
    }
}

/** Two cards per row. */
@Composable
private fun <T> CardGrid(items: List<T>, card: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { card(it) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkinCard(
    name: String, body: Color, key: Color, forms: Set<SkinForm>, inUse: Boolean, pickable: Boolean, reason: String,
    onPick: () -> Unit, onMenu: () -> Unit,
) {
    val menuLabel = stringResource(R.string.skins_actions, name)
    Column(
        Modifier.fillMaxWidth().gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(Oe.Surface)
            .border(2.dp, if (inUse) Oe.AccentBright else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton) { if (pickable) onPick() else onMenu() }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniSkin(body, key, Modifier.fillMaxWidth().height(78.dp).alpha(if (pickable) 1f else DIMMED_ALPHA))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    inUse -> Text(stringResource(R.string.skins_in_use), style = Type.Small.copy(color = Oe.AccentText, fontWeight = FontWeight.SemiBold))
                    !pickable -> Text(reason, style = Type.Small)
                }
                FormBadges(forms)
            }
            Box(
                Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape)
                    .clickable(onClickLabel = menuLabel, role = Role.Button, onClick = onMenu).semantics { contentDescription = menuLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Dots, Oe.Muted, 20.dp) }
        }
    }
}

@Composable
private fun ImportedRow(skin: StoredSkin, inUse: Boolean, pickable: Boolean, reason: String, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(Oe.Surface)
            .border(2.dp, if (inUse) Oe.AccentBright else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = pickable, role = Role.RadioButton, onClick = onPick)
            .alpha(if (pickable) 1f else DIMMED_ALPHA)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (body, key) = storedColors(skin)
        MiniSkin(body, key, Modifier.width(72.dp).height(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(skin.name, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FormBadges(skin.forms)
            when {
                inUse -> Text(stringResource(R.string.skins_in_use), style = Type.Small.copy(color = Oe.AccentText, fontWeight = FontWeight.SemiBold))
                !pickable -> Text(reason, style = Type.Small)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkinActionsSheet(
    name: String, colors: Pair<Color, Color>, forms: Set<SkinForm>, inUse: Boolean, onClose: () -> Unit,
    rows: @Composable () -> Unit,
) {
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniSkin(colors.first, colors.second, Modifier.width(132.dp).height(66.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = Type.Title.copy(fontSize = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FormBadges(forms)
                    if (inUse) Tag(stringResource(R.string.skins_in_use), Oe.AccentTint, Oe.AccentText)
                }
            }
            Card { rows() }
        }
    }
}

@Composable
private fun ActionRow(icon: String, label: String, danger: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().gamepadFocus(corner = 0.dp, inset = true).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val color = if (danger) Oe.Danger else Oe.Text
        Icon(icon, color, 20.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body.copy(color = color))
            if (sub != null) Text(sub, style = Type.Small)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSkinSheet(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val valid = name.isNotBlank()
    fun save() { if (valid) onSave(name.trim()) }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.skins_rename_title), style = Type.Title.copy(fontSize = 22.sp))
            OeTextField(
                stringResource(R.string.skins_name_label), name, { name = it.take(SkinStore.MAX_NAME) }, Modifier.fillMaxWidth(),
                isError = !valid,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.detail_cancel), onClose, Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.skins_save), ::save, Modifier.weight(1f), enabled = valid)
            }
        }
    }
}

/** A dashed outline of [color], for a form badge that is off. */
private fun Modifier.dashedBorder(color: Color, corner: androidx.compose.ui.unit.Dp) = drawBehind {
    val r = corner.toPx()
    drawRoundRect(
        color, cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

/** How faded a skin that cannot be used on this device is. */
private const val DIMMED_ALPHA = 0.6f

/** The skin list for [console] in a sheet, for where it does not fit inline (a game's detail screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(library: Library, console: Console, onClose: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val state = rememberSkinList(library, console, revision)
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.skins_title), style = Type.Title.copy(fontSize = 22.sp))
            Text(stringResource(R.string.skins_lead), style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            state?.let { SkinList(library, console, it, onChanged = { revision++ }) }
        }
    }
}
