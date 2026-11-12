package com.openemux.mobile.game

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.NdsShown
import com.openemux.mobile.data.SHADERS
import com.openemux.mobile.data.SaveSlots
import com.openemux.mobile.data.ShaderPicks
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.launch.TABLETOP
import com.openemux.mobile.ui.DISABLED_ALPHA
import com.openemux.mobile.ui.FocusOutset
import com.openemux.mobile.ui.HintBar
import com.openemux.mobile.ui.InputMode
import com.openemux.mobile.ui.MarginFocusScroll
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.ListRow
import com.openemux.mobile.ui.LocalWindowLayout
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OpenEmuxTheme
import com.openemux.mobile.ui.RadioCard
import com.openemux.mobile.ui.SkinChoices
import com.openemux.mobile.ui.SkinListState
import com.openemux.mobile.ui.StateTile
import com.openemux.mobile.ui.readSkinList
import com.openemux.mobile.ui.skinValue
import com.openemux.mobile.ui.stateDeleteBody
import com.openemux.mobile.ui.stateTag
import com.openemux.mobile.ui.Tag
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.holdGamepadFocus
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
import java.util.concurrent.Executors
import kotlin.concurrent.thread

data class PauseSession(
    val title: String,
    val cartPath: String?,
    val statesDir: File,
    val romBase: String,
    val shadersDir: File,
    val shader: String,
    /** The ROM's library key and the folder a shader picked here is saved through (ShaderPicks); empty/null: not saved. */
    val romKey: String = "",
    val shaderPicks: File? = null,
    val coreName: String,
    val startedAt: Long,
    /** RetroAchievements hardcore: the engine refuses loading states and slow motion; the app leaves fast forward out too. */
    val hardcore: Boolean = false,
    /** The engine's speed as the sheet opens: fast forward, slow motion or neither (GameActivity.onToggleStates). */
    val speed: Speed = Speed.NORMAL,
    /** Whether the game is muted as the sheet opens (GameActivity.onMuteChanged). */
    val muted: Boolean = false,
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
        fun fromIntent(intent: Intent, startedAt: Long, speed: Speed = Speed.NORMAL, muted: Boolean = false) = PauseSession(
            title = intent.getStringExtra(GameExtras.TITLE) ?: "",
            cartPath = intent.getStringExtra(GameExtras.CART),
            statesDir = File(intent.getStringExtra(GameExtras.STATES_DIR) ?: "/"),
            romBase = intent.getStringExtra(GameExtras.ROM_BASE) ?: "",
            shadersDir = File(intent.getStringExtra(GameExtras.SHADERS_DIR) ?: "/"),
            shader = intent.getStringExtra(GameExtras.SHADER) ?: "none",
            romKey = intent.getStringExtra(GameExtras.ROM_KEY) ?: "",
            shaderPicks = intent.getStringExtra(GameExtras.SHADER_PICKS)?.let(::File),
            coreName = intent.getStringExtra(GameExtras.CORE_NAME) ?: "",
            startedAt = startedAt,
            hardcore = intent.getBooleanExtra(GameExtras.HARDCORE, false),
            speed = speed,
            muted = muted,
        )
    }
}

/**
 * Survives the dialog: choices the sheet keeps for the whole session. Speed and mute are not here: the engine reports
 * them (PauseSession.speed, PauseSession.muted), so the sheet agrees with the skin's strip and the gamepad hotkeys.
 */
private object SessionToggles {
    /** The shader picked in this process, with the ROM it was picked for: a process reused for another game shows that game's own. */
    var shader: Pair<String, String>? = null
}

/**
 * The ROM's current slot (issue 857): the one the menu opens on, kept across openings, sessions and restarts. The value
 * chosen in this process wins over the file, whose write is still on its way; writes and the engine's slot go in order.
 */
private object CurrentSlot {
    private var chosen: Pair<String, Int>? = null
    private val writer by lazy { Executors.newSingleThreadExecutor { Thread(it, "current-slot") } }

    // The session too: a value chosen in an earlier game in this process must not outrank what the library wrote since.
    private fun key(session: PauseSession) = "${File(session.statesDir, session.romBase).path}@${session.startedAt}"

    fun of(session: PauseSession): Int =
        chosen?.takeIf { it.first == key(session) }?.second ?: SaveSlots.current(session.statesDir, session.romBase)

    /** Makes [slot] current, on disk and in the engine, whose Save and Load keys (strip, controller) use it. */
    fun choose(session: PauseSession, slot: Int) {
        if (slot !in SaveSlots.RANGE) return
        chosen = key(session) to slot
        writer.execute {
            SaveSlots.setCurrent(session.statesDir, session.romBase, slot)
            // Not waited for, so a quick run of arrows never queues the next write behind a reply. An engine without the
            // command ignores it and keeps the slot it was launched with.
            RaCommand.send("SET_STATE_SLOT $slot")
        }
    }
}

private enum class Page { Main, States, StateOptions, StateDelete, Shader, Screens, Skin }

/**
 * The console's skins as the sheet shows them (issue 827): [bundledId] and [custom] are the ones the game plays, kept by
 * the activity; [pick] plays another (a bundled id, or a player's skin ref with the bundled one kept to go back to);
 * [edit] opens the editor over the game.
 */
