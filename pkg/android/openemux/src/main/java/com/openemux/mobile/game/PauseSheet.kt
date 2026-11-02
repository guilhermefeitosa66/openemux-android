package com.openemux.mobile.game

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.NdsShown
import com.openemux.mobile.data.SHADERS
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.launch.TABLETOP
import com.openemux.mobile.ui.DISABLED_ALPHA
import com.openemux.mobile.ui.FocusOutset
import com.openemux.mobile.ui.HintBar
import com.openemux.mobile.ui.MarginFocusScroll
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.ListRow
import com.openemux.mobile.ui.LocalWindowLayout
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OpenEmuxTheme
import com.openemux.mobile.ui.RadioCard
import com.openemux.mobile.ui.Tag
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.forbiddenBand
import com.openemux.mobile.ui.formatAgo
import com.openemux.mobile.ui.gamepadFocus
import com.openemux.mobile.ui.restorableFocus
import com.openemux.mobile.ui.tabletop
import com.retroarch.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PauseSession(
    val title: String,
    val cartPath: String?,
    val statesDir: File,
    val romBase: String,
    val shadersDir: File,
    val shader: String,
    val coreName: String,
    val startedAt: Long,
    /** RetroAchievements hardcore: the engine refuses loading states and slow motion; the app leaves fast forward out too. */
    val hardcore: Boolean = false,
    /** The engine's speed as the sheet opens: fast forward, slow motion or neither (GameActivity.onToggleStates). */
    val speed: Speed = Speed.NORMAL,
) {
    data class State(val slot: Int, val file: File, val thumb: File?, val modified: Long)

    fun states(): List<State> {
        val prefix = "$romBase.state"
        return statesDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".png") }
            .mapNotNull { f ->
                val suffix = f.name.removePrefix(prefix)
                val slot = when {
                    suffix.isEmpty() -> 0
                    suffix == ".auto" -> -1
                    suffix.all { it.isDigit() } && suffix.toInt() != com.openemux.mobile.data.SCRATCH_SLOT -> suffix.toInt()
                    else -> return@mapNotNull null
                }
                State(slot, f, File(f.path + ".png").takeIf { it.exists() }, f.lastModified())
            }
            .sortedByDescending { it.modified }
    }

    /** Copies the auto state where a slot command can reach it. */
    fun stageAuto(st: State): Int {
        val slot = com.openemux.mobile.data.SCRATCH_SLOT
        st.file.copyTo(File(statesDir, "$romBase.state$slot"), overwrite = true)
        return slot
    }

    companion object {
        fun fromIntent(intent: Intent, startedAt: Long, speed: Speed = Speed.NORMAL) = PauseSession(
            title = intent.getStringExtra(GameExtras.TITLE) ?: "",
            cartPath = intent.getStringExtra(GameExtras.CART),
            statesDir = File(intent.getStringExtra(GameExtras.STATES_DIR) ?: "/"),
            romBase = intent.getStringExtra(GameExtras.ROM_BASE) ?: "",
            shadersDir = File(intent.getStringExtra(GameExtras.SHADERS_DIR) ?: "/"),
            shader = intent.getStringExtra(GameExtras.SHADER) ?: "none",
            coreName = intent.getStringExtra(GameExtras.CORE_NAME) ?: "",
            startedAt = startedAt,
            hardcore = intent.getBooleanExtra(GameExtras.HARDCORE, false),
            speed = speed,
        )
    }
}

/**
 * Survives the dialog: toggles the engine keeps for the whole session. Fast forward and slow motion are not here: the
 * engine reports them (PauseSession.speed), so the sheet agrees with the skin's strip and the gamepad hotkeys.
 */
private object SessionToggles {
    var muted = false
    var shader: String? = null
}

private enum class Page { Main, States, Shader, Screens }

/**
 * The NDS screens as the sheet shows them: the layout in use for its orientation ([shown]), kept by the activity
 * whatever changed it; [cycles], bumped when R3 steps the layout with the sheet open; [pick] puts the screens on a
 * layout of the orientation shown.
 */
