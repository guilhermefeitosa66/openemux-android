package com.openemux.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.launch.ImportedSkinRenderer
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a console's skin choice offers (issue 718): [dark], a Dark package ships for it; [imported], the user's own skin wins. */
data class SkinChoice(val dark: Boolean, val imported: Boolean)

/**
 * The value the skin row shows for a console: the user's imported skin when one replaces the bundled ones,
 * else the variant picked ([skin], its saved id); null when the console has nothing to pick from.
 */
@StringRes
fun skinValueLabel(choice: SkinChoice?, skin: String): Int? = when {
    choice == null -> null
    choice.imported -> R.string.settings_skin_imported_value
    choice.dark -> SkinVariant.byId(skin).label
    else -> null
}

/** [SkinChoice] for [console], read off the main thread; null until it is known. */
@Composable
fun rememberSkinChoice(library: Library, console: Console): SkinChoice? {
    val context = LocalContext.current
    val choice by produceState<SkinChoice?>(null, console) {
        value = withContext(Dispatchers.IO) {
            SkinChoice(
                ImportedSkinRenderer.bundledName(context.assets.list(SkinVariant.DARK.assetDir), console) != null,
                ImportedSkinRenderer.imported(library.paths.base, console) != null,
            )
        }
    }
    return choice
}

/** One card per [SkinVariant]; picking one saves it for every game of [console]. */
@Composable
fun SkinCards(library: Library, console: Console, skin: String, imported: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinVariant.entries.forEach { v ->
                RadioCard(
                    SkinVariant.byId(skin) == v, stringResource(v.label), stringResource(v.description),
                    onClick = { library.updateConsole(console) { it.copy(skin = v.id) } },
                )
            }
        }
        if (imported) {
            Text(stringResource(R.string.settings_skin_imported, console.short), style = Type.Small, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** The skin choice for [console] in a sheet, for where the cards do not fit inline (a game's detail screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(library: Library, console: Console, skin: String, imported: Boolean, onClose: () -> Unit) {
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.settings_default_skin), style = Type.Title.copy(fontSize = 22.sp))
            Text(stringResource(R.string.settings_skin_sheet_desc, console.short), style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            SkinCards(library, console, skin, imported)
        }
    }
}