class GameSkins(
    val console: Console,
    val store: SkinStore,
    val bundledId: State<String>,
    val custom: State<String?>,
    val pick: (String, String?) -> Unit,
    val edit: () -> Unit,
)

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
    /** The console's skins and how to change or edit the one in use; null: the rows are not shown. */
    skins: GameSkins? = null,
    /**
     * The speed the fast-forward and slow-motion tiles now show. Sent by the activity after the sheet closes: the
     * engine refuses either while paused.
     */
    onSpeed: (Speed) -> Unit = {},
    /** The mute state the Audio tile just asked the engine for, so a sheet opened later starts from it. */
    onMute: (Boolean) -> Unit = {},
    /** Closes the sheet and opens RetroArch's own menu over the game, for advanced settings. */
    onRetroArchMenu: () -> Unit = {},
    /** A gamepad detected: the Touch controls tile and its state (issue 865); null: no tile. */
    touchControls: TouchToggle? = null,
    /**
     * Where the game's picture ends on screen, in pixels from its top (GameViewport); null: not known. A phone in
     * portrait opens the sheet under it (issue 849).
     */
    gameBottom: Float? = null,
) {
    OpenEmuxTheme {
        var page by remember { mutableStateOf(Page.Main) }
        var toast by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        // Flipped at once on a tap; the engine's own value comes in again the next time the sheet opens.
        var speed by remember { mutableStateOf(if (session.hardcore) Speed.NORMAL else session.speed) }
        var muted by remember { mutableStateOf(session.muted) }
        var touchOn by remember { mutableStateOf(touchControls?.on == true) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val states = remember(refresh) { session.states() }
        // The numbered slots holding a state: the slider fills them, Save and Load read the current one's (issue 867).
        val usedSlots = remember(states) { states.filterNot { it.isAutoSlot() }.mapTo(HashSet()) { it.slot } }
        // The ROM's current slot, which only the player changes (issue 857).
        var slot by remember { mutableIntStateOf(CurrentSlot.of(session)) }
        fun choose(next: Int) { if (next in SaveSlots.RANGE) { slot = next; CurrentSlot.choose(session, next) } }
        // The state the options and the confirmation pages are about.
        var chosen by remember { mutableStateOf<PauseSession.State?>(null) }
        fun options(st: PauseSession.State) { chosen = st; page = Page.StateOptions }
        // Here and not on the page, which the options replace: coming back lands on the same tile, scrolled as it was.
        val statesFocus = remember { mutableStateOf<String?>(null) }
        val statesGrid = remember(refresh) { LazyGridState() }
        val skinList = skins?.let { rememberGameSkinList(it) }
        // Whether the game has a skin in use, which brings the skin tiles; its name is not shown on the sheet (issue 875).
        val hasSkin = skinValue(skinList) != null
        val cart = remember { session.cartPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
        val window = LocalWindowLayout.current
        // Half open with the fold across, the menu takes the bottom half and the game shows on the top one (DobFoldPausa).
        val band = forbiddenBand(window)?.takeIf { window.tabletop }
        val layout = pauseLayout(tablet = window.tablet, tabletop = band != null, wide = window.wide)
        val tabletop = layout == PauseLayout.TABLETOP
        // A tablet gets a side panel on the right, the game still showing on the left (TabPausa).
        val panel = layout == PauseLayout.PANEL
        val panelWidth = min(440.dp, (window.widthDp * 0.55f).dp)
        // A wide window (a phone on its side) is short: the main page goes in two columns when they fit, and whatever still overflows scrolls.
        val landscape = layout == PauseLayout.LANDSCAPE
        // A phone upright: the sheet opens under the game, stretches up and is swiped down to resume (issue 849).
        val phone = layout == PauseLayout.PHONE
        val sheet = remember { SheetDrag() }
        val density = LocalDensity.current
        val flingPx = with(density) { SheetFling.toPx() }
        fun release(velocity: Float) = sheet.release(scope, velocity, flingPx, onResume)
        val grip = if (phone) {
            Modifier.draggable(
                rememberDraggableState { sheet.by(it) },
                Orientation.Vertical,
                onDragStopped = { release(it) },
            )
        } else Modifier
        // The content scrolls first; the sheet follows once it is at its top, and stretches before it scrolls up.
        val follow = remember(sheet) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = available.y
                    val take = (dy < 0f && sheet.top > sheet.reach) || (dy > 0f && sheet.moving != null)
                    return if (take) Offset(0f, sheet.by(dy)) else Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, sheet.by(available.y)) else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (sheet.moving == null) return Velocity.Zero
                    release(available.y)
                    return available
                }
            }
        }
        val cutout = WindowInsets.displayCutout
        val navigation = WindowInsets.navigationBars

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
                if (replyFailed(reply)) toast = context.getString(R.string.game_toast_state_other_core)
                else {
                    // A numbered state loaded here is the current slot from now on; the auto state is not a slot.
                    if (!st.isAutoSlot()) choose(st.slot)
                    onResume()
                }
            }
        }

        var confirmRestart by remember { mutableStateOf(false) }
        val restartEntry = remember { FocusRequester() }
        // Back on the entry that opened the dialog, for the pad: the dialog's own buttons are going away.
        fun focusRestartEntry() { if (InputMode.gamepad) runCatching { restartEntry.requestFocus() } }
        fun cancelRestart() { confirmRestart = false; focusRestartEntry() }

        fun restart() {
            // Closed before the reply: a second tap must not restart twice.
            confirmRestart = false
            scope.launch {
                val reply = withContext(Dispatchers.IO) { RaCommand.restart() }
                if (replyFailed(reply)) { toast = context.getString(R.string.game_toast_restart_failed); focusRestartEntry() }
                else onResume()
            }
        }

        LaunchedEffect(toast) {
            if (toast != null) { delay(1800); toast = null }
        }
        // R3 stepped the layout with this sheet open: name the one it landed on. The count outlives the sheet (it is
        // the activity's), so only a step since this sheet opened names anything.
        val cycles = screens?.cycles?.value ?: 0
        val cyclesAtOpen = remember { cycles }
        LaunchedEffect(cycles) {
            if (cycles != cyclesAtOpen && screens != null) {
                screenLayout(screens.shown.value)?.let { toast = context.getString(R.string.game_toast_screens_layout, context.getString(it.label)) }
            }
        }
        // The page a sub-page's back leads to; null on the main page.
        fun backPage(): Page? = when (page) {
            Page.Main -> null
            Page.StateDelete, Page.StateOptions -> Page.States
            else -> Page.Main
        }
        // B on a gamepad arrives as BACK: out of the dialog first, then a sub-page, then back to the game.
        BackHandler { if (confirmRestart) cancelRestart() else backPage()?.let { page = it } ?: onResume() }

        Box(Modifier.fillMaxSize().onGloballyPositioned { sheet.origin = it.localToScreen(Offset.Zero).y }) {
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
                    ::options,
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
                        .let {
                            if (!phone) it else it.sheetPlace(sheet, gameBottom, cutout, navigation).nestedScroll(follow)
                        }
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
                    // On a phone upright the pill is the handle the sheet is dragged by, with some room around it.
                    Box(Modifier.fillMaxWidth().height(19.dp).then(grip), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(Oe.Chevron))
                    }
                }
                // In tabletop the top half's card names the game: the main page needs no header.
                if (!tabletop || page != Page.Main) Row(grip, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page != Page.Main) {
                        SheetIconButton(Icons.Back, stringResource(R.string.game_back)) { backPage()?.let { page = it } }
                    } else if (cart != null) {
                        // Any short window keeps the small cart, a wide fold half open included (its half is short too).
                        Image(cart, null, Modifier.width(if (window.wide && !panel) 56.dp else 92.dp), contentScale = ContentScale.FillWidth)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (page) {
                                Page.Main -> session.title
                                Page.States -> stringResource(R.string.game_save_states)
                                Page.StateOptions -> chosen?.let { stateTag(it.slot) } ?: ""
                                Page.StateDelete -> stringResource(R.string.game_state_delete_title)
                                Page.Shader -> stringResource(R.string.game_shader)
                                Page.Screens -> stringResource(R.string.game_screens)
                                Page.Skin -> stringResource(R.string.game_change_skin)
                            },
                            style = Type.Title.copy(fontSize = 17.sp, lineHeight = 21.sp),
                            maxLines = 2,
                        )
                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when (page) {
                                    Page.Main -> playingFor(session)
                                    Page.States -> stringResource(R.string.game_tap_to_load)
                                    Page.StateOptions, Page.StateDelete -> chosen?.let { formatAgo(it.modified) } ?: ""
                                    Page.Shader -> stringResource(R.string.game_shader_this_game)
                                    Page.Screens -> stringResource(rememberedFor(screens?.shown?.value?.orientation))
                                    Page.Skin -> stringResource(R.string.game_skin_every_game)
                                },
                                style = Type.Small, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (page == Page.Main && session.hardcore) Tag(stringResource(R.string.game_hardcore_tag), Oe.WarnBg, Oe.WarnText)
                        }
                    }
                    SheetIconButton(Icons.Close, stringResource(R.string.game_back_to_game), onResume)
                }

                // On the panel and the fold the page fills the height.
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
                            quickRows = layout.quickRows,
                            tileColumns = tileColumns(layout),
                            onResume = onResume,
                            hardcore = session.hardcore,
                            speed = speed,
                            slot = slot,
                            slotInUse = slot in usedSlots,
                            usedSlots = usedSlots,
                            onSlot = { choose(it) },
                            onSave = {
                                val target = slot
                                scope.launch {
                                    // Answered once the state is on disk, which a big core takes a while to do. No answer is not taken as a failure.
                                    val reply = withContext(Dispatchers.IO) { RaCommand.ask("SAVE_STATE_SLOT $target", 8000) }
                                    toast = context.getString(if (replyFailed(reply)) R.string.game_toast_save_failed else R.string.game_toast_saved_slot, target)
                                    // The engine writes the picture after answering.
                                    delay(600)
                                    refresh++
                                }
                            },
                            onSeeAll = { page = Page.States },
                            onLoadSlot = { states.firstOrNull { it.slot == slot }?.let(::load) },
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
                            nds = screens != null,
                            onScreens = { page = Page.Screens },
                            onShader = { page = Page.Shader },
                            hasSkin = hasSkin,
                            onSkin = { page = Page.Skin },
                            canEditSkin = skinList?.let { skinToEdit(it) != null } == true,
                            onEditSkin = { skins?.edit?.invoke() },
                            muted = muted,
                            onAudio = {
                                muted = !muted
                                onMute(muted)
                                command("MUTE", context.getString(if (muted) R.string.game_toast_sound_off else R.string.game_toast_sound_on))
                            },
                            onReset = { confirmRestart = true },
                            restartEntry = restartEntry,
                            onRetroArchMenu = onRetroArchMenu,
                            touchControls = touchControls?.let { touchOn },
                            onTouchControls = {
                                touchOn = !touchOn
                                touchControls?.onChange?.invoke(touchOn)
                                toast = context.getString(if (touchOn) R.string.game_toast_touch_controls_on else R.string.game_toast_touch_controls_off)
                            },
                            onQuit = onQuit,
                        )
                        Page.States -> StatesPage(states, statesFocus, statesGrid, ::load, ::options)
                        Page.StateOptions -> StateOptionsPage { page = Page.StateDelete }
                        Page.StateDelete -> chosen?.let { st ->
                            StateDeletePage(st, onCancel = { page = Page.States }) {
                                scope.launch {
                                    val gone = withContext(Dispatchers.IO) { SaveSlots.delete(st.file) }
                                    toast = context.getString(if (gone) R.string.game_state_deleted else R.string.game_state_delete_failed)
                                    statesFocus.value = null
                                    refresh++
                                    page = Page.States
                                }
                            }
                        }
                        Page.Screens -> if (screens != null) ScreensPage(screens.shown.value) { layout ->
                            screens.pick(layout)
                            page = Page.Main
                            toast = context.getString(R.string.game_toast_screens_layout, context.getString(layout.label))
                        }
                        Page.Skin -> if (skins != null && skinList != null) SkinPage(skins.console, skinList) { name, variant, custom ->
                            // The one already in use: nothing to redraw or to save.
                            if (variant != skins.bundledId.value || custom != skins.custom.value) skins.pick(variant, custom)
                            page = Page.Main
                            toast = context.getString(R.string.game_toast_skin, name)
                        }
                        Page.Shader -> ShaderPage(SessionToggles.shader?.takeIf { it.first == session.romKey }?.second ?: session.shader) { id ->
                            SessionToggles.shader = session.romKey to id
                            // Saved for this game: the library folds it into the ROM's shader on its next resume or launch.
                            val picks = session.shaderPicks
                            if (picks != null && session.romKey.isNotEmpty()) {
                                // Off the sheet's scope: closing the sheet right after the pick must not cancel the write.
                                thread(name = "shader-pick") { ShaderPicks.record(picks, session.romKey, id) }
                            }
                            val preset = SHADERS.first { it.id == id }.preset
                            val arg = preset?.let { " " + File(session.shadersDir, it).absolutePath } ?: ""
                            command("SET_SHADER$arg", context.getString(R.string.game_toast_shader, SHADERS.first { it.id == id }.name(context)))
                        }
                    }
                }
                } }
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
            if (confirmRestart) RestartDialog(band?.endDp?.dp ?: 0.dp, ::cancelRestart, ::restart)
        }
    }
}