class NdsScreens(
    val shown: State<NdsShown>,
    val cycles: State<Int>,
    val pick: (ScreenLayout) -> Unit,
)

@Composable
fun PauseSheet(
    session: PauseSession,
    onResume: () -> Unit,
    onQuit: () -> Unit,
    /** NDS: the screen layouts, the one in use and how to change it; null for other consoles. */
    screens: NdsScreens? = null,
    /**
     * The speed the fast-forward and slow-motion tiles now show. Sent by the activity after the sheet closes: the
     * engine refuses either while paused.
     */
    onSpeed: (Speed) -> Unit = {},
    /** Closes the sheet and opens RetroArch's own menu over the game, for advanced settings. */
    onRetroArchMenu: () -> Unit = {},
) {
    OpenEmuxTheme {
        var page by remember { mutableStateOf(Page.Main) }
        var toast by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        // Flipped at once on a tap; the engine's own value comes in again the next time the sheet opens.
        var speed by remember { mutableStateOf(if (session.hardcore) Speed.NORMAL else session.speed) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val states = remember(refresh) { session.states() }
        val cart = remember { session.cartPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
        val window = LocalWindowLayout.current
        // Half open with the fold across, the menu takes the bottom half and the game shows on the top one (DobFoldPausa).
        val band = forbiddenBand(window)?.takeIf { window.tabletop }
        val tabletop = band != null
        // A tablet gets a side panel on the right, the game still showing on the left (TabPausa).
        val panel = window.tablet && !tabletop
        val panelWidth = min(440.dp, (window.widthDp * 0.55f).dp)
        // A wide window (a phone on its side) is short: the main page goes in two columns when they fit, and whatever still overflows scrolls.
        val landscape = window.wide && !panel

        fun command(cmd: String, message: String? = null, after: () -> Unit = {}) {
            scope.launch {
                withContext(Dispatchers.IO) { RaCommand.send(cmd) }
                if (message != null) toast = message
                after()
            }
        }

        fun load(st: PauseSession.State) {
            scope.launch {
                val reply = withContext(Dispatchers.IO) {
                    val slot = if (st.slot < 0) session.stageAuto(st) else st.slot
                    RaCommand.ask("LOAD_STATE_SLOT $slot", 3000)
                }
                if (reply?.contains("ERROR") == true) toast = context.getString(R.string.game_toast_state_other_core)
                else onResume()
            }
        }

        LaunchedEffect(toast) {
            if (toast != null) { delay(1800); toast = null }
        }
        // R3 stepped the layout with the sheet open: name the one it landed on.
        val cycles = screens?.cycles?.value ?: 0
        LaunchedEffect(cycles) {
            if (cycles > 0 && screens != null) {
                screenLayout(screens.shown.value)?.let { toast = context.getString(R.string.game_toast_screens_layout, context.getString(it.label)) }
            }
        }
        // B on a gamepad arrives as BACK: out of a sub-page first, then back to the game.
        BackHandler { if (page != Page.Main) page = Page.Main else onResume() }

        Box(Modifier.fillMaxSize()) {
            // In tabletop the band takes no tap: only the top half's card resumes.
            Box(
                Modifier.fillMaxSize().background(Color(0x8C08080A)).let {
                    if (tabletop) it else it.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onResume)
                }
            )
            if (band != null) {
                PausedCard(session, cart, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(band.startDp.dp), onResume)
            }
            if (panel) {
                PausedArea(
                    if (session.hardcore) emptyList() else states.take(4),
                    Modifier.fillMaxSize().padding(end = panelWidth),
                    ::load,
                )
            }
            val shape = if (panel) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                if (band != null) {
                    // Nothing in the band: the panel starts under it.
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((window.heightDp - band.endDp).dp)
                        .clip(shape)
                        .background(Oe.Sheet)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .navigationBarsPadding()
                        .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 12.dp)
                } else if (panel) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelWidth)
                        .shadow(24.dp, shape)
                        .clip(shape)
                        .background(Oe.Sheet)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .widthIn(max = if (landscape) 760.dp else 520.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Oe.Sheet)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
                },
            ) {
                if (!panel && !tabletop) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(CircleShape).background(Oe.Chevron))
                    Spacer(Modifier.height(14.dp))
                }
                // In tabletop the top half's card names the game: the main page needs no header.
                if (!tabletop || page != Page.Main) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page != Page.Main) {
                        SheetIconButton(Icons.Back, stringResource(R.string.game_back)) { page = Page.Main }
                    } else if (cart != null) {
                        Image(cart, null, Modifier.width(if (landscape) 56.dp else 92.dp), contentScale = ContentScale.FillWidth)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (page) {
                                Page.Main -> session.title
                                Page.States -> stringResource(R.string.game_save_states)
                                Page.Shader -> stringResource(R.string.game_shader)
                                Page.Screens -> stringResource(R.string.game_screens)
                            },
                            style = Type.Title.copy(fontSize = 17.sp, lineHeight = 21.sp),
                            maxLines = 2,
                        )
                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when (page) {
                                    Page.Main -> playingFor(session)
                                    Page.States -> stringResource(R.string.game_tap_to_load)
                                    Page.Shader -> stringResource(R.string.game_shader_this_session)
                                    Page.Screens -> stringResource(rememberedFor(screens?.shown?.value?.orientation))
                                },
                                style = Type.Small, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (page == Page.Main && session.hardcore) Tag(stringResource(R.string.game_hardcore_tag), Oe.WarnBg, Oe.WarnText)
                        }
                    }
                    SheetIconButton(Icons.Close, stringResource(R.string.game_back_to_game), onResume)
                }

                // On the panel the page fills the height, so Quit stays at its foot.
                MarginFocusScroll { Column(Modifier.weight(1f, fill = panel || tabletop).verticalScroll(rememberScrollState())) {
                if (toast != null) {
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Oe.OkBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Check, Oe.OkText, 18.dp, stroke = 2.4f)
                        Text(toast!!, style = Type.Body.copy(color = Oe.OkText, fontSize = 14.sp))
                    }
                }

                AnimatedContent(
                    page,
                    transitionSpec = { (fadeIn(tween(160)) + slideInVertically { it / 12 }) togetherWith fadeOut(tween(120)) },
                    label = "page",
                ) { current ->
                    when (current) {
                        Page.Main -> MainPage(
                            landscape = landscape,
                            tabletop = tabletop,
                            onResume = onResume,
                            hardcore = session.hardcore,
                            speed = speed,
                            stateCount = states.count { !it.isAutoSlot() },
                            onSave = {
                                val next = (states.filter { it.slot > 0 }.maxOfOrNull { it.slot } ?: 0) + 1
                                command("SAVE_STATE_SLOT $next", context.getString(R.string.game_toast_saved_slot, next)) {
                                    scope.launch { delay(600); refresh++ }
                                }
                            },
                            onLoad = { page = Page.States },
                            onSpeed = { tapped ->
                                speed = speed.tap(tapped)
                                onSpeed(speed)
                                toast = context.getString(
                                    when (speed) {
                                        Speed.FAST -> R.string.game_toast_fast_forward_on
                                        Speed.SLOW -> R.string.game_toast_slow_motion_on
                                        Speed.NORMAL -> R.string.game_toast_fast_forward_off
                                    }
                                )
                            },
                            onScreenshot = { command("SCREENSHOT", context.getString(R.string.game_toast_screenshot)) },
                            screens = screens?.let { screenLayout(it.shown.value) },
                            onScreens = { page = Page.Screens },
                            onShader = { page = Page.Shader },
                            onAudio = {
                                SessionToggles.muted = !SessionToggles.muted
                                command("MUTE", context.getString(if (SessionToggles.muted) R.string.game_toast_sound_off else R.string.game_toast_sound_on))
                            },
                            onReset = { command("RESET") { onResume() } },
                            onRetroArchMenu = onRetroArchMenu,
                            onQuit = onQuit.takeUnless { panel },
                        )
                        Page.States -> StatesPage(states, ::load)
                        Page.Screens -> if (screens != null) ScreensPage(screens.shown.value) { layout ->
                            screens.pick(layout)
                            page = Page.Main
                            toast = context.getString(R.string.game_toast_screens_layout, context.getString(layout.label))
                        }
                        Page.Shader -> ShaderPage(SessionToggles.shader ?: session.shader) { id ->
                            SessionToggles.shader = id
                            val preset = SHADERS.first { it.id == id }.preset
                            val arg = preset?.let { " " + File(session.shadersDir, it).absolutePath } ?: ""
                            command("SET_SHADER$arg", context.getString(R.string.game_toast_shader, SHADERS.first { it.id == id }.name(context)))
                        }
                    }
                }
                } }
                if (panel && page == Page.Main) QuitButton(Modifier.padding(top = 12.dp), onQuit)
                HintBar(
                    listOfNotNull(
                        "A" to stringResource(R.string.game_choose),
                        "B" to stringResource(R.string.game_back),
                        ("R3" to stringResource(R.string.game_hint_next_layout)).takeIf { screens != null && page == Page.Main },
                        "START" to stringResource(R.string.game_back_to_game),
                    ),
                    Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun PauseSession.State.isAutoSlot() = slot < 0

@Composable
private fun SheetIconButton(path: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).gamepadFocus(corner = 22.dp).clip(CircleShape).background(Oe.Raised).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(path, Oe.Text, 18.dp, stroke = 2.4f) }
}

