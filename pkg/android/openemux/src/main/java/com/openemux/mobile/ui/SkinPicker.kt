package com.openemux.mobile.ui

import android.content.Context
import android.widget.Toast
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.openemux.mobile.data.ImportCheck
import com.openemux.mobile.data.ImportRefusal
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinCatalog
import com.openemux.mobile.data.SkinForm
import com.openemux.mobile.data.SkinImport
import com.openemux.mobile.data.SkinImportTooLarge
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinTemplate
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.data.StoredSkin
import com.openemux.mobile.data.StoredSkinKind
import com.openemux.mobile.launch.ImportedSkinRenderer
import com.openemux.mobile.launch.SkinConversion
import com.openemux.mobile.launch.OpenEmuxSkinRenderer
import com.openemux.mobile.launch.skinForm
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
 * [convertible]: the bundled variants (ids) "Create copy and edit" can make a skin of the player's own of (issue 783),
 * [converted] those of them that are Delta/Manic packages, converted rather than copied (issue 797).
 */
data class SkinListState(
    val bundled: SkinChoice,
    val own: List<StoredSkin>,
    val imported: List<StoredSkin>,
    val bundledId: String,
    val custom: String?,
    val convertible: Set<String> = emptySet(),
    val converted: Set<String> = emptySet(),
) {
    /** The player's skin in use: the one [custom] names, when it is still there and readable. */
    val customInUse: StoredSkin? get() = (own + imported).firstOrNull { it.ref == custom && !it.broken }

    /** The bundled skin used, or gone back to. */
    val bundledPick: SkinSwatch? get() = bundled.option(bundledId)

    fun inUse(skin: StoredSkin) = customInUse?.ref == skin.ref

    fun inUse(swatch: SkinSwatch) = customInUse == null && bundledPick == swatch

    /** Whether "Create copy and edit" is offered for the bundled [swatch]. */
    fun convertible(swatch: SkinSwatch) = swatch.variant.id in convertible

    /** Whether making [swatch]'s copy converts a Delta/Manic package (issue 797), which takes a moment. */
    fun converts(swatch: SkinSwatch) = swatch.variant.id in converted

    /**
     * More than one skin in all, or a bundled one to make a copy of (issue 798: a console with only the drawn skin):
     * the list has something to pick or to do.
     */
    val shown: Boolean get() = bundled.options.size + own.size + imported.size > 1 || convertible.isNotEmpty()
}

/** Whether [skin] can be used on a device of [form]: readable and with a layout for it. */
fun madeFor(skin: StoredSkin, form: SkinForm) = !skin.broken && form in skin.forms