/**
 * Restarts straight away. The bare word "RESET" is a press of the engine's reset hotkey, which confirm_reset turns into
 * a press-twice check; with an argument it is the action, which resets on the first one. confirm_reset stays on for
 * the skin's key and the gamepad hotkey, which sit under the player's fingers.
 */
internal const val RESTART_COMMAND = "RESET off"

/** No reply is not a failure: the work can outlast the wait, as a state load can. */
internal fun replyFailed(reply: String?) = reply?.contains("ERROR") == true

/**
 * Asks before restarting. Drawn in the sheet's own window and not as an OeConfirmDialog: a dialog is a window of its
 * own that takes focus from the engine, which then stops polling the commands, and the pad only reaches the sheet's
 * view. [top] keeps the card under the fold in tabletop.
 */
@Composable
private fun RestartDialog(top: Dp, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    // Again when the pad wakes: opened by touch, focus could not land here and the pad would drive the sheet underneath.
    LaunchedEffect(InputMode.gamepad) { runCatching { cancel.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color(0x8C08080A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .padding(top = top),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(24.dp).widthIn(max = 320.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Oe.Sheet)
                .pointerInput(Unit) { detectTapGestures {} }
                .holdGamepadFocus(true)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.game_restart_confirm_title), style = Type.Title.copy(fontSize = 20.sp))
            Text(stringResource(R.string.game_restart_confirm_body), style = Type.Small)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onCancel, Modifier.heightIn(min = 44.dp).focusRequester(cancel).gamepadFocus(corner = 20.dp)) {
                    Text(stringResource(R.string.detail_cancel), style = Type.Section)
                }
                TextButton(onConfirm, Modifier.heightIn(min = 44.dp).gamepadFocus(corner = 20.dp)) {
                    Text(stringResource(R.string.detail_restart), style = Type.Section.copy(color = Oe.Danger))
                }
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
    /** The bottom half of a half-open fold (DobFoldPausa): three rows, Back to game and Restart in them. */
    tabletop: Boolean,
    /** A phone, upright or on its side (issues 849, 855): Continue and Restart on top, Load beside Save, no Restart row in the list. */
    quickRows: Boolean,
    /** The action tiles' columns: 4 on a phone upright, 5 elsewhere (issue 858). */
    tileColumns: Int,
    onResume: () -> Unit,
    hardcore: Boolean,
    speed: Speed,
    /** The slot "Save" writes to, whether a state is there already, and an arrow's step. */
    slot: Int,
    slotInUse: Boolean,
    /** The numbered slots holding a state, which the slider fills (issue 867). */
    usedSlots: Set<Int>,
    /** Makes a slot current: the slider was dragged, tapped or stepped. */
    onSlot: (Int) -> Unit,
    onSave: () -> Unit,
    /** Opens the save states page ("See all"). */
    onSeeAll: () -> Unit,
    /** Loads the state in [slot] (Load beside Save). */
    onLoadSlot: () -> Unit,
    /** The fast-forward (FAST) or slow-motion (SLOW) tile was tapped. */
    onSpeed: (Speed) -> Unit,
    onScreenshot: () -> Unit,
    onShader: () -> Unit,
    /** Whether a skin is in use; false: no skin tiles. */
    hasSkin: Boolean,
    onSkin: () -> Unit,
    canEditSkin: Boolean,
    onEditSkin: () -> Unit,
    muted: Boolean,
    onAudio: () -> Unit,
    /** NDS, which has the Screens tile; its page marks the layout in use. */
    nds: Boolean,
    onScreens: () -> Unit,
    onReset: () -> Unit,
    /** Takes the pad back to the Restart entry after a cancelled dialog. */
    restartEntry: FocusRequester,
    onRetroArchMenu: () -> Unit,
    /** Whether the Touch controls tile is lit; null: no gamepad, no tile (issue 865). */
    touchControls: Boolean?,
    onTouchControls: () -> Unit,
    /** The Quit tile, the grid's last (issue 875). */
    onQuit: () -> Unit,
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
    // Save and Load, "See all" and the slot slider under them: the same block in every form (issue 867). [saveFocus] is
    // where the pad starts on the panel and the fold, which have no Continue at the top.
    val saveArea = @Composable { modifier: Modifier, saveFocus: Modifier ->
        SaveLoadRow(
            slot, slotInUse, usedSlots, modifier, saveFocus, onSlot, onSave, onLoadSlot, canLoad = slotInUse && !hardcore,
            onSeeAll = onSeeAll, canSeeAll = !hardcore,
        )
    }
    val firstFocus = @Composable { Modifier.restorableFocus("save", remember { mutableStateOf<String?>(null) }, initial = true) }
    // Continue takes the pad's first focus there: A right after pausing resumes.
    val resumeRow = @Composable { modifier: Modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                stringResource(R.string.game_continue), Icons.Play, Oe.Accent, Color.White,
                Modifier.weight(1.6f).restorableFocus("continue", remember { mutableStateOf<String?>(null) }, initial = true), onResume,
            )
            BigButton(
                stringResource(R.string.game_restart_short), Icons.Reset, Oe.Raised, Oe.Text, Modifier.weight(1f).focusRequester(restartEntry), onReset,
                iconYields = true,
            )
        }
    }
    val tiles = @Composable { modifier: Modifier ->
        // Every action is a tile (issue 862); only the toggles ever light up.
        val items = pauseTiles(nds = nds, skin = hasSkin, tabletop = tabletop, touch = touchControls != null)
        val tileView = @Composable { tile: PauseTile ->
            when (tile) {
                PauseTile.FAST_FORWARD -> ActionTile(stringResource(R.string.game_fast_forward), Icons.FastForward, speed == Speed.FAST, filled = true, enabled = !hardcore) { onSpeed(Speed.FAST) }
                PauseTile.SLOW_MOTION -> ActionTile(stringResource(R.string.game_slow_motion), Icons.SlowMotion, speed == Speed.SLOW, enabled = !hardcore) { onSpeed(Speed.SLOW) }
                PauseTile.SCREENSHOT -> ActionTile(stringResource(R.string.game_screenshot), Icons.Camera, false, onClick = onScreenshot)
                PauseTile.SHADER -> ActionTile(stringResource(R.string.game_shader), Icons.Shader, false, onClick = onShader)
                PauseTile.SCREENS -> ActionTile(stringResource(R.string.game_screens), Icons.Screens, false, onClick = onScreens)
                PauseTile.AUDIO -> MuteTile.of(muted).let { mute -> ActionTile(stringResource(mute.label), mute.icon, false, warn = mute.warn, onClick = onAudio) }
                PauseTile.CHANGE_SKIN -> ActionTile(stringResource(R.string.game_change_skin), Icons.Skin, false, onClick = onSkin)
                PauseTile.EDIT_SKIN -> ActionTile(stringResource(R.string.game_edit_skin), Icons.Pencil, false, enabled = canEditSkin, onClick = onEditSkin)
                PauseTile.TOUCH_CONTROLS -> ActionTile(stringResource(R.string.game_touch_controls), Icons.TouchControls, touchControls == true, onClick = onTouchControls)
                PauseTile.RETROARCH_MENU -> ActionTile(stringResource(R.string.game_retroarch_menu), Icons.Sliders, false, onClick = onRetroArchMenu)
                // The muted tile's red (issue 861), always: an action that ends the game, never lit or unlit.
                PauseTile.QUIT -> ActionTile(stringResource(R.string.game_quit), Icons.Quit, false, warn = true, onClick = onQuit)
            }
        }
        // A grid of fixed columns that wraps from the left (issue 858): 5 tiles upright are 4 + 1, never 3 + 2.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val grid = tileGrid(maxWidth.value, tileColumns)
            Column(verticalArrangement = Arrangement.spacedBy(TILE_ROW_GAP_DP.dp)) {
                items.chunked(grid.columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(grid.gapDp.dp)) {
                        row.forEach { Box(Modifier.width(grid.columnDp.dp), contentAlignment = Alignment.TopCenter) { tileView(it) } }
                    }
                }
            }
        }
    }
    if (tabletop) {
        Column {
            note()
            val backToGame = @Composable { modifier: Modifier ->
                BigButton(stringResource(R.string.game_back_to_game), Icons.Play, Oe.Raised, Oe.Text, modifier, onResume)
            }
            BoxWithConstraints(Modifier.padding(top = 4.dp).fillMaxWidth()) {
                if (maxWidth >= TabletopSaveRowWidth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        saveArea(Modifier.weight(1f), firstFocus())
                        backToGame(Modifier.width(168.dp))
                    }
                } else {
                    // A small fold has no room for Back to game beside Save and Load: it goes under the slider.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        saveArea(Modifier.fillMaxWidth(), firstFocus())
                        backToGame(Modifier.fillMaxWidth())
                    }
                }
            }
            tiles(Modifier.padding(top = 16.dp))
            BigButton(
                stringResource(R.string.game_restart), Icons.Reset, Oe.Raised, Oe.Text,
                Modifier.padding(top = 16.dp).fillMaxWidth().focusRequester(restartEntry), onReset,
            )
        }
        return
    }
    val actions = @Composable { modifier: Modifier ->
        Column(modifier) {
            if (quickRows) {
                // The essentials first (upright, all above the first stop's fold): the note goes under them.
                resumeRow(Modifier.padding(top = 16.dp).fillMaxWidth())
                saveArea(Modifier.padding(top = 10.dp).fillMaxWidth(), Modifier)
                note()
            } else {
                note()
                saveArea(Modifier.padding(top = 16.dp).fillMaxWidth(), firstFocus())
            }
            // On its side the tiles go to the right half, beside these rows (issue 862).
            if (!landscape) tiles(Modifier.padding(top = 18.dp))
        }
    }
    val rest = @Composable { modifier: Modifier ->
        Column(modifier) {
            if (landscape) tiles(Modifier.padding(top = 16.dp))
            // On a phone Restart sits beside Continue; the tablet panel keeps its row.
            if (!quickRows) {
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Oe.Raised)) {
                    Box(Modifier.focusRequester(restartEntry).focusGroup()) { ListRow(stringResource(R.string.game_restart), onClick = onReset) }
                }
            }
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