@Composable
private fun MainPage(
    landscape: Boolean,
    /** The bottom half of a half-open fold (DobFoldPausa): three rows, Back to game and Quit in them. */
    tabletop: Boolean,
    onResume: () -> Unit,
    hardcore: Boolean,
    speed: Speed,
    stateCount: Int,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    /** The fast-forward (FAST) or slow-motion (SLOW) tile was tapped. */
    onSpeed: (Speed) -> Unit,
    onScreenshot: () -> Unit,
    onShader: () -> Unit,
    onAudio: () -> Unit,
    /** NDS: the layout in use, which the Screens tile names; null for other consoles (no tile). */
    screens: ScreenLayout?,
    onScreens: () -> Unit,
    onReset: () -> Unit,
    onRetroArchMenu: () -> Unit,
    /** Null on the tablet panel, which pins Quit at its foot. */
    onQuit: (() -> Unit)?,
) {
    val note = @Composable {
        if (hardcore) {
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Oe.WarnBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Warn, Oe.WarnText, 18.dp, stroke = 2.2f)
                    Text(stringResource(R.string.game_hardcore_note), style = Type.Body.copy(color = Oe.WarnText, fontSize = 13.sp, lineHeight = 18.sp))
                }
        }
    }
    val save = @Composable { modifier: Modifier ->
        BigButton(stringResource(R.string.game_save_state), Icons.SaveState, Oe.Accent, Color.White, modifier.restorableFocus("save", remember { mutableStateOf<String?>(null) }, initial = true), onSave)
    }
    val load = @Composable { modifier: Modifier ->
        BigButton(stringResource(R.string.game_load), Icons.LoadState, Oe.Raised, Oe.Text, modifier, onLoad, enabled = !hardcore)
    }
    val tiles = @Composable { modifier: Modifier ->
        val items = listOfNotNull<@Composable () -> Unit>(
            { ActionTile(stringResource(R.string.game_fast_forward), Icons.FastForward, speed == Speed.FAST, filled = true, enabled = !hardcore) { onSpeed(Speed.FAST) } },
            { ActionTile(stringResource(R.string.game_slow_motion), Icons.SlowMotion, speed == Speed.SLOW, enabled = !hardcore) { onSpeed(Speed.SLOW) } },
            { ActionTile(stringResource(R.string.game_screenshot), Icons.Camera, false, onClick = onScreenshot) },
            { ActionTile(stringResource(R.string.game_shader), Icons.Shader, false, onClick = onShader) },
            screens?.let { layout -> { ActionTile(stringResource(R.string.game_screens), Icons.Screens, false, detail = stringResource(layout.label), onClick = onScreens) } },
            { ActionTile(stringResource(if (SessionToggles.muted) R.string.game_muted else R.string.game_audio), if (SessionToggles.muted) Icons.Mute else Icons.Audio, SessionToggles.muted, onClick = onAudio) },
        )
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val rows = tileRows(items.size, maxWidth.value)
            if (rows == 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { items.forEach { it() } }
            } else {
                // Too narrow for one row: rows of equal columns, full-size tiles and full labels.
                val perRow = tilesPerRow(items.size, rows)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.chunked(perRow).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { it() } }
                            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (tabletop) {
        Column {
            note()
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                save(Modifier.weight(1f))
                load(Modifier.weight(1f))
                BigButton(stringResource(R.string.game_back_to_game), Icons.Play, Oe.Raised, Oe.Text, Modifier.width(168.dp), onResume)
            }
            tiles(Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("${stringResource(R.string.game_save_states)} · $stateCount", Icons.Chevron, Oe.Raised, Oe.Text, Modifier.weight(1f), onLoad, enabled = !hardcore)
                BigButton(stringResource(R.string.game_restart), Icons.Reset, Oe.Raised, Oe.Text, Modifier.weight(1f), onReset)
                if (onQuit != null) QuitButton(Modifier.width(168.dp).height(56.dp), onQuit)
            }
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Raised)) {
                RetroArchMenuRow(divider = false, minHeight = 56.dp, onClick = onRetroArchMenu)
            }
        }
        return
    }
    val actions = @Composable { modifier: Modifier ->
        Column(modifier) {
            note()
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                save(Modifier.weight(1f))
                load(Modifier.weight(1f))
            }
            tiles(Modifier.padding(top = 18.dp))
        }
    }
    val rest = @Composable { modifier: Modifier ->
        Column(modifier) {
            Column(Modifier.padding(top = if (landscape) 16.dp else 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Raised)) {
                ListRow(stringResource(R.string.game_save_states), value = stateCount.toString(), onClick = onLoad, enabled = !hardcore)
                ListRow(stringResource(R.string.game_restart), divider = true, onClick = onReset)
                RetroArchMenuRow(divider = true, onClick = onRetroArchMenu)
            }
            if (onQuit != null) QuitButton(Modifier.padding(top = 12.dp), onQuit)
        }
    }
    if (landscape) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            actions(Modifier.weight(1f))
            rest(Modifier.weight(1f))
        }
    } else {
        Column {
            actions(Modifier)
            rest(Modifier)
        }
    }
}

