package com.openemux.mobile.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import com.openemux.mobile.share.ShareEvent
import com.openemux.mobile.share.TransferSurface
import com.openemux.mobile.ui.share.ConnectByAddressScreen
import com.openemux.mobile.ui.share.IncomingOfferSheet
import com.openemux.mobile.ui.share.TransferChip
import com.openemux.mobile.ui.share.rememberTransferSurface
import com.openemux.mobile.ui.share.ShareScreen
import com.openemux.mobile.ui.share.WhatToSendScreen
import com.openemux.mobile.OpenEmuxApp
import com.openemux.mobile.findActivity
import com.openemux.mobile.data.Rom
import com.openemux.mobile.data.Setup
import com.retroarch.R
import androidx.activity.compose.BackHandler
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.saveable.rememberSaveable
import com.openemux.mobile.data.BiosDef
import com.openemux.mobile.data.Console
import com.openemux.mobile.launch.LaunchOutcome
import com.openemux.mobile.launch.LaunchStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

object Routes {
    const val SHELF = "shelf"
    const val LIBRARY = "library/{filter}"
    const val ROM = "rom/{key}"
    const val ARTWORK = "artwork/{key}?tab={tab}"
    const val ARTWORK_PROVIDERS = "artwork-providers"
    const val RECENT = "recent"
    const val IMPORT = "import"
    const val SETTINGS = "settings"
    const val CONSOLE = "settings/{console}"
    const val BIOS = "bios"
    const val ACHIEVEMENTS = "achievements"
    const val BACKUP = "backup"
    const val CONTROLLERS = "controllers"
    const val ABOUT = "about"
    const val LICENSES = "about/licenses"
    const val LICENSE = "about/license/{id}"
    const val SHARE = "share"
    const val SHARE_RECEIVE = "share/receive"
    const val SHARE_ITEMS = "share/items"
    const val SHARE_ADDRESS = "share/address?addr={addr}"

    fun library(filter: String) = "library/$filter"
    fun rom(key: String) = "rom/${Uri.encode(key)}"
    fun artwork(key: String, tab: String? = null) = "artwork/${Uri.encode(key)}" + (tab?.let { "?tab=$it" } ?: "")
    fun console(id: String) = "settings/$id"
    /** The BIOS screen scrolled to [console], from a launch that found its BIOS missing (issue 923). */
    fun bios(console: String) = "bios?console=$console"
    fun license(id: String) = "about/license/$id"
    fun shareAddress(address: String) = "share/address?addr=${Uri.encode(address)}"
}

/** What every screen needs: the app's data, navigation and the game launcher. */
class AppActions(
    val app: OpenEmuxApp,
    val nav: NavHostController,
    val play: (Rom, Boolean, Int?) -> Unit,
)

val LocalActions = staticCompositionLocalOf<AppActions> { error("no actions") }

/** How long the launch cover takes to leave once the bars are gone. */
private const val LAUNCH_FADE_MS = 150