/** A plain button of the sheet: full width, 50 dp tall unless [modifier] says otherwise. */
@Composable
private fun SheetButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.Button.copy(color = fg, fontSize = 15.sp)) }
}

/** Below this width, a tabletop's bottom half puts Back to game under the save block instead of beside it. */
private val TabletopSaveRowWidth = 800.dp

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
private fun PausedArea(states: List<PauseSession.State>, modifier: Modifier, onPick: (PauseSession.State) -> Unit, onOptions: (PauseSession.State) -> Unit) {
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
                        StateTile(st.slot, st.thumb, st.modified, Modifier.weight(1f, fill = false).widthIn(max = 140.dp), Color(0xB3FFFFFF), { onPick(st) }, { onOptions(st) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(
    text: String, icon: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit,
    enabled: Boolean = true, showIcon: Boolean = true,
    /** The icon steps aside when the label would be cut, so the whole word shows (Tamil's Restart in landscape, issue 855). */
    iconYields: Boolean = false,
) {
    // Measured, not guessed from the width: the label's length is the locale's. A new window size asks again.
    var iconFits by remember(text, if (iconYields) LocalConfiguration.current.screenWidthDp else 0) { mutableStateOf(true) }
    Row(
        modifier.height(56.dp).let { if (enabled) it.gamepadFocus(corner = 16.dp) else it.alpha(DISABLED_ALPHA) }
            .clip(RoundedCornerShape(16.dp)).background(bg).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon && iconFits) {
            Icon(icon, fg, 22.dp, filled = icon == Icons.SaveState || icon == Icons.LoadState)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, style = Type.Button.copy(color = fg, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (iconYields && iconFits && it.isLineEllipsized(0)) iconFits = false },
        )
    }
}

/** Below this width the phone's save row leaves the icons out of Save and Load, so neither label is cut. */
private val SaveLoadIconsWidth = 360.dp

/**
 * The save block (issues 849, 867): "Save" and Load beside it, which loads the slot shown; under Load, "See all", which
 * opens the save-states page; under both, the slot slider. Save shows the slot and whether it is in use; Load is off for
 * an empty slot ([canLoad]). [saveFocus] goes on Save.
 */
@Composable
private fun SaveLoadRow(
    slot: Int, inUse: Boolean, used: Set<Int>, modifier: Modifier, saveFocus: Modifier, onPick: (Int) -> Unit, onSave: () -> Unit,
    onLoad: () -> Unit, canLoad: Boolean, onSeeAll: () -> Unit, canSeeAll: Boolean,
) {
    BoxWithConstraints(modifier) {
        val icons = maxWidth >= SaveLoadIconsWidth
        Column {
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1.25f).fillMaxHeight().then(saveFocus).gamepadFocus(corner = 16.dp).clip(RoundedCornerShape(16.dp))
                        .background(Oe.Raised).clickable(role = Role.Button, onClick = onSave).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icons) {
                        Icon(Icons.SaveState, Oe.Text, 20.dp, filled = true)
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(horizontalAlignment = if (icons) Alignment.Start else Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.game_save), style = Type.Button.copy(color = Oe.Text, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (inUse) R.string.game_slot_used else R.string.game_slot_empty, slot),
                            style = Type.Caption.copy(color = if (inUse) Oe.AccentOnSoft else Oe.Muted, fontWeight = if (inUse) FontWeight.SemiBold else FontWeight.Normal),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                BigButton(stringResource(R.string.game_load), Icons.LoadState, Oe.Raised, Oe.Text, Modifier.weight(1f), onLoad, enabled = canLoad, showIcon = icons)
            }
            SlotStrip(slot, used, Modifier.padding(top = 2.dp).fillMaxWidth(), onPick, onSeeAll, canSeeAll)
        }
    }
}

