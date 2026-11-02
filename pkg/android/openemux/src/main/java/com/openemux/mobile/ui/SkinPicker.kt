package com.openemux.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.Library
import com.openemux.mobile.data.SkinCatalog
import com.openemux.mobile.data.SkinSwatch
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.launch.ImportedSkinRenderer
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What a console's skin choice offers: [options], the variants the app ships a package of for it, in the picker's
 * order (its own colors, Dark, issue 718, and its official colors, issue 720); [imported], the user's own skin wins.
 */
data class SkinChoice(val options: List<SkinSwatch>, val imported: Boolean) {
    /** More than the console's own colors: there is something to pick. */
    val pickable: Boolean get() = options.size > 1

    /** The option [skin] (a saved id) names; the console's own colors when it names none of them. */
    fun option(skin: String): SkinSwatch? = options.firstOrNull { it.variant.id == skin } ?: options.firstOrNull { it.variant == SkinVariant.CONSOLE }

    companion object {
        /**
         * The choice for [console], from the [catalog] (its JSON), keeping the variants [hasPackage] finds a package of.
         * No catalog lists the variants in their own order, without swatches.
         */
        fun of(catalog: String?, console: Console, imported: Boolean, hasPackage: (SkinVariant) -> Boolean): SkinChoice {
            val listed = SkinCatalog.parse(catalog, console).ifEmpty { SkinVariant.entries.map { SkinSwatch(it, null, null) } }
            return SkinChoice(listed.filter { hasPackage(it.variant) }, imported)
        }
    }
}

/**
 * The value the skin row shows for a console: the user's imported skin when one replaces the bundled ones,
 * else the variant picked ([skin], its saved id); null when the console has nothing to pick from.
 */
@StringRes
fun skinValueLabel(choice: SkinChoice?, skin: String): Int? = when {
    choice == null -> null
    choice.imported -> R.string.settings_skin_imported_value
    choice.pickable -> choice.option(skin)?.variant?.label
    else -> null
}

/** [SkinChoice] for [console], read off the main thread; null until it is known. */
@Composable
fun rememberSkinChoice(library: Library, console: Console): SkinChoice? {
    val context = LocalContext.current
    val choice by produceState<SkinChoice?>(null, console) {
        value = withContext(Dispatchers.IO) {
            val catalog = runCatching { context.assets.open(SkinCatalog.ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            SkinChoice.of(catalog, console, ImportedSkinRenderer.imported(library.paths.base, console) != null) {
                ImportedSkinRenderer.bundledName(context.assets.list(it.assetDir), console) != null
            }
        }
    }
    return choice
}

/** The shell's color with a button's on it, a small picture of the variant (issue 720). */
@Composable
fun SkinSwatchIcon(swatch: SkinSwatch) {
    val body = swatch.body ?: return
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(body)).border(1.dp, Oe.Border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.BottomEnd,
    ) {
        swatch.key?.let { Box(Modifier.padding(5.dp).size(13.dp).clip(CircleShape).background(Color(it))) }
    }
}

/** One card per variant [choice] offers, with its swatch; picking one saves it for every game of [console]. */
@Composable
fun SkinCards(library: Library, console: Console, skin: String, choice: SkinChoice, modifier: Modifier = Modifier) {
    val picked = choice.option(skin)
    Column(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choice.options.forEach { o ->
                val v = o.variant
                RadioCard(
                    o == picked, stringResource(v.label), v.description?.let { stringResource(it) }.orEmpty(),
                    onClick = { library.updateConsole(console) { it.copy(skin = v.id) } },
                    leading = { SkinSwatchIcon(o) },
                )
            }
        }
        if (choice.imported) {
            Text(stringResource(R.string.settings_skin_imported, console.short), style = Type.Small, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** The skin choice for [console] in a sheet, for where the cards do not fit inline (a game's detail screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(library: Library, console: Console, skin: String, choice: SkinChoice, onClose: () -> Unit) {
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.settings_default_skin), style = Type.Title.copy(fontSize = 22.sp))
            Text(stringResource(R.string.settings_skin_sheet_desc, console.short), style = Type.Small, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            SkinCards(library, console, skin, choice)
        }
    }
}