@Composable
fun OpenEmuxRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as OpenEmuxApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var launching by remember { mutableStateOf<Rom?>(null) }
    // The launch under way (issue 923): its job, so Back can cancel it; when it started; what it was, so a session
    // that ends on its own can be explained and tried again.
    var launchJob by remember { mutableStateOf<Job?>(null) }
    var launchedAt by remember { mutableStateOf(0L) }
    var lastLaunch by remember { mutableStateOf<Triple<Rom, Boolean, Int?>?>(null) }
    var problem by remember { mutableStateOf<LaunchProblem?>(null) }
    // Cancelling is offered a second in: a tap is never cancelled by the Back that was meant for the screen before.
    var cancellable by remember { mutableStateOf(false) }
    LaunchedEffect(launching) {
        cancellable = false
        if (launching != null) { delay(CANCEL_AFTER_MS); cancellable = true }
    }
    val stage by app.launcher.stage.collectAsState()

    // The gamepad layout turning on (a controller connected, no press yet) leaves touch mode, where nothing takes
    // focus, so the screen's item gets the ring at once.
    // Not a pad press: the window's scope lets Compose and the screen's initial item take focus as usual.
    val inputModes = LocalInputModeManager.current
    val padScope = LocalPadScope.current
    LaunchedEffect(InputMode.padLayout) {
        if (InputMode.padLayout) {
            inputModes.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            padScope.touchExit = false
        }
    }

    LifecycleResumeEffect(Unit) {
        scope.launch { app.library.scan() }
        val back = launching
        val since = launchedAt
        launching = null
        launchedAt = 0L
        // Back from a game that ended on its own (issue 923): say so, with the engine's log, instead of the shelf alone.
        val last = lastLaunch
        if (back != null && since > 0 && last != null && stage == null) scope.launch {
            val ended = withContext(Dispatchers.IO) { app.launcher.sessionOutcome(since) }
            if (ended != null) {
                problem = LaunchProblem.Ended(last.first, last.second, last.third, ended.first, ((System.currentTimeMillis() - since) / 1000).toInt(), ended.second)
            }
        }
        onPauseOrDispose { }
    }

    // Starts [rom]; with [checkBios], a console whose required BIOS is missing asks first (issue 923).
    fun start(rom: Rom, resume: Boolean, slot: Int?, checkBios: Boolean) {
        if (launching != null) return
        launching = rom
        launchedAt = System.currentTimeMillis()
        lastLaunch = Triple(rom, resume, slot)
        launchJob = scope.launch {
            val missing = if (checkBios) withContext(Dispatchers.IO) { app.launcher.missingBios(rom.console) } else null
            if (missing != null) {
                launching = null
                launchedAt = 0L
                problem = LaunchProblem.MissingBios(rom, resume, slot, missing)
                return@launch
            }
            try {
                app.launcher.launch(context.findActivity(), rom, resume, slot)
            } catch (e: CancellationException) {
                launching = null
                launchedAt = 0L
                throw e
            } catch (e: Exception) {
                launching = null
                launchedAt = 0L
                problem = LaunchProblem.Failed(rom, e)
            }
        }
    }
    fun cancelLaunch() {
        launchJob?.cancel()
        launchJob = null
        launching = null
        launchedAt = 0L
    }

    val actions = remember(nav) {
        AppActions(app, nav) { rom, resume, slot -> start(rom, resume, slot, checkBios = true) }
    }

    OpenEmuxTheme {
        // The app runs full screen: the bars, when a swipe brings them back, sit over it without moving anything,
        // so their insets are spent here and only the camera cutout keeps the content away. In split screen or a
        // freeform window they cannot be hidden, so the content is padded away from them there. Either way the
        // screens' own bar paddings read 0.
        val insets = if (AppWindow.multiWindow) Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        else Modifier.consumeWindowInsets(WindowInsets.systemBars).windowInsetsPadding(WindowInsets.displayCutout)
        // A launch keeps the starting window's look (its background, whatever theme the app picked) over the
        // content until the bars it brought are gone; from API 31 the held system splash sits on top of it anyway.
        val cover by animateFloatAsState(if (AppWindow.gate.open) 0f else 1f, tween(LAUNCH_FADE_MS), label = "launch")
        val starting = colorResource(R.color.oe_bg)
        Box(Modifier.fillMaxSize().background(Oe.Bg).then(insets).drawWithContent {
            drawContent()
            if (cover > 0f) drawRect(starting, alpha = cover)
        }) {
            val setup by app.setup.state.collectAsState()
            // "Fully drawn" (the start-up metric, issue 920): the gate open and the first screen with its content,
            // the shelf once the library is known, or the setup.
            val loaded by app.library.loaded.collectAsState()
            ReportDrawnWhen { AppWindow.gate.open && (setup !is Setup.State.Done || loaded) }
            // Nothing opens until the cores are on the device.
            if (setup !is Setup.State.Done) SetupScreen(setup) { scope.launch { app.setup.run() } }
            else CompositionLocalProvider(LocalActions provides actions) {
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                // App-wide buttons: Start opens Ajustes, L1/R1 walk the main tabs where a screen does not use them.
                fun tab(step: Int) {
                    val i = (MAIN_TABS.indexOf(if (route == Routes.LIBRARY) Routes.SHELF else route).coerceAtLeast(0) + step)
                        .coerceIn(0, MAIN_TABS.lastIndex)
                    nav.navigateTab(MAIN_TABS[i])
                }
                // The D-pad stays inside the screen, so the transfer chip answers to Start while it shows (its badge says so).
                val transferChip = rememberTransferSurface(app.shareCoordinator) == TransferSurface.CHIP
                GamepadButtons(
                    l1 = { if (route in MAIN_TABS || route == Routes.LIBRARY) tab(-1) },
                    r1 = { if (route in MAIN_TABS || route == Routes.LIBRARY) tab(1) },
                    start = {
                        when {
                            transferChip -> app.shareCoordinator.showProgress()
                            route != Routes.SETTINGS -> nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }
                    },
                )
                val showTabs = route in setOf(Routes.SHELF, Routes.LIBRARY, Routes.RECENT, Routes.IMPORT, Routes.SETTINGS)
                // Where this box sits in the window, top and bottom in dp: the fold is in window coordinates.
                var span by remember { mutableStateOf<Pair<Float, Float>?>(null) }
                val layout = LocalWindowLayout.current
                // Half open with the fold across: content on the top half, actions and tabs on the bottom one.
                val halves = span?.let { (top, bottom) -> tabletopHalves(layout, top, bottom) }
                val tabletop = halves != null
                // A short window (a phone on its side, a split-screen half): the bottom bar (86dp or so) would eat the height, so the tabs go
                // to a rail; a tablet has the width for one, with the logo on top. In tabletop they go to the bottom half
                // instead. The NavHost stays outside any branch on this, so resizing between them keeps the screen.
                val navKind = if (showTabs && !tabletop) layout.nav else NavKind.BottomBar
                val rail = navKind != NavKind.BottomBar
                val railWidth = RailWidth
                val deck = remember { DeckSlot<@Composable () -> Unit>() }
                val topFocus = remember { FocusRequester() }
                val navFocus = remember { FocusFlag() }
                val density = LocalDensity.current.density
                // The bottom tab bar's real height: it wraps the system navigation bar, so 86dp is only a first guess.
                // Handed down as the State itself, so a new height recomposes only what reads TabBarHeight, not this root.
                val tabBarMeasured = remember { mutableStateOf(Dp.Unspecified) }
                Box(Modifier.fillMaxSize().background(Oe.Bg)) {
                // The NavHost keeps its place in the tree whatever the posture, so its screens keep their state.
                Column(Modifier.fillMaxSize().onGloballyPositioned { c ->
                    val top = c.positionInWindow().y / density
                    span = top to top + c.size.height / density
                }) {
                Box(
                    (if (halves != null) Modifier.fillMaxWidth().height(halves.first.dp) else Modifier.fillMaxWidth().weight(1f))
                        .focusRequester(topFocus).focusRestorer().focusGroup(),
                ) {
                CompositionLocalProvider(LocalRail provides rail, LocalTabletop provides tabletop, LocalTabBarMeasured provides tabBarMeasured, LocalDeckSlot provides deck, LocalNavFocus provides navFocus) {
                    NavHost(
                        nav,
                        startDestination = Routes.SHELF,
                        // Right from the rail comes back to the item the screen had focused.
                        modifier = Modifier.fillMaxSize().padding(start = if (rail) railWidth else 0.dp)
                            .tracksFocus(navFocus)
                            .focusProperties { onEnter = { if (requestedFocusDirection == FocusDirection.Right) InputMode.last?.requestFocus() } }
                            .focusGroup(),
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                    ) {
                        composable(Routes.SHELF) { ShelfScreen() }
                        composable(Routes.LIBRARY) { LibraryScreen(it.arguments?.getString("filter") ?: "ALL", it) }
                        composable(Routes.ROM) { DetailScreen(Uri.decode(it.arguments?.getString("key") ?: "")) }
                        composable(Routes.ARTWORK, listOf(navArgument("tab") { defaultValue = "" })) {
                            RememberRowFocus { ArtworkScreen(Uri.decode(it.arguments?.getString("key") ?: ""), it.arguments?.getString("tab")) }
                        }
                        composable(Routes.ARTWORK_PROVIDERS) { RememberRowFocus { ArtworkProvidersScreen() } }
                        composable(Routes.RECENT) { RecentScreen() }
                        // Rows (ListRow, SwitchRow, SettingTile) get their focus back on these when they come back.
                        composable(Routes.IMPORT) { RememberRowFocus { ImportScreen() } }
                        composable(Routes.SETTINGS) { RememberRowFocus { SettingsScreen(it) } }
                        composable(Routes.CONSOLE) { RememberRowFocus { ConsoleSettingsScreen(it.arguments?.getString("console") ?: "GBA") } }
                        composable("${Routes.BIOS}?console={console}", listOf(navArgument("console") { defaultValue = "" })) {
                            RememberRowFocus { BiosScreen(console = it.arguments?.getString("console")?.takeIf(String::isNotEmpty)) }
                        }
                        composable(Routes.ACHIEVEMENTS) { RememberRowFocus { AchievementsScreen() } }
                        composable(Routes.BACKUP) { RememberRowFocus { BackupScreen() } }
                        composable(Routes.CONTROLLERS) { RememberRowFocus { ControllersScreen() } }
                        composable(Routes.ABOUT) { RememberRowFocus { AboutScreen() } }
                        composable(Routes.LICENSES) { RememberRowFocus { LicensesScreen() } }
                        composable(Routes.LICENSE) { RememberRowFocus { LicenseTextScreen(it.arguments?.getString("id").orEmpty()) } }
                        composable(Routes.SHARE) { ShareScreen(receive = false) }
                        composable(Routes.SHARE_RECEIVE) { ShareScreen(receive = true) }
                        composable(Routes.SHARE_ITEMS) { WhatToSendScreen() }
                        composable(Routes.SHARE_ADDRESS, listOf(navArgument("addr") { defaultValue = "" })) {
                            ConnectByAddressScreen(it.arguments?.getString("addr").orEmpty())
                        }
                    }
                    if (tabletop) {
                        // The tabs, the hints and the toast are on the bottom half.
                    } else if (rail) {
                        TabRail(route, navKind == NavKind.TabletRail, railWidth, Modifier.align(Alignment.CenterStart))
                        TransferChip(Modifier.align(Alignment.BottomCenter).padding(start = railWidth, bottom = 12.dp + hintBarInset))
                        ArtworkDoneToast(Modifier.align(Alignment.BottomCenter).padding(start = railWidth, bottom = 12.dp))
                        HintBar(GamepadBus.hints, Modifier.align(Alignment.BottomCenter).padding(start = railWidth))
                    } else if (showTabs) {
                        TabBar(route, Modifier.align(Alignment.BottomCenter).onSizeChanged { tabBarMeasured.value = (it.height / density).dp })
                        TransferChip(Modifier.align(Alignment.BottomCenter).padding(bottom = TabBarHeight + 12.dp + hintBarInset))
                        ArtworkDoneToast(Modifier.align(Alignment.BottomCenter).padding(bottom = TabBarHeight + 12.dp))
                        // Flush on the tab bar, both opaque: one block at the foot, nothing showing between or behind them.
                        HintBar(GamepadBus.hints, Modifier.align(Alignment.BottomCenter).padding(bottom = TabBarHeight), opaque = true)
                    } else {
                        // What to Send has its own bar at the bottom, with Done on it.
                        if (route != Routes.SHARE_ITEMS) TransferChip(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp + hintBarInset))
                        HintBar(GamepadBus.hints, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                    }
                }
                }
                if (halves != null) {
                    CompositionLocalProvider(LocalDeckSlot provides deck) {
                        TabletopBottom(forbiddenBand(layout)!!, route, showTabs, topFocus)
                    }
                }
                }
                launching?.let { LaunchingOverlay(it, stage, cancellable, ::cancelLaunch) }
                BackHandler(enabled = launching != null && cancellable) { cancelLaunch() }
                problem?.let { p ->
                    LaunchProblemSheet(
                        p,
                        onClose = { problem = null },
                        onPlayAnyway = { rom, resume, slot -> problem = null; start(rom, resume, slot, checkBios = false) },
                        onImportBios = { console -> problem = null; nav.navigate(Routes.bios(console.id)) },
                        onRetry = { rom, resume, slot -> problem = null; start(rom, resume, slot, checkBios = true) },
                    )
                }
                // Share nearby: an incoming offer over whatever is open, a link to an address, an expired offer's toast.
                IncomingOfferSheet()
                ShareLinks()
                // "Open with" and "Share" from the file manager (ImportActivity).
                ExternalImports()
                }
            }
        }
    }
}

