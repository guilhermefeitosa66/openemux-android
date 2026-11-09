package com.openemux.mobile.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.openemux.mobile.AppLocale
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.launch.SkinConversion
import com.openemux.mobile.ui.ButtonLabelsPref
import com.openemux.mobile.ui.ControllerLayoutPref
import com.openemux.mobile.ui.LocalPadScope
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OpenEmuxTheme
import com.openemux.mobile.ui.PadScope
import com.openemux.mobile.ui.ProvideWindowLayout
import com.openemux.mobile.ui.SkinEditTarget
import com.openemux.mobile.ui.SkinEditor
import com.openemux.mobile.ui.SkinEditorHost
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.padRoot
import com.openemux.mobile.ui.readSkinList
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The skin editor over a game left paused under its sheet (issue 827). An activity of its own because the sheet's
 * window never takes focus (the engine has to keep it) and the game's is a NativeActivity: the editor needs a keyboard
 * and a file picker. In the game's process, so the game is never a background process that could be reclaimed, and
 * translucent, so the game stays started and its surface is kept.
 */
class GameSkinActivity : ComponentActivity() {
    private val padScope = PadScope()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePref.load(this)
        ControllerLayoutPref.load(this)
        ButtonLabelsPref.load(this)
        Oe.syncSystem(this)
        super.onCreate(savedInstanceState)
        val console = Console.byId(intent.getStringExtra(GameExtras.CONSOLE))
        val base = intent.getStringExtra(GameExtras.SKINS_BASE)?.let(::File)
        if (console == null || base == null) {
            finish()
            return
        }
        val variant = intent.getStringExtra(GameExtras.SKIN_VARIANT) ?: SkinVariant.CONSOLE.id
        val custom = intent.getStringExtra(GameExtras.SKIN_CUSTOM)
        setContent {
            CompositionLocalProvider(LocalPadScope provides padScope) {
                Box(Modifier.padRoot(padScope)) {
                    ProvideWindowLayout { OpenEmuxTheme { Editing(console, SkinStore(base), variant, custom) } }
                }
            }
        }
    }

    @Composable
    private fun Editing(console: Console, store: SkinStore, variant: String, custom: String?) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var session by remember { mutableStateOf<SkinEditSession?>(null) }
        LaunchedEffect(Unit) {
            val opened = withContext(Dispatchers.IO) {
                runCatching { open(context, console, store, variant, custom) }
                    .onFailure { Log.e(TAG, "no skin to edit for ${console.id}", it) }.getOrNull()
            }
            if (opened == null) {
                Toast.makeText(context, R.string.skins_error, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                session = opened
            }
        }
        val open = session ?: return
        val host = remember(open) { SkinEditorHost(store, prepare = {}, use = { _, ref -> open.usedNew(ref) }) }
        SkinEditor(host, console, open.target, onChanged = open::changed, onClose = {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { open.close() }.onFailure { Log.w(TAG, "copy not removed", it) } }
                val ref = open.result()
                if (ref != null) setResult(RESULT_OK, Intent().putExtra(GameExtras.SKIN_CUSTOM, ref))
                finish()
            }
        })
    }

    /** The session for the skin in use, its copy made when it needs one. Blocks: call off the main thread. */
    private fun open(context: Context, console: Console, store: SkinStore, variant: String, custom: String?): SkinEditSession? {
        val state = readSkinList(context, store, console).copy(bundledId = variant, custom = custom)
        return when (val skin = skinToEdit(state)) {
            null -> null
            is SkinToEdit.Own -> SkinEditSession(store, console, SkinEditTarget(skin.skin.id, skin.skin.name, inUse = true), copy = false)
            is SkinToEdit.Bundled -> {
                val label = context.getString(skin.swatch.variant.label)
                val name = store.freeName(console, context.getString(R.string.skins_edited_name, label))
                session(store, console, SkinConversion.convert(context, store, console, skin.swatch.variant, name), name)
            }
            is SkinToEdit.Imported -> {
                val name = store.freeName(console, context.getString(R.string.skins_copy_name, skin.skin.name).take(SkinStore.MAX_NAME))
                session(store, console, SkinConversion.duplicatePackage(store, console, skin.skin.id, name), name)
            }
        }
    }

    private fun session(store: SkinStore, console: Console, ref: String, name: String): SkinEditSession? =
        SkinStore.parse(ref)?.let { (_, id) -> SkinEditSession(store, console, SkinEditTarget(id, name, inUse = true), copy = true) }

    private companion object {
        const val TAG = "OpenEmuxSkin"
    }
}