/** A fling at least this fast, between the stops, goes the way it was flung. */
private val SheetFling = 400.dp
/** Above the taller stop: the game still peeks out. */
private val SheetExpandedTop = 16.dp
/** Between the game's bottom and the first stop. */
private val SheetGap = 8.dp
/**
 * What the first stop always shows on a short screen: the header, Continue, the save row, "See all" and the slot slider
 * (issue 867), the navigation bar on top.
 */
private val SheetEssentials = 420.dp

/**
 * The phone-portrait sheet's position (issue 849): resting on a [stop], or [moving] under a finger or an animation.
 * The rest comes from the last layout, in the sheet area's pixels (its top just under the cutout).
 */
@Stable
private class SheetDrag {
    var stop by mutableStateOf(SheetStop.FIRST)
    /** The sheet's top while it moves; null at rest. */
    var moving by mutableStateOf<Float?>(null)
    /** This window's top on screen, which [gameBottom][sheetPlace] is measured from. */
    var origin by mutableStateOf(0f)
    var area = 0f
    var expanded = 0f
    var first = 0f
    /** Where the top shows now, and where it shows resting on the first stop (lower than [first] for short content). */
    var shown = 0f
    var restFirst = 0f
    /** The highest the top can show: [expanded], or lower when the content fits whole under it (nothing to stretch into). */
    var reach = 0f
    private var from = SheetStop.FIRST
    private var job: Job? = null