@Composable
private fun ShareLinks() {
    val a = LocalActions.current
    val context = LocalContext.current
    val share = a.app.shareCoordinator
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Only the activity in front takes a link: one stopped behind it would navigate where nobody sees.
    LaunchedEffect(share) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            share.link.collect { uri ->
                if (uri != null) {
                    share.link.value = null
                    a.nav.navigate(Routes.shareAddress(uri)) { launchSingleTop = true }
                }
            }
        }
    }
    LaunchedEffect(share) {
        share.events.collect { event ->
            if (event is ShareEvent.OfferExpired) {
                Toast.makeText(context, context.getString(R.string.share_offer_expired_toast, event.sender), Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun TabBar(route: String?, modifier: Modifier) {
    // Opaque under the hint bar, fading with it (HintBar's 120ms), so the two never differ mid-fade.
    val alpha by animateFloatAsState(if (InputMode.gamepad) 1f else 0.94f, tween(120), label = "tabBarAlpha")
    Column(modifier.fillMaxWidth().background(Oe.Bg.copy(alpha = alpha))) {
        HorizontalDivider(color = Oe.Surface2, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tabs(route)
        }
    }
}

/** The tabs down the left edge: centred for a phone on its side; on a tablet, under the logo, with the active pill. */
@Composable
private fun TabRail(route: String?, tablet: Boolean, width: Dp, modifier: Modifier) {
    val label = stringResource(R.string.nav_rail_label)
    val activeTab = remember { FocusRequester() }
    Row(
        modifier.fillMaxHeight().background(Oe.Bg.copy(alpha = 0.94f)).semantics { contentDescription = label }
            // Only Left from the content moves into the rail, onto the current tab; the first press of a gamepad
            // lands on the screen's own item.
            .focusProperties {
                onEnter = { if (requestedFocusDirection != FocusDirection.Left || !activeTab.requestFocus()) cancelFocusChange() }
            }
            .focusGroup(),
    ) {
        if (tablet) {
            val expanded = width > 80.dp
            Column(
                Modifier.width(width - 1.dp).fillMaxHeight().padding(top = if (expanded) 32.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.oe_logo), contentDescription = "OpenEmux", modifier = Modifier.height(if (expanded) 36.dp else 34.dp))
                Column(
                    Modifier.padding(top = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tabs(route, pill = true, activeTab)
                }
            }
        } else {
            Column(
                Modifier.width(width).fillMaxHeight().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Tabs(route, activeTab = activeTab)
            }
        }
        VerticalDivider(color = Oe.Surface2, thickness = 1.dp)
    }
}

/** A main tab: its label, its icon and the route it opens. */
class TabItem(val label: Int, val icon: String, val route: String) {
    /** The library tab stays lit inside the library it leads to. */
    fun isActive(current: String?) = when (route) {
        Routes.SHELF -> current == Routes.SHELF || current == Routes.LIBRARY
        else -> current == route
    }
}

/** The main tabs, in order: the bar, the rail and the tabletop bar all show these. */
val TAB_ITEMS = listOf(
    TabItem(R.string.settings_tab_library, Icons.Cart, Routes.SHELF),
    TabItem(R.string.settings_tab_recent, Icons.Clock, Routes.RECENT),
    TabItem(R.string.settings_tab_import, Icons.Download, Routes.IMPORT),
    TabItem(R.string.settings_tab_settings, Icons.Sliders, Routes.SETTINGS),
)

@Composable
private fun Tabs(route: String?, pill: Boolean = false, activeTab: FocusRequester? = null) {
    val nav = LocalActions.current.nav
    TAB_ITEMS.forEach { tab ->
        val active = tab.isActive(route)
        Column(
            Modifier
                .then(if (pill) Modifier.width(76.dp).heightIn(min = 56.dp) else Modifier.widthIn(min = 64.dp))
                .then(if (active && activeTab != null) Modifier.focusRequester(activeTab) else Modifier)
                .gamepadFocus(corner = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Tab) { if (tab.route == Routes.SHELF || tab.route != route) nav.navigateTab(tab.route) }
                .semantics { selected = active }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val color = if (active) Oe.AccentText else Oe.Muted
            if (pill) {
                // The tablet rail: the active tab sits in a filled pill, its label in the text colour.
                Box(
                    Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(if (active) Oe.AccentSoft else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { Icon(tab.icon, color, 22.dp) }
                Text(
                    stringResource(tab.label),
                    style = Type.Caption.copy(color = if (active) Oe.Text else Oe.Muted, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Icon(tab.icon, color, 24.dp)
                Text(stringResource(tab.label), style = Type.Caption.copy(color = color, fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Opens a main tab as its button does: the library tab always lands on the shelf, wherever the library was left. */
fun NavHostController.navigateTab(target: String) {
    if (target == Routes.SHELF) popBackStack(Routes.SHELF, inclusive = false)
    else navigate(target) {
        popUpTo(Routes.SHELF) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The cartridge, the stage the launch is at and, a second in, a way out (issue 923). */
@Composable
private fun LaunchingOverlay(rom: Rom, stage: LaunchStage?, cancellable: Boolean, onCancel: () -> Unit) {
    val app = LocalActions.current.app
    Box(
        Modifier.fillMaxSize().background(Oe.Bg.copy(alpha = 0.9f)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Cartridge(
                rom.console, labelFor(rom), rom.title, app.library.effectiveColor(rom),
                Modifier.size(width = 220.dp, height = 140.dp), cover = coverFor(rom),
            )
            val text = when (stage) {
                is LaunchStage.Core -> stringResource(R.string.launch_stage_core, stage.core)
                is LaunchStage.Download -> stringResource(R.string.launch_stage_download, stage.core)
                LaunchStage.Controls, null -> stringResource(R.string.launch_stage_controls)
            }
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Oe.AccentText, strokeWidth = 2.dp)
                Text("  $text", style = Type.Small)
            }
            if (stage is LaunchStage.Download) {
                if (stage.total > 0) LinearProgressIndicator({ stage.bytes.toFloat() / stage.total }, Modifier.padding(top = 12.dp).width(220.dp), color = Oe.AccentBright, trackColor = Oe.Divider)
                else LinearProgressIndicator(Modifier.padding(top = 12.dp).width(220.dp), color = Oe.AccentBright, trackColor = Oe.Divider)
            }
            if (cancellable) SecondaryButton(stringResource(R.string.launch_cancel), onCancel, Modifier.padding(top = 20.dp).gamepadFocus(corner = 26.dp))
        }
    }
}

/** Why a launch stopped short, or how a session ended, each with a way on (issue 923). */
sealed interface LaunchProblem {
    data class MissingBios(val rom: Rom, val resume: Boolean, val slot: Int?, val bios: BiosDef) : LaunchProblem
    data class Failed(val rom: Rom, val error: Throwable) : LaunchProblem
    data class Ended(val rom: Rom, val resume: Boolean, val slot: Int?, val outcome: LaunchOutcome, val seconds: Int, val log: String) : LaunchProblem
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LaunchProblemSheet(
    problem: LaunchProblem,
    onClose: () -> Unit,
    onPlayAnyway: (Rom, Boolean, Int?) -> Unit,
    onImportBios: (Console) -> Unit,
    onRetry: (Rom, Boolean, Int?) -> Unit,
) {
    var details by rememberSaveable { mutableStateOf(false) }
    OeSheet(onClose) {
        SheetBarIcons()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (problem) {
                is LaunchProblem.MissingBios -> {
                    Text(stringResource(R.string.launch_bios_title, problem.rom.console.displayName), style = Type.Title.copy(fontSize = 22.sp))
                    Text(stringResource(R.string.launch_bios_body, problem.bios.files.joinToString(" / ")), style = Type.Small)
                    PrimaryButton(stringResource(R.string.launch_bios_import), { onImportBios(problem.rom.console) }, Modifier.fillMaxWidth().padding(top = 8.dp).gamepadFocus(corner = 26.dp), icon = Icons.Download)
                    SecondaryButton(stringResource(R.string.launch_bios_anyway), { onPlayAnyway(problem.rom, problem.resume, problem.slot) }, Modifier.fillMaxWidth().gamepadFocus(corner = 26.dp))
                }
                is LaunchProblem.Failed -> {
                    Text(stringResource(R.string.launch_failed_title), style = Type.Title.copy(fontSize = 22.sp))
                    Text(problem.error.message ?: problem.error.javaClass.simpleName, style = Type.Small)
                    ProblemDetails(details, problem.error.stackTraceToString()) { details = it }
                    SecondaryButton(stringResource(R.string.launch_close), onClose, Modifier.fillMaxWidth().gamepadFocus(corner = 26.dp))
                }
                is LaunchProblem.Ended -> {
                    Text(stringResource(if (problem.outcome == LaunchOutcome.NOT_STARTED) R.string.launch_died_title else R.string.launch_stopped_title), style = Type.Title.copy(fontSize = 22.sp))
                    Text(stringResource(R.string.launch_died_body, problem.seconds), style = Type.Small)
                    ProblemDetails(details, problem.log.ifBlank { stringResource(R.string.launch_no_log) }) { details = it }
                    PrimaryButton(stringResource(R.string.launch_retry), { onRetry(problem.rom, problem.resume, problem.slot) }, Modifier.fillMaxWidth().padding(top = 8.dp).gamepadFocus(corner = 26.dp), icon = Icons.Play)
                    SecondaryButton(stringResource(R.string.launch_close), onClose, Modifier.fillMaxWidth().gamepadFocus(corner = 26.dp))
                }
            }
        }
    }
}

/** "Show details" and, open, the text in a scrolling box: a stack trace or the engine's log. */
@Composable
private fun ProblemDetails(open: Boolean, text: String, onToggle: (Boolean) -> Unit) {
    Text(
        stringResource(if (open) R.string.launch_hide_details else R.string.launch_details),
        style = Type.BodyMedium.copy(color = Oe.AccentText),
        modifier = Modifier.gamepadFocus(corner = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onToggle(!open) }.padding(4.dp),
    )
    if (open) {
        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)).background(Oe.Surface2).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(text, style = Type.Caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Oe.Text))
        }
    }
}

/** How long a launch runs before Back and the overlay's button can cancel it. */
private const val CANCEL_AFTER_MS = 1000L

/** Shared tile used by both the shelf and the settings to show a console. */
@Composable
fun ConsoleBadge(text: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Oe.Border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = Type.Tag.copy(color = Oe.Muted))
    }
}

/** True on the main tabs when they are a rail on the left (a phone on its side, a tablet), not a bar under everything. */
val LocalRail = compositionLocalOf { false }

/** The bottom tab bar's height as laid out (it wraps the system navigation bar), unspecified until it is. */
val LocalTabBarMeasured = staticCompositionLocalOf<State<Dp>> { mutableStateOf(Dp.Unspecified) }

/** The tab bar's height before it is first measured. */
val DefaultTabBarHeight = 86.dp

/** The room the bottom tab bar takes: none for the side rail or the tabletop's bottom half, else its measured height. */
fun tabBarInset(rail: Boolean, tabletop: Boolean, measured: Dp): Dp = when {
    rail || tabletop -> 0.dp
    measured.isSpecified -> measured
    else -> DefaultTabBarHeight
}

/** The room the bottom tab bar takes, none when the tabs are the side rail or on the tabletop's bottom half. */
val TabBarHeight: Dp
    @Composable get() = tabBarInset(LocalRail.current, LocalTabletop.current, LocalTabBarMeasured.current.value)

/** The rail's width for this window: 80dp, 88dp on a wide tablet. */
val RailWidth: Dp
    @Composable get() = railWidthDp(LocalWindowLayout.current).let { if (it > 0f) it.dp else 80.dp }

private val MAIN_TABS = TAB_ITEMS.map { it.route }
val BottomSpace: Modifier
    @Composable get() = Modifier.height(TabBarHeight + hintBarInset + 24.dp)