/** Whether Duplicate can make an imported package [skin] a skin of the player's own: it reads and has art to convert (issue 797). */
fun duplicable(skin: StoredSkin) = !skin.broken && skin.forms.isNotEmpty()

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
    val listed by produceState<SkinListState?>(null, console, revision) {
        value = withContext(Dispatchers.IO) {
            val list = { dir: String -> context.assets.list(dir) }
            val choice = SkinChoice.of(skinCatalog(context), console) { ImportedSkinRenderer.bundledName(list(it.assetDir), console) != null }
            val stored = library.skins.list(console)
            val kinds = choice.options.associate { it.variant.id to SkinConversion.kind(list, console, it.variant) }
            SkinListState(
                choice, stored.filter { it.kind == StoredSkinKind.OWN }, stored.filter { it.kind == StoredSkinKind.IMPORTED }, "", null,
                kinds.filterValues { it != null }.keys, kinds.filterValues { it == SkinConversion.Kind.PACKAGE }.keys,
            )
        }
    }
    val state = listed ?: return null
    return state.copy(bundledId = settings?.skin ?: SkinVariant.CONSOLE.id, custom = remember(meta, console) { library.customSkin(console) })
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormBadges(forms: Set<SkinForm>, modifier: Modifier = Modifier) {
    // A badge never shrinks under its word: when the three do not fit side by side, the last one goes to a new line.
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SkinForm.entries.forEach { form ->
            val on = form in forms
            val label = stringResource(formLabel(form))
            val said = stringResource(if (on) R.string.skins_form_made else R.string.skins_form_missing, label)
            Box(
                Modifier.semantics { contentDescription = said }.clip(RoundedCornerShape(6.dp))
                    .background(if (on) Oe.AccentSoft else Color.Transparent)
                    .let { if (on) it.border(1.dp, Oe.AccentText.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else it.dashedBorder(Oe.Chevron, 6.dp) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text(label, style = Type.Tag.copy(color = if (on) Oe.AccentOnSoft else Oe.Faint, fontSize = 10.sp), maxLines = 1, softWrap = false) }
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
    var importedSheet by remember { mutableStateOf<StoredSkin?>(null) }
    var renaming by remember { mutableStateOf<StoredSkin?>(null) }
    var copying by remember { mutableStateOf<SkinSwatch?>(null) }
    var deleting by remember { mutableStateOf<StoredSkin?>(null) }
    var templating by remember { mutableStateOf<StoredSkin?>(null) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<PickedSkin?>(null) }
    var reading by remember { mutableStateOf(false) }
    // An import still writing: picking another file meanwhile would clear the cache folder under it.
    var writing by remember { mutableStateOf(false) }

    // Issue 787: the template is written straight into the document the player picked.
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val skin = templating
        if (uri == null) return@rememberLauncherForActivityResult
        if (skin == null) {
            // The sheet went away while the picker was open (the activity was recreated): drop the empty document.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            return@rememberLauncherForActivityResult
        }
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { writeTemplate(context, library, console, skin, uri) }.isSuccess
                    // A zip cut short is no template: the document the picker created goes too.
                    .also { if (!it) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
            }
            saving = false
            if (ok) templating = null
            Toast.makeText(context, if (ok) R.string.skins_template_saved else R.string.skins_error, Toast.LENGTH_SHORT).show()
        }
    }
    // Any file: a .deltaskin has no MIME type a picker would filter on.
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            importing = withContext(Dispatchers.IO) { pickSkin(context, console, uri) }
            reading = false
        }
    }

    fun store(action: () -> Unit) {
        scope.launch {
            // Migrated first: until then the console plays the first skin by name, which a copy or a delete would change.
            val ok = withContext(Dispatchers.IO) { runCatching { library.migrateSkins() }; runCatching(action).isSuccess }
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
                        ImportedRow(
                            skin, state.inUse(skin), madeFor(skin, form), stringResource(if (skin.broken) R.string.skins_broken else notMadeFor(form)),
                            onPick = { library.useCustomSkin(console, skin.ref) },
                            // A package with nothing to convert (unreadable, no complete art) has no menu: Duplicate would fail.
                            onMenu = if (duplicable(skin)) { { importedSheet = skin } } else null,
                        )
                    }
                }
            }
        }
        SecondaryButton(stringResource(R.string.skins_import), { pickZip.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), icon = Icons.Upload, busy = reading || writing)
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
                ActionRow(Icons.Download, stringResource(R.string.skins_template), sub = stringResource(R.string.skins_template_sub)) {
                    ownSheet = null; templating = skin
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
            if (state.convertible(swatch)) ActionRow(Icons.Pencil, stringResource(R.string.skins_create_copy)) {
                bundledSheet = null; copying = swatch
            }
            ActionRow(Icons.Check, stringResource(R.string.skins_use_as_is)) {
                bundledSheet = null; library.useBundledSkin(console, swatch.variant.id)
            }
        }
    }
    importedSheet?.let { skin ->
        SkinActionsSheet(skin.name, storedColors(skin), skin.forms, state.inUse(skin), onClose = { importedSheet = null }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Lock, Oe.Muted, 18.dp)
                Text(stringResource(R.string.skins_imported_locked), style = Type.Small)
            }
            HorizontalDivider(color = Oe.Divider, thickness = 1.dp)
            if (!state.inUse(skin) && madeFor(skin, form)) ActionRow(Icons.Check, stringResource(R.string.skins_use)) {
                importedSheet = null; library.useCustomSkin(console, skin.ref)
            }
            // Issue 797: a copy of the player's own, in OpenEmux's format; the package stays under Imported.
            if (duplicable(skin)) ActionRow(Icons.Copy, stringResource(R.string.skins_duplicate)) {
                importedSheet = null
                Toast.makeText(context, R.string.skins_copy_working, Toast.LENGTH_SHORT).show()
                val name = context.getString(R.string.skins_copy_name, skin.name).take(SkinStore.MAX_NAME)
                store { SkinConversion.duplicatePackage(library.skins, console, skin.id, name) }
            }
        }
    }
    renaming?.let { skin ->
        NameSkinSheet(stringResource(R.string.skins_rename_title), skin.name, stringResource(R.string.skins_save), onClose = { renaming = null }) { name ->
            renaming = null
            store { library.skins.rename(console, skin.id, name) }
        }
    }
    copying?.let { swatch ->
        val label = stringResource(swatch.variant.label)
        NameSkinSheet(
            stringResource(R.string.skins_create_copy_title), stringResource(R.string.skins_bundled_copy_name, label).take(SkinStore.MAX_NAME),
            stringResource(R.string.skins_create_copy_save), lead = stringResource(R.string.skins_create_copy_lead), onClose = { copying = null },
        ) { name ->
            copying = null
            // A Delta/Manic package takes a moment: every control is lifted off its art (issue 797).
            if (state.converts(swatch)) Toast.makeText(context, R.string.skins_copy_working, Toast.LENGTH_SHORT).show()
            // The copy is used at once: until the editor comes (issue 784), the flow ends on the list with it in use.
            store { library.useCustomSkin(console, SkinConversion.convert(context, library.skins, console, swatch.variant, name)) }
        }
    }
    templating?.let { skin ->
        TemplateSheet(skin.name, saving, onClose = { if (!saving) templating = null }) {
            saveTemplate.launch(SkinTemplate.fileName(skin.name))
        }
    }
    importing?.let { picked ->
        ImportSkinSheet(picked, console, state.own.filter { !it.broken }, onClose = { importing = null; picked.file.delete() }) { name, replace ->
            val check = picked.check
            importing = null
            writing = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { library.migrateSkins() }
                    runCatching {
                        when (check) {
                            is ImportCheck.Own -> library.skins.importOwn(console, picked.file, name, replace)
                            is ImportCheck.Package -> library.skins.importPackage(console, picked.file, check.name, check.extension)
                            is ImportCheck.Refused -> error("refused")
                        }
                    }.isSuccess.also { picked.file.delete() }
                }
                writing = false
                Toast.makeText(context, if (ok) R.string.skins_import_done else R.string.skins_error, Toast.LENGTH_SHORT).show()
                onChanged()
            }
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
    // The menu button sits inside the card, so a spatial search from the card never finds it: Right goes to it and
    // Left comes back, and from the button Right goes on to the next card as before.
    val cardFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth().gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(Oe.Surface)
            .border(2.dp, if (inUse) Oe.AccentBright else Color.Transparent, RoundedCornerShape(16.dp))
            .focusRequester(cardFocus).focusProperties { right = menuFocus }
            .clickable(role = Role.RadioButton) { if (pickable) onPick() else onMenu() }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniSkin(body, key, Modifier.fillMaxWidth().height(78.dp).alpha(if (pickable) 1f else DIMMED_ALPHA))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    inUse -> Text(stringResource(R.string.skins_in_use), style = Type.Small.copy(color = Oe.AccentText, fontWeight = FontWeight.SemiBold))
                    !pickable -> Text(reason, style = Type.Small)
                }
            }
            Box(
                Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape)
                    .focusRequester(menuFocus).focusProperties { left = cardFocus }
                    .clickable(onClickLabel = menuLabel, role = Role.Button, onClick = onMenu).semantics { contentDescription = menuLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Dots, Oe.Muted, 20.dp) }
        }
        // Under the name and the menu button, across the whole card: beside the button there was no room for three.
        FormBadges(forms, Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}