    val top: Float get() = moving ?: if (stop == SheetStop.EXPANDED) expanded else first

    /** Moves the top by [delta] from where it shows, never above the taller stop; returns how far it went. */
    fun by(delta: Float): Float {
        job?.cancel()
        // A sheet that cannot rise above the first stop rests on it, whatever stop it was last sent to.
        val now = moving ?: shown.also { from = if (reach >= restFirst - 1f) SheetStop.FIRST else stop }
        val next = (now + delta).coerceIn(reach, area)
        moving = next
        return next - now
    }

    /** Lets go at [velocity]: springs to the stop [settle] picks, or slides out and calls [onClosed]. */
    fun release(scope: CoroutineScope, velocity: Float, fling: Float, onClosed: () -> Unit) {
        val now = moving ?: return
        val target = settle(now, velocity, reach, restFirst, area - restFirst, from == SheetStop.FIRST, fling)
        val end = when (target) {
            SheetStop.EXPANDED -> reach
            SheetStop.FIRST -> restFirst
            SheetStop.CLOSED -> area
        }
        job?.cancel()
        job = scope.launch {
            animate(now, end, velocity, if (target == SheetStop.CLOSED) tween(180) else spring()) { v, _ -> moving = v }
            if (target == SheetStop.CLOSED) {
                onClosed()
            } else {
                stop = target
                moving = null
            }
        }
    }
}