/** The advanced way out: RetroArch's own menu, its changes kept for this session only (the subtitle says so). */
@Composable
private fun RetroArchMenuRow(divider: Boolean, minHeight: Dp = 50.dp, onClick: () -> Unit) {
    ListRow(
        stringResource(R.string.game_retroarch_menu),
        subtitle = stringResource(R.string.game_retroarch_menu_note),
        divider = divider,
        minHeight = minHeight,
        onClick = onClick,
    )
}

@Composable
private fun QuitButton(modifier: Modifier, onQuit: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(Oe.DangerBg)
            .clickable(role = Role.Button, onClick = onQuit),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(R.string.game_quit), style = Type.Button.copy(color = Oe.Danger, fontSize = 15.sp)) }
}

/**
 * The top half of a half-open fold, over the dimmed game: the "Paused" chip and the game's card. A tap anywhere
 * resumes; nothing here takes the gamepad's focus, which stays in the bottom half.
 */
@Composable
private fun PausedCard(session: PauseSession, cart: ImageBitmap?, modifier: Modifier, onResume: () -> Unit) {
    Box(modifier.windowInsetsPadding(WindowInsets.displayCutout).pointerInput(onResume) { detectTapGestures { onResume() } }.padding(24.dp)) {
        Text(
            stringResource(R.string.game_paused).uppercase(),
            style = Type.Tag.copy(color = Color.White),
            modifier = Modifier.align(Alignment.TopStart).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Row(
            Modifier.align(Alignment.Center).widthIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (cart != null) Image(cart, null, Modifier.width(72.dp), contentScale = ContentScale.FillWidth)
            Column {
                Text(session.title, style = Type.Title.copy(color = Color.White, fontSize = 17.sp, lineHeight = 21.sp), maxLines = 2)
                Text(
                    playingFor(session),
                    style = Type.Small.copy(color = Color(0xB3FFFFFF)), modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** How long the game has been played, and on which core: the sheet's header and the tabletop card. */
@Composable
private fun playingFor(session: PauseSession): String {
    val minutes = ((System.currentTimeMillis() - session.startedAt) / 60000).coerceAtLeast(0)
    return if (minutes < 1) stringResource(R.string.game_playing_now, session.coreName) else stringResource(R.string.game_playing_for, minutes.toInt(), session.coreName)
}

/** Left of the tablet panel, over the dimmed game: the "Paused" chip and the latest saved states, a tap loads one. */
@Composable
private fun PausedArea(states: List<PauseSession.State>, modifier: Modifier, onPick: (PauseSession.State) -> Unit) {
    Box(modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(28.dp)) {
        Text(
            stringResource(R.string.game_paused).uppercase(),
            style = Type.Tag.copy(color = Color.White),
            modifier = Modifier.align(Alignment.TopStart).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (states.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(stringResource(R.string.game_save_states), style = Type.Title.copy(color = Color.White, fontSize = 15.sp))
                Text(stringResource(R.string.game_tap_to_load), style = Type.Small.copy(color = Color(0xB3FFFFFF)), modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    states.forEach { st ->
                        StateCard(st, Modifier.weight(1f, fill = false).widthIn(max = 140.dp), Color(0xB3FFFFFF)) { onPick(st) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier.height(56.dp).let { if (enabled) it.gamepadFocus(corner = 16.dp) else it.alpha(DISABLED_ALPHA) }
            .clip(RoundedCornerShape(16.dp)).background(bg).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, fg, 22.dp, filled = icon == Icons.SaveState || icon == Icons.LoadState)
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.Button.copy(color = fg, fontSize = 15.sp))
    }
}

@Composable
private fun ActionTile(label: String, icon: String, on: Boolean, filled: Boolean = false, enabled: Boolean = true, detail: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.let { if (enabled) it.gamepadFocus(corner = 12.dp) else it.alpha(DISABLED_ALPHA) }
            .clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(if (on) Oe.AccentSoft else Oe.Surface2)
                .let { if (on) it.border(1.5.dp, Oe.AccentBright, RoundedCornerShape(18.dp)) else it },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, if (on) Oe.AccentOnSoft else Oe.Text, 22.dp, filled = filled) }
        Text(label, style = Type.Caption.copy(color = Oe.Text, fontWeight = FontWeight.Medium))
        // What the tile is set to now (the NDS layout), under its name: as narrow as the tile, two lines at most.
        if (detail != null) {
            Text(
                detail,
                style = Type.Caption.copy(color = Oe.Muted, fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center),
                maxLines = 2,
                modifier = Modifier.widthIn(max = 76.dp),
            )
        }
    }
}

@Composable
private fun StatesPage(states: List<PauseSession.State>, onPick: (PauseSession.State) -> Unit) {
    if (states.isEmpty()) {
        Text(stringResource(R.string.game_no_states), style = Type.Small, modifier = Modifier.padding(vertical = 28.dp))
        return
    }
    val focused = remember { mutableStateOf<String?>(null) }
    MarginFocusScroll { LazyVerticalGrid(
        GridCells.Adaptive(160.dp),
        Modifier.padding(top = 16.dp - FocusOutset).height(380.dp),
        contentPadding = PaddingValues(vertical = FocusOutset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(states) { st ->
            StateCard(st, Modifier.restorableFocus(st.file.path, focused, initial = st == states.first())) { onPick(st) }
        }
    } }
}

/** A saved state's thumbnail, its slot over it and its age under it; [ago] tints the age (white over the game). */
@Composable
private fun StateCard(st: PauseSession.State, modifier: Modifier, ago: Color? = null, onClick: () -> Unit) {
    Column(Modifier.gamepadFocus(corner = 10.dp).then(modifier).clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            val bmp = remember(st.thumb) { st.thumb?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (st.slot < 0) stringResource(R.string.game_state_auto) else stringResource(R.string.game_state_slot, st.slot), style = Type.Tag.copy(color = Color.White, fontSize = 10.sp))
            }
        }
        Text(formatAgo(st.modified), style = if (ago != null) Type.Caption.copy(color = ago) else Type.Caption, modifier = Modifier.padding(top = 5.dp, start = 2.dp))
    }
}

/** The layout [shown] names, or null when its orientation has no such layout. */
private fun screenLayout(shown: NdsShown): ScreenLayout? = NdsLayouts.find(shown.orientation, shown.layout)

/** The Screens page's subtitle: the orientation the choice is remembered for. */
@StringRes
private fun rememberedFor(orientation: String?): Int = when (orientation) {
    "landscape" -> R.string.game_screens_remembered_landscape
    TABLETOP -> R.string.game_screens_remembered_tabletop
    else -> R.string.game_screens_remembered_portrait
}

/** The NDS layouts of the orientation [shown] is in, the one in use checked and focused first; [onPick] applies one. */
@Composable
private fun ScreensPage(shown: NdsShown, onPick: (ScreenLayout) -> Unit) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val focused = remember { mutableStateOf<String?>(null) }
        NdsLayouts.of(shown.orientation).forEach { layout ->
            val current = layout.id == shown.layout
            Box(Modifier.restorableFocus(layout.id, focused, initial = current)) {
                RadioCard(
                    current, stringResource(layout.label), "", onClick = { onPick(layout) }, background = Oe.Raised,
                    badge = if (current) stringResource(R.string.game_screens_current) else null,
                    leading = { LayoutGlyph(layout.id, landscape = shown.orientation == "landscape", on = current) },
                )
            }
        }
    }
}

/**
 * A layout drawn small: the screens it shows filled, a hidden one dashed; one over the other, or side by side for
 * "side". Decorative: the card's name says it.
 */
@Composable
private fun LayoutGlyph(id: String, landscape: Boolean, on: Boolean) {
    val fill = if (on) Oe.AccentOnSoft else Oe.Text
    val hidden = Oe.Chevron
    Canvas(Modifier.size(if (landscape) 32.dp else 28.dp, if (landscape) 24.dp else 32.dp)) {
        val gap = 2.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())
        val stroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
        if (id == "side") {
            val w = (size.width - gap) / 2f
            val h = w * 0.75f
            val top = (size.height - h) / 2f
            drawRoundRect(fill, Offset(0f, top), Size(w, h), corner)
            drawRoundRect(fill, Offset(w + gap, top), Size(w, h), corner)
            return@Canvas
        }
        val h = (size.height - gap) / 2f
        val w = minOf(size.width, h / 0.75f)
        val left = (size.width - w) / 2f
        listOf(id != "bottom", id != "top").forEachIndexed { i, shownScreen ->
            val origin = Offset(left, i * (h + gap))
            if (shownScreen) drawRoundRect(fill, origin, Size(w, h), corner)
            else drawRoundRect(hidden, origin + Offset(0.75.dp.toPx(), 0.75.dp.toPx()), Size(w - 1.5.dp.toPx(), h - 1.5.dp.toPx()), corner, style = stroke)
        }
    }
}

@Composable
private fun ShaderPage(current: String, onPick: (String) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val focused = remember { mutableStateOf<String?>(null) }
        SHADERS.forEach { s ->
            Box(Modifier.restorableFocus(s.id, focused, initial = s.id == current)) {
                RadioCard(selected == s.id, s.name(), shaderBlurb(s.id), onClick = { selected = s.id; onPick(s.id) }, background = Oe.Raised)
            }
        }
    }
}

@Composable
fun shaderBlurb(id: String) = when (id) {
    "none" -> stringResource(R.string.game_shader_blurb_none)
    "dot" -> stringResource(R.string.game_shader_blurb_dot)
    "sharp" -> stringResource(R.string.game_shader_blurb_sharp)
    "zfast" -> stringResource(R.string.game_shader_blurb_zfast)
    "geom" -> stringResource(R.string.game_shader_blurb_geom)
    else -> ""
}