@Composable
private fun ImportedRow(skin: StoredSkin, inUse: Boolean, pickable: Boolean, reason: String, onPick: () -> Unit, onMenu: (() -> Unit)?) {
    val menuLabel = stringResource(R.string.skins_actions, skin.name)
    // As on a skin card: Right goes from the row to its menu button, Left comes back.
    val rowFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(Oe.Surface)
            .border(2.dp, if (inUse) Oe.AccentBright else Color.Transparent, RoundedCornerShape(16.dp))
            .focusRequester(rowFocus).focusProperties { if (onMenu != null) right = menuFocus }
            // A package not made for this device opens its sheet: Duplicate still works there.
            .clickable(enabled = pickable || onMenu != null, role = Role.RadioButton) { if (pickable) onPick() else onMenu?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (body, key) = storedColors(skin)
        MiniSkin(body, key, Modifier.width(72.dp).height(40.dp).alpha(if (pickable) 1f else DIMMED_ALPHA))
        Column(Modifier.weight(1f).alpha(if (pickable) 1f else DIMMED_ALPHA), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(skin.name, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FormBadges(skin.forms)
            when {
                inUse -> Text(stringResource(R.string.skins_in_use), style = Type.Small.copy(color = Oe.AccentText, fontWeight = FontWeight.SemiBold))
                !pickable -> Text(reason, style = Type.Small)
            }
        }
        if (onMenu != null) Box(
            Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape)
                .focusRequester(menuFocus).focusProperties { left = rowFocus }
                .clickable(onClickLabel = menuLabel, role = Role.Button, onClick = onMenu).semantics { contentDescription = menuLabel },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Dots, Oe.Muted, 20.dp) }
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

/** The sheet that asks for a skin's name: [title] over an optional [lead] line, [initial] filled in, [confirm] saving it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSkinSheet(title: String, initial: String, confirm: String, lead: String? = null, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val valid = name.isNotBlank()
    fun save() { if (valid) onSave(name.trim()) }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Type.Title.copy(fontSize = 22.sp))
            if (lead != null) Text(lead, style = Type.Small)
            OeTextField(
                stringResource(R.string.skins_name_label), name, { name = it.take(SkinStore.MAX_NAME) }, Modifier.fillMaxWidth(),
                isError = !valid,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.detail_cancel), onClose, Modifier.weight(1f))
                PrimaryButton(confirm, ::save, Modifier.weight(1f), enabled = valid)
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

/** A file picked with "Import .zip" (issue 787): its copy in the cache, its name as the picker gave it, what it is. */
data class PickedSkin(val file: File, val name: String, val check: ImportCheck)

/**
 * Copies the document [uri] into the cache (at most [SkinImport.MAX_BYTES]) and tells what it is for [console]'s list.
 * A file over the limit, or one that cannot be read, comes back refused with nothing kept.
 */
private fun pickSkin(context: Context, console: Console, uri: Uri): PickedSkin {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    val dir = File(context.cacheDir, "skin-import").apply { mkdirs() }
    // One picked file at a time: whatever an earlier one left (the app closed with its sheet open) goes.
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "picked-${System.currentTimeMillis()}.zip")
    val check = try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        input.use { i -> file.outputStream().use { SkinImport.copyLimited(i, it, SkinImport.MAX_BYTES) } }
        SkinImport.inspect(file, console, name)
    } catch (e: SkinImportTooLarge) {
        ImportCheck.Refused(ImportRefusal.TOO_LARGE)
    } catch (e: Exception) {
        ImportCheck.Refused(ImportRefusal.NOT_A_SKIN)
    }
    if (check is ImportCheck.Refused) file.delete()
    return PickedSkin(file, name, check)
}