/**
 * Sizes and places the phone-portrait sheet for [sheet]: above the first stop it is as tall as the room from its top
 * down (what does not fit scrolls inside), below it the sheet keeps the first stop's height and slides down.
 */
private fun Modifier.sheetPlace(sheet: SheetDrag, gameBottom: Float?, cutout: WindowInsets, navigation: WindowInsets): Modifier =
    layout { measurable, constraints ->
        val area = constraints.maxHeight.toFloat()
        // The area starts under the cutout; no skin box: a 4:3 game at its top.
        val game = gameBottom?.let { it - sheet.origin - cutout.getTop(this) } ?: (constraints.maxWidth * GameViewport.UNKNOWN_ASPECT)
        sheet.area = area
        sheet.expanded = SheetExpandedTop.toPx()
        sheet.first = firstStopTop(game, area, sheet.expanded, SheetEssentials.toPx() + navigation.getBottom(this), SheetGap.toPx())
        val top = sheet.top
        val room = (area - minOf(top, sheet.first)).roundToInt().coerceIn(0, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = room))
        val rest = area - placeable.height
        val shift = maxOf(0f, top - maxOf(sheet.first, rest))
        sheet.shown = rest + shift
        if (sheet.moving == null) sheet.restFirst = maxOf(sheet.first, rest)
        // Content shorter than the room it was given has all of it showing: the top goes no higher than that.
        sheet.reach = if (placeable.height < room) maxOf(sheet.expanded, rest) else sheet.expanded
        layout(placeable.width, placeable.height) { placeable.placeRelativeWithLayer(0, 0) { translationY = shift } }
    }

@Composable
private fun ActionTile(label: String, icon: String, on: Boolean, filled: Boolean = false, enabled: Boolean = true, warn: Boolean = false, onClick: () -> Unit) {
    // [warn]: the danger tone, for a state to notice rather than a feature that is on (the muted game, issue 861) and
    // for the action that ends the game (Quit, issue 875).
    val ring = when { warn -> Oe.Danger; on -> Oe.AccentBright; else -> null }
    Column(
        Modifier.let { if (enabled) it.gamepadFocus(corner = 12.dp) else it.alpha(DISABLED_ALPHA) }
            .clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(when { warn -> Oe.DangerBg; on -> Oe.AccentSoft; else -> Oe.Surface2 })
                .let { if (ring != null) it.border(1.5.dp, ring, RoundedCornerShape(18.dp)) else it },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, when { warn -> Oe.Danger; on -> Oe.AccentOnSoft; else -> Oe.Text }, 22.dp, filled = filled) }
        // The name alone, never a second line under it (issue 875): every row of the grid is the same height.
        TileLabel(label)
    }
}

/** The smallest a tile's label shrinks to so that no word breaks in two (de "Bildschirmfoto", ta "திரைப்பிடிப்பு"). */
private val TileLabelMinSize = 8.sp

/** A tile's name, centred on up to two lines; a word too long for its column shrinks the text a step at a time. */
@Composable
private fun TileLabel(label: String) {
    val style = Type.Caption.copy(color = Oe.Text, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    BasicText(label, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, autoSize = WholeWords(style.fontSize))
}

/** Text auto-size that takes the largest size from [max] down to [TileLabelMinSize] fitting without splitting a word. */
private data class WholeWords(val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max
        while (size.value > TileLabelMinSize.value) {
            val layout = performLayout(constraints, text, size)
            val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
            if (!layout.hasVisualOverflow && !splitsAWord(text.text, ends)) return size
            size = (size.value - 1).sp
        }
        return TileLabelMinSize
    }
}

@Composable
private fun StatesPage(states: List<PauseSession.State>, focused: MutableState<String?>, grid: LazyGridState, onPick: (PauseSession.State) -> Unit, onOptions: (PauseSession.State) -> Unit) {
    if (states.isEmpty()) {
        Text(stringResource(R.string.game_no_states), style = Type.Small, modifier = Modifier.padding(vertical = 28.dp))
        return
    }
    MarginFocusScroll { LazyVerticalGrid(
        GridCells.Adaptive(160.dp),
        Modifier.padding(top = 16.dp - FocusOutset).height(380.dp),
        state = grid,
        contentPadding = PaddingValues(vertical = FocusOutset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(states, key = { it.file.path }) { st ->
            StateTile(st.slot, st.thumb, st.modified, Modifier.restorableFocus(st.file.path, focused, initial = st == states.first()), onClick = { onPick(st) }, onOptions = { onOptions(st) })
        }
    } }
}

/**
 * A state's options, a page of the sheet rather than a popup: this window cannot take focus (the engine keeps it, see
 * GameActivity.onExternalMenuInput), so the pad's keys reach only this view and a window over it would never get them.
 */
@Composable
private fun StateOptionsPage(onDelete: () -> Unit) {
    val focused = remember { mutableStateOf<String?>(null) }
    SheetButton(stringResource(R.string.game_state_delete), Oe.DangerBg, Oe.Danger, Modifier.padding(top = 16.dp).restorableFocus("delete", focused, initial = true), onDelete)
}

/** Asks before deleting [st], in the sheet for the same reason as [StateOptionsPage]; focus starts on Cancel. */
@Composable
private fun StateDeletePage(st: PauseSession.State, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val focused = remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 14.dp)) {
        Text(stateDeleteBody(st.slot), style = Type.Body.copy(color = Oe.Muted, fontSize = 14.sp, lineHeight = 20.sp))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton(stringResource(R.string.detail_cancel), Oe.Raised, Oe.Text, Modifier.weight(1f).restorableFocus("cancel", focused, initial = true), onCancel)
            SheetButton(stringResource(R.string.game_state_delete), Oe.DangerBg, Oe.Danger, Modifier.weight(1f), onConfirm)
        }
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

/** The console's skins, read again when the one in use changes (an editor can have made a copy). */
@Composable
private fun rememberGameSkinList(skins: GameSkins): SkinListState? {
    val context = LocalContext.current
    val bundledId = skins.bundledId.value
    val custom = skins.custom.value
    val list by produceState<SkinListState?>(null, skins.console, bundledId, custom) {
        value = withContext(Dispatchers.IO) {
            runCatching { readSkinList(context, skins.store, skins.console).copy(bundledId = bundledId, custom = custom) }.getOrNull()
        }
    }
    return list
}

/** The skin screen's list, its miniatures and all, for picking only: copies, imports and the like stay on that screen. */
@Composable
private fun SkinPage(console: Console, state: SkinListState, onPick: (name: String, variant: String, custom: String?) -> Unit) {
    val context = LocalContext.current
    val focused = remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SkinChoices(
            console, state,
            onStored = { onPick(it.name, state.bundledId, it.ref) },
            onBundled = { onPick(context.getString(it.variant.label), it.variant.id, null) },
            background = Oe.Raised,
            item = { key, inUse -> Modifier.restorableFocus(key, focused, initial = inUse) },
        )
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