/** Writes the player's skin [skin] of [console] as a template zip into the document [uri]. */
private fun writeTemplate(context: Context, library: Library, console: Console, skin: StoredSkin, uri: Uri) {
    val (pkg, json) = library.skins.template(console, skin.id)
    val renderer = OpenEmuxSkinRenderer(context)
    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot write $uri")
    out.use { SkinTemplate.write(it, pkg.skin, json) { id, image -> renderer.templateImage(pkg, id, image) } }
}

/** "Download as template": what the zip holds and how it comes back, and the button that saves it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(name: String, saving: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.skins_template_title), style = Type.Title.copy(fontSize = 22.sp))
                Text(stringResource(R.string.skins_template_lead, name), style = Type.Small.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    R.string.skins_template_step1 to R.string.skins_template_step1_desc,
                    R.string.skins_template_step2 to R.string.skins_template_step2_desc,
                    R.string.skins_template_step3 to R.string.skins_template_step3_desc,
                ).forEachIndexed { i, (title, desc) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Oe.AccentTint), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = Type.Tag.copy(color = Oe.AccentText, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                        Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(title), style = Type.Section)
                            Text(stringResource(desc), style = Type.Small)
                        }
                    }
                }
            }
            Text(stringResource(R.string.skins_template_note), style = Type.Small)
            if (saving) {
                SecondaryButton(stringResource(R.string.skins_template_saving), {}, Modifier.fillMaxWidth(), busy = true)
            } else {
                PrimaryButton(stringResource(R.string.skins_template_save), onSave, Modifier.fillMaxWidth(), icon = Icons.Download)
            }
        }
    }
}

/**
 * "Import skin": the picked file, its console and badges, and where it goes. A skin in OpenEmux's format goes in as a
 * new skin (with a name) or over one of the player's skins in [own] (never a bundled one); a package goes under
 * Imported; a refused file says why. [onImport] gets the new skin's name, or the id of the skin to replace.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ImportSkinSheet(picked: PickedSkin, console: Console, own: List<StoredSkin>, onClose: () -> Unit, onImport: (String?, String?) -> Unit) {
    val check = picked.check
    var name by remember(picked) { mutableStateOf((check as? ImportCheck.Own)?.skin?.name.orEmpty().take(SkinStore.MAX_NAME)) }
    var replace by remember(picked) { mutableStateOf<String?>(null) }
    var replacing by remember(picked) { mutableStateOf(false) }
    val ready = when (check) {
        is ImportCheck.Own -> if (replacing) replace != null else name.isNotBlank()
        is ImportCheck.Package -> true
        is ImportCheck.Refused -> false
    }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.skins_import_title), style = Type.Title.copy(fontSize = 22.sp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val (body, key) = importColors(check)
                MiniSkin(body, key, Modifier.width(96.dp).height(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(picked.name.ifEmpty { stringResource(R.string.skins_import_title) }, style = Type.Section, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (check) {
                        is ImportCheck.Own -> {
                            Text(console.displayName, style = Type.Small)
                            FormBadges(check.forms)
                        }
                        is ImportCheck.Package -> {
                            Text(console.displayName, style = Type.Small)
                            FormBadges(check.forms)
                        }
                        is ImportCheck.Refused -> Unit
                    }
                    if (check is ImportCheck.Refused) {
                        Text(refusal(check), style = Type.Small.copy(color = Oe.Danger))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Check, Oe.Ok, 14.dp)
                            Text(stringResource(R.string.skins_import_ready), style = Type.Small.copy(color = Oe.OkText))
                        }
                    }
                }
            }
            when (check) {
                is ImportCheck.Own -> {
                    Text(stringResource(R.string.skins_import_save_as), style = Type.Caption.copy(color = Oe.Muted))
                    RadioCard(!replacing, stringResource(R.string.skins_import_new), "", { replacing = false })
                    if (!replacing) {
                        OeTextField(
                            stringResource(R.string.skins_name_label), name, { name = it.take(SkinStore.MAX_NAME) }, Modifier.fillMaxWidth(),
                            isError = name.isBlank(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                    if (own.isNotEmpty()) {
                        RadioCard(replacing, stringResource(R.string.skins_import_replace), stringResource(R.string.skins_import_replace_sub), { replacing = true })
                        if (replacing) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                own.forEach { skin -> SkinChip(skin.name, replace == skin.id) { replace = skin.id } }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Lock, Oe.Muted, 16.dp)
                        Text(stringResource(R.string.skins_import_bundled_note), style = Type.Small)
                    }
                }
                is ImportCheck.Package -> Text(stringResource(R.string.skins_import_package), style = Type.Small)
                is ImportCheck.Refused -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (check is ImportCheck.Refused) {
                    SecondaryButton(stringResource(R.string.skins_close), onClose, Modifier.weight(1f))
                } else {
                    SecondaryButton(stringResource(R.string.detail_cancel), onClose, Modifier.weight(1f))
                    PrimaryButton(
                        stringResource(R.string.skins_import_action),
                        { if (ready) onImport(if (replacing) null else name.trim(), if (replacing) replace else null) },
                        Modifier.weight(1f), enabled = ready,
                    )
                }
            }
        }
    }
}

/** One of the player's skins as a choice to replace. */
@Composable
private fun SkinChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.gamepadFocus(corner = 999.dp).clip(RoundedCornerShape(999.dp))
            .background(if (selected) Oe.AccentTint else Color.Transparent)
            .border(1.dp, if (selected) Oe.AccentBright else Oe.Border, RoundedCornerShape(999.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(name, style = Type.Body.copy(fontSize = 13.sp, color = if (selected) Oe.AccentText else Oe.Text), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The preview colours of a picked skin: its first layout's background and first coloured part, as the list shows. */
private fun importColors(check: ImportCheck): Pair<Color, Color> {
    val layout = (check as? ImportCheck.Own)?.skin?.layouts?.values?.firstOrNull()
    val body = layout?.background?.color
    val key = layout?.elements?.firstNotNullOfOrNull { e -> e.colors.values.firstOrNull() }
    return Color(body?.toLong()?.and(0xFFFFFFFFL) ?: 0xFF24242C) to Color(key?.toLong()?.and(0xFFFFFFFFL) ?: 0xFF6E6E7C)
}

@Composable
private fun refusal(check: ImportCheck.Refused): String = when (check.reason) {
    ImportRefusal.NOT_A_SKIN -> stringResource(R.string.skins_import_not_skin)
    ImportRefusal.BROKEN -> stringResource(R.string.skins_import_broken, check.detail.orEmpty())
    ImportRefusal.OTHER_CONSOLE -> stringResource(R.string.skins_import_other_console, check.console?.displayName.orEmpty())
    ImportRefusal.TOO_LARGE -> stringResource(R.string.skins_import_too_large)
    ImportRefusal.UNSAFE -> stringResource(R.string.skins_import_unsafe)
}
