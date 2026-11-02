package com.openemux.mobile.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.openemux.mobile.AppLocale
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.NdsShown
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.launch.GameSkin
import com.openemux.mobile.launch.OverlayName
import com.openemux.mobile.launch.SkinGeometry
import com.openemux.mobile.launch.SkinRenderer
import com.openemux.mobile.launch.SkinSize
import com.openemux.mobile.launch.TABLETOP
import com.openemux.mobile.launch.overlayNames
import com.openemux.mobile.ui.Fold
import com.openemux.mobile.ui.InputMode
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.ProvideWindowLayout
import com.openemux.mobile.ui.ControllerLayoutPref
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.ButtonLabelsPref
import com.openemux.mobile.ui.PadLabels
import com.openemux.mobile.ui.WindowLayout
import com.openemux.mobile.ui.folds
import com.retroarch.BuildConfig
import com.retroarch.R
import com.retroarch.browser.retroactivity.RetroActivityFuture
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The RetroArch NativeActivity, with the in-game menu swapped for the
 * OpenEmux pause sheet. Runs in its own process (see the manifest).
 */
class GameActivity : RetroActivityFuture() {
    private var resumedAt = 0L
    var sessionStart = 0L
        private set
    private var dialog: ComponentDialog? = null
    private var sheetView: ComposeView? = null

    /** Read on the engine thread: whether the pause sheet takes the gamepad. */
    @Volatile private var sheetOpen = false

    /** Written on the engine thread (onToggleStates): whether fast forward is on, for the pause sheet. */
    @Volatile private var fastForwardOn = false
    /** Written on the engine thread (onToggleStates): whether slow motion is on, for the pause sheet. */
    @Volatile private var slowMotionOn = false
    /**
     * The speed the pause sheet's fast-forward and slow-motion tiles asked for and the engine has not been sent yet;
     * null = nothing pending. A sheet opened before it is applied shows it and takes it over.
     */
    @Volatile private var speedWanted: Speed? = null
    /** Bumped each time the pause sheet opens, so a deferred speed change knows a newer sheet took over. */
    @Volatile private var sheetCount = 0
    /** Armed by the sheet's "RetroArch menu" row: the next menu request opens RetroArch's own menu. */
    private val retroArchMenu = RetroArchMenuGate()
    private var stickKey = 0

    // The NDS overlay last shown: its orientation, and the layout each orientation was left on (engine thread only).
    private var ndsOrientation = "portrait"
    private val ndsLayouts = mutableMapOf<String, String>()
    private var ndsRestoring: String? = null
    /** The NDS screens in use, which the pause sheet shows and changes (main thread writes). */
    private val ndsShown = mutableStateOf(NdsShown("portrait", ""))
    /** Bumped when R3 steps the layout while the sheet is open, so the sheet names it (main thread). */
    private val ndsCycles = mutableIntStateOf(0)
    private var layoutToast: android.widget.Toast? = null
    /** The overlay switch the app asked for last and the engine has not shown yet (under ndsLayouts' lock). */
    private var ndsAsked: NdsShown? = null
    /** The app's overlay switches, one at a time and in order, so a quick second R3 never lands first. */
    private val ndsSwitches = Executors.newSingleThreadExecutor()
    private val r3 = PadShortcut.Press()

    // The window the skin in use was drawn for, and the one a redraw is under way for (skin thread writes).
    @Volatile private var skinSize: SkinSize? = null
    private val skinWanted = AtomicReference<SkinSize?>(null)
    private var skinSeq = 0
    private var engineReady = false
    private val skinThread = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val skinCheck = Runnable { checkSkinSize() }
    /** A fold turned up that the skin shown has no tabletop overlays for: redraw it at the same size. */
    @Volatile private var skinStale = false
    /** The fold a redraw was last asked for: one that still gives no tabletop overlays is not asked for again. */
    private var staleFold: Fold? = null

    /** The skin's tabletop overlays: the launcher's, then each redrawn skin's. */
    @Volatile private var tabletopSkin: TabletopSkin? = null
    /** The fold last seen, which a redrawn skin is drawn for. */
    @Volatile private var lastFold: Fold? = null

    // The overlay shown, which a redrawn skin opens on, the last flat one (to go back to), and whether the posture asks
    // for tabletop (under ndsLayouts' lock).
    private var overlay: String? = null
    private var flatOverlay: String? = null
    private var wantTabletop = false
    private var postureSeq = 0

    /** The skin's overlays, in skin.cfg's order: the launcher's, then each redrawn skin's. */
    @Volatile private var skinOverlays: List<String> = emptyList()
    /** Whether a gamepad is connected, which picks the skin's gamepad layout (under ndsLayouts' lock). */
    private var wantGamepad = false
    /** Debug builds: a gamepad said to be connected or not (DEBUG_GAMEPAD), over what the devices say (main thread). */
    private var gamepadOverride: Boolean? = null
    private var debugReceiver: BroadcastReceiver? = null

    // NativeActivity is no LifecycleOwner: the posture is followed from start to stop.
    private val scope = MainScope()
    private var postureJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the engine starts: it reports the overlays from its own thread.
        val skinDir = intent.getStringExtra("CONFIGFILE")?.let { File(File(it).parentFile, "skin") }
        tabletopSkin = skinDir?.let(::readTabletopSkin)
        skinOverlays = skinDir?.let(::readOverlays).orEmpty()
        wantGamepad = Gamepads.connected()
        super.onCreate(savedInstanceState)
        registerDebugGamepad()
        sessionStart = System.currentTimeMillis()
        if (intent.hasExtra(GameExtras.LOAD_SLOT)) loadSlotWhenRunning(intent.getIntExtra(GameExtras.LOAD_SLOT, 0))
        skinSize = SkinSize.parse(intent.getStringExtra(GameExtras.SKIN_SIZE))
        // The launcher drew for the largest window: a game opened in split screen or free form needs its own.
        window.decorView.post { scheduleSkinCheck() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleSkinCheck()
    }

    /** An unfocused engine stops polling commands: a redraw it missed is tried again once it has focus back. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleSkinCheck()
    }

    override fun onDestroy() {
        debugReceiver?.let(::unregisterReceiver)
        debugReceiver = null
        handler.removeCallbacks(skinCheck)
        skinThread.shutdownNow()
        ndsSwitches.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    /** A free-form drag sends a burst of changes: look once it settles. */
    private fun scheduleSkinCheck() {
        if (Build.VERSION.SDK_INT < 30 || isFinishing || isDestroyed) return
        handler.removeCallbacks(skinCheck)
        handler.postDelayed(skinCheck, SKIN_DEBOUNCE_MS)
    }

    private fun checkSkinSize() {
        if (Build.VERSION.SDK_INT < 30 || isFinishing || isDestroyed || skinThread.isShutdown) return
        // Unfocused, the engine stops polling: an OVERLAY_LOAD sent now would wait in its socket and be
        // replayed, stale, once focus is back. onWindowFocusChanged looks again then.
        if (!hasWindowFocus()) return
        val size = SkinSize.of(windowManager.currentWindowMetrics)
        if (!skinStale && !size.differs(skinWanted.get() ?: skinSize)) return
        skinWanted.set(size)
        // One redraw at a time; a newer window supersedes one still waiting.
        skinThread.execute {
            // shutdownNow() in onDestroy interrupts a wait for the engine: an uncaught exception here would kill :game.
            try {
                if (skinWanted.get() === size) {
                    // Back to the window the shown skin was drawn for: nothing to redraw.
                    if (skinStale || size.differs(skinSize)) redrawSkin(size) else skinWanted.compareAndSet(size, null)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Draws the skin for [size] into a fresh directory (the engine skips
     * reloading a path it already has) and swaps it in with OVERLAY_LOAD,
     * the core running on. A refused one leaves the old skin in place.
     */
    private fun redrawSkin(size: SkinSize) {
        val launchDir = intent.getStringExtra(GameExtras.LAUNCH_DIR)?.let(::File)
        val console = Console.byId(intent.getStringExtra(GameExtras.CONSOLE))
        val base = intent.getStringExtra(GameExtras.SKINS_BASE)?.let(::File)
        if (launchDir == null || console == null || base == null || !engineRunning()) {
            skinWanted.compareAndSet(size, null)
            return
        }
        val dir = File(launchDir, "skin-${++skinSeq}")
        val stale = skinStale
        skinStale = false
        val bounds = windowManager.currentWindowMetrics.bounds
        val first = overlayToReopen()
        val reply = runCatching {
            // Chosen as at launch: the console's package skin, else the drawn one.
            val skin = GameSkin.render(
                this, base, console, size, dir,
                density = resources.displayMetrics.density,
                hardcore = intent.getBooleanExtra(GameExtras.HARDCORE, false),
                coreId = intent.getStringExtra(GameExtras.CORE_ID),
                first = first,
                gamepad = synchronized(ndsLayouts) { wantGamepad },
                // Drawn upright, as the launcher does: the fold goes with it.
                fold = SkinGeometry.portraitFold(lastFold, bounds.width(), bounds.height()),
            ).file
            RaCommand.ask("OVERLAY_LOAD ${skin.absolutePath}", 2000)
        }.getOrNull()
        val ok = reply?.startsWith("OK") == true
        if (ok) {
            Log.i(SKIN_TAG, "OVERLAY_LOAD skin for $size: ${dir.name}")
            skinSize = size
            followRedrawnSkin(readTabletopSkin(dir), readOverlays(dir))
            launchDir.listFiles { f -> f.isDirectory && f.name.startsWith("skin") && f != dir }?.forEach { it.deleteRecursively() }
        } else {
            Log.w(SKIN_TAG, "Skin for $size not loaded, keeping the one shown")
            // A redraw for the fold is still owed: the next look (focus back, a new window) tries again.
            if (stale) skinStale = true
            // No answer is not a refusal: the engine may be loading this skin right now, so it stays on disk.
            if (reply != null) dir.deleteRecursively()
        }
        // Only this redraw's own request is cleared: a newer one set meanwhile still runs.
        skinWanted.compareAndSet(size, null)
    }

    private fun readTabletopSkin(dir: File): TabletopSkin? =
        runCatching { TabletopSkin.parse(File(dir, SkinRenderer.TABLETOP_FILE).readText()) }.getOrNull()

    /** Whether [a] and [b] are the same page, whatever NDS layout each names. */
    private fun samePage(a: String, b: String?): Boolean {
        if (b == null) return false
        if (a == b) return true
        val pa = OverlayName.parse(a) ?: return false
        val pb = OverlayName.parse(b) ?: return false
        return pa.copy(layout = null) == pb.copy(layout = null)
    }

    private fun readOverlays(dir: File): List<String> =
        runCatching { overlayNames(File(dir, "skin.cfg").readText()) }.getOrDefault(emptyList())

    /**
     * The engine opens a redrawn skin on its first overlay and says nothing: the drawn skin puts the one shown first
     * when it still has it, a package skin only its own flat ones. A tabletop overlay is selected again by name (a
     * no-op when it is the one shown); one the new window has none of is left for the flat one, which is selected too,
     * and the core goes back with it. A package skin opens on its own page: a gamepad page shown is selected again
     * too. Then the posture and the gamepad are looked at again, against the new skin.
     */
    private fun followRedrawnSkin(tabletop: TabletopSkin?, overlays: List<String>) {
        val target = synchronized(ndsLayouts) {
            val shown = overlay?.takeIf { TABLETOP in it }
            val target = when {
                // A package skin opens on its own page: one shown in another mode is selected again. The drawn
                // skin opened on overlayToReopen(), which may name another NDS layout of the same page: kept.
                shown == null -> overlay?.takeIf { it in overlays && !samePage(it, overlays.firstOrNull()) }
                tabletop?.overlays?.contains(shown) == true -> {
                    // The same tabletop layout around a fold the new window puts elsewhere.
                    if (ndsScreens) tabletop?.gaps?.get(shown)?.let { gap -> Thread { RaCommand.send("SET_CORE_OPTION desmume_screens_gap $gap") }.start() }
                    shown
                }
                else -> {
                    // The old skin's band and package layout still apply to leaving it, in the mode shown.
                    wantTabletop = false
                    val mode = OverlayName.parse(shown)
                    val flat = OverlayName.parse(flatTarget() ?: "portrait")
                    val back = if (mode != null && flat != null) available(flat.withModeOf(mode), overlays) else null
                    (back ?: "portrait").also(::onOverlayChanged)
                }
            }
            tabletopSkin = tabletop
            skinOverlays = overlays
            target
        }
        // Outside the lock, which the engine thread takes to report the switch; before the posture is looked at
        // again, so a switch that sends comes after this one.
        if (target != null) RaCommand.ask("OVERLAY_SELECT $target", 300)
        runOnUiThread {
            if (!isDestroyed) {
                onFold(lastFold)
                followGamepad()
            }
        }
    }

    /**
     * The overlay a redrawn skin opens on. An NDS layout picked from the pause
     * sheet leaves the hidden overlay behind: reopen on the layout the core shows.
     */
    private fun overlayToReopen(): String? = synchronized(ndsLayouts) {
        val shown = overlay ?: return null
        val name = OverlayName.parse(shown)
        if (!ndsScreens || name?.layout == null) return shown
        ndsLayouts[name.orientation]?.let { name.copy(layout = it).toString() } ?: shown
    }

    /** The engine answers commands once the content is up; a game opened in split screen gets here early. */
    private fun engineRunning(): Boolean {
        val deadline = System.currentTimeMillis() + 30_000
        while (!engineReady && System.currentTimeMillis() < deadline) {
            engineReady = RaCommand.isRunning(300)
            if (!engineReady) Thread.sleep(250)
        }
        return engineReady
    }

    /** Opening at a chosen state: wait for the content, then load it. */
    private fun loadSlotWhenRunning(slot: Int) {
        Thread {
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                if (RaCommand.ask("GET_STATUS", 300)?.contains("PLAYING") == true) {
                    val reply = RaCommand.ask("LOAD_STATE_SLOT $slot", 3000)
                    if (reply?.contains("ERROR") == true) runOnUiThread {
                        android.widget.Toast.makeText(this, getString(R.string.game_toast_state_saved_other_core), android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                Thread.sleep(250)
            }
        }.start()
    }

    @OptIn(FlowPreview::class)
    override fun onStart() {
        super.onStart()
        // Followed without tabletop overlays too: a skin redrawn for a fold gets them.
        postureJob?.cancel()
        // Postures flicker while the device is being folded: act once it settles.
        postureJob = scope.launch { folds().distinctUntilChanged().debounce(250).collect(::onFold) }
        followGamepad()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        super.onInputDeviceAdded(deviceId)
        followGamepad()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        super.onInputDeviceChanged(deviceId)
        followGamepad()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        super.onInputDeviceRemoved(deviceId)
        followGamepad()
    }

    /** A gamepad connected or gone (main thread): the skin switches to its gamepad layout or back; the core keeps running. */
    private fun followGamepad() {
        val pad = gamepadOverride ?: Gamepads.connected()
        // This process has no MainActivity to follow the pads for the pause sheet's gamepad layout.
        InputMode.padConnected = pad
        synchronized(ndsLayouts) {
            wantGamepad = pad
            switchGamepad()
        }
    }

    /**
     * Under ndsLayouts' lock: sends the overlay the gamepad asks for, if it is not the one shown. True when it sent
     * one: the overlay shown is about to change.
     */
    private fun switchGamepad(): Boolean {
        val current = overlay ?: skinOverlays.firstOrNull() ?: return false
        val target = gamepadTarget(current, wantGamepad, skinOverlays) ?: return false
        if (BuildConfig.DEBUG) Log.d(TAG, "gamepad $wantGamepad: $current -> $target")
        val seq = ++postureSeq
        Thread { selectOverlay(target, seq) }.start()
        return true
    }

    /**
     * Debug builds only: QA has no gamepad on the emulator, so one is said to be connected or not with
     * `adb shell am broadcast -a com.openemux.mobile.DEBUG_GAMEPAD --ez connected true|false` (`--ez clear true` goes
     * back to the devices). Only the shell (it holds DUMP) can send it.
     */
    private fun registerDebugGamepad() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // A vendor says which pad names the buttons in the pause sheet, the way it does in the library.
                if (PadLabels.debug(intent)) return
                gamepadOverride = if (intent.getBooleanExtra("clear", false)) null else intent.getBooleanExtra("connected", true)
                Log.i(TAG, "debug gamepad: $gamepadOverride")
                followGamepad()
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(DEBUG_GAMEPAD), android.Manifest.permission.DUMP, null, ContextCompat.RECEIVER_EXPORTED,
        )
        debugReceiver = receiver
    }

    override fun onStop() {
        postureJob?.cancel()
        postureJob = null
        super.onStop()
    }

    /** The posture changed: switch to the tabletop skin, or back to the flat one, by name; the core keeps running. */
    private fun onFold(fold: Fold?) {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
        val density = resources.displayMetrics.density
        val window = WindowLayout.of(bounds.width() / density, bounds.height() / density, fold = fold, density = density)
        lastFold = fold
        val skin = tabletopSkin
        val want = wantsTabletop(window, skin)
        // A fold the skin was not drawn for (it showed up after the window changed, or a redraw drew for the fold of
        // the window before): the skin is drawn again for it.
        if (fold != staleFold && Build.VERSION.SDK_INT >= 30 &&
            skinMissesFold(SkinGeometry.tabletopBandFor(fold, bounds.width(), bounds.height(), density), density, skin)
        ) {
            staleFold = fold
            skinStale = true
            scheduleSkinCheck()
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "fold $fold in ${bounds.width()}x${bounds.height()}: tabletop $want")
        synchronized(ndsLayouts) {
            wantTabletop = want
            switchPosture()
        }
    }

    /**
     * Under ndsLayouts' lock: sends the overlay the posture asks for, if it is not the one shown. True when it sent
     * one: the overlay shown is about to change.
     */
    private fun switchPosture(): Boolean {
        val skin = tabletopSkin ?: return false
        val current = overlay ?: skin.overlays.first()
        // The engine reports no overlay until the first switch: the one it opened on is the flat one to come back to.
        if (flatOverlay == null && TABLETOP !in current) flatOverlay = current
        if (ndsScreens) loadNdsLayouts()
        // A newer switch supersedes one still waiting for the engine.
        val seq = ++postureSeq
        val posture = tabletopTarget(current, wantTabletop, skin.overlays, flatTarget(), ndsLayouts[TABLETOP]) ?: return false
        // In the layout the gamepad asks for, which may have changed while the posture did.
        val target = gamepadTarget(posture, wantGamepad, skinOverlays) ?: posture
        Thread { selectOverlay(target, seq) }.start()
        return true
    }

    /**
     * Under ndsLayouts' lock: the flat overlay to go back to. On NDS, the portrait layout in use: the pause sheet may
     * have changed it under the hidden overlay, and landing on another would step the overlays on from there, past
     * the portrait ones.
     */
    private fun flatTarget(): String? {
        if (ndsScreens) loadNdsLayouts()
        return flatOverlay?.let { f ->
            val name = OverlayName.parse(f)
            if (ndsScreens && name != null && name.orientation == "portrait" && name.layout != null) name.copy(layout = ndsLayouts["portrait"]).toString() else f
        }
    }

    /**
     * Sends OVERLAY_SELECT until the engine answers it: a game opened half open sees its posture before the engine's
     * command interface and overlay are up, and a datagram sent then is lost. Gives up when a newer switch [seq] comes.
     */
    private fun selectOverlay(target: String, seq: Int) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && synchronized(ndsLayouts) { seq == postureSeq }) {
            val reply = RaCommand.ask("OVERLAY_SELECT $target", 300)
            if (reply != null && "no overlay is loaded" !in reply) return
            Thread.sleep(250)
        }
    }

    override fun onResume() {
        super.onResume()
        resumedAt = System.currentTimeMillis()
    }

    override fun onPause() {
        // A request armed, or still waiting to arm, when leaving must not reach a later sheet request.
        retroArchMenu.cancel()
        flushPlaytime()
        super.onPause()
    }

    /**
     * Called on the engine thread when fast forward, turbo or slow motion goes on or off, whatever flipped it: the
     * skin's strip, the pause sheet or a gamepad hotkey. The strip draws its own "on" look (overlayN_descM_lit_by);
     * the pause sheet reads fast forward and slow motion from here.
     */
    override fun onToggleStates(fastForward: Boolean, turbo: Boolean, slowMotion: Boolean) {
        fastForwardOn = fastForward
        slowMotionOn = slowMotion
    }

    /**
     * Called on the engine thread when the skin's overlay changes; a redrawn
     * skin opens on it. An NDS
     * overlay is named "<orientation>@<layout>": DeSmuME is set to that
     * screen layout, which is kept for the orientation.
     */
    override fun onOverlayChanged(name: String): Unit = synchronized(ndsLayouts) {
        val previous = overlay
        overlay = name
        if (TABLETOP !in name) flatOverlay = name
        // The overlay shown is not the one the posture asks for: turned back upright while half open (auto-rotate
        // picked the flat overlay), or a switch that reached the engine after the posture changed again. The overlay
        // switched to sets the core; stepping this one to its saved layout would race the switch.
        if ((TABLETOP in name) != wantTabletop && switchPosture()) return
        // The same for the gamepad: connected or gone while the overlay was switching.
        val page = OverlayName.parse(name)
        if (page != null && page.gamepad != wantGamepad && switchGamepad()) return
        if (!ndsScreens) return
        // Back from tabletop to a package skin, whose overlays name no layout: the screens one over the other, no gap.
        val flatLayout = tabletopSkin?.flatLayout
        if (flatLayout != null && TABLETOP !in name && previous != null && TABLETOP in previous) {
            Thread {
                RaCommand.send("SET_CORE_OPTION desmume_screens_gap 0")
                RaCommand.send("SET_CORE_OPTION desmume_screens_layout $flatLayout")
            }.start()
            return
        }
        val orientation = page?.orientation ?: return
        val layout = NdsLayouts.find(orientation, page.layout) ?: return
        loadNdsLayouts()
        val sameOrientation = orientation == ndsOrientation
        if (!sameOrientation) ndsRestoring = ndsLayouts[orientation]
        ndsOrientation = orientation
        // Turning lands on the layout saved at launch: on to the one used last here, in the mode shown (stepping to
        // the next overlay would leave the mode when the skin opened on a page pulled ahead of its own).
        val restoring = ndsRestoring
        if (restoring != null && restoring != layout.id) {
            val target = page.copy(layout = restoring).toString()
            Thread { RaCommand.ask("OVERLAY_SELECT $target", 300) }.start()
            return
        }
        ndsRestoring = null
        // R3 pressed again before the engine showed the first switch: the newer one lands next.
        if (NdsLayouts.superseded(ndsAsked, orientation, layout.id)) return
        if (ndsAsked?.orientation == orientation) ndsAsked = null
        // The skin's screens key steps the overlay: name the layout it landed on, as R3 does.
        if (NdsLayouts.announce(sameOrientation, ndsLayouts[orientation], layout.id)) runOnUiThread { showLayoutToast(layout) }
        ndsLayouts[orientation] = layout.id
        publishNdsShown(orientation, layout.id)
        // One per half puts the fold in the gap between the screens; every other layout has none.
        val gap = tabletopSkin?.let { it.gaps[name] ?: 0 }
        Thread {
            NdsLayouts.save(filesDir, orientation, layout)
            gap?.let { RaCommand.send("SET_CORE_OPTION desmume_screens_gap $it") }
            RaCommand.send("SET_CORE_OPTION desmume_screens_layout ${layout.value}")
        }.start()
    }

    /** NDS on DeSmuME: the skin and the pause sheet switch its screen layout. */
    private val ndsScreens by lazy {
        intent.getStringExtra(GameExtras.CONSOLE) == "NDS" && "desmume" in intent.getStringExtra("LIBRETRO").orEmpty()
    }

    private fun loadNdsLayouts() {
        if (ndsLayouts.isEmpty()) {
            NdsLayouts.load(filesDir).let { (p, l) -> ndsLayouts["portrait"] = p.id; ndsLayouts["landscape"] = l.id }
            ndsLayouts[TABLETOP] = NdsLayouts.loadTabletop(filesDir).id
        }
    }

    /** The orientation whose NDS layouts apply now: the tabletop overlay's when it is shown, else the window's. */
    private fun shownNdsOrientation(): String = NdsLayouts.orientation(
        tabletopShown = overlay?.let(OverlayName::parse)?.tabletop == true,
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )

    /** The NDS screens in use for the orientation shown. */
    private fun currentNdsShown(): NdsShown = synchronized(ndsLayouts) {
        loadNdsLayouts()
        val orientation = shownNdsOrientation()
        NdsShown(orientation, (NdsLayouts.find(orientation, ndsLayouts[orientation]) ?: NdsLayouts.default(orientation)).id)
    }

    /**
     * From R3, in game or on the sheet: the orientation's next layout in the gamepad cycle (R3 is a gamepad's, so side
     * by side is in it in landscape). The skin's screens key steps its pages itself (onOverlayChanged follows).
     */
    private fun nextNdsLayout(): ScreenLayout = synchronized(ndsLayouts) {
        loadNdsLayouts()
        val orientation = shownNdsOrientation()
        applyNdsLayout(orientation, NdsLayouts.next(orientation, ndsLayouts[orientation], gamepad = true))
    }

    /**
     * Puts the orientation's screens on [layout]. An overlay that names its layout (the drawn skin, in whichever mode
     * it shows) switches with them, which sets the core; one that names none (a package skin's flat pages), or no
     * overlay answering, leaves the core to be set here.
     */
    private fun applyNdsLayout(orientation: String, layout: ScreenLayout): ScreenLayout = synchronized(ndsLayouts) {
        val shown = overlay?.let(OverlayName::parse)
        ndsLayouts[orientation] = layout.id
        publishNdsShown(orientation, layout.id)
        // Supersedes a switch still waiting for the engine, which would land on the old layout.
        ++postureSeq
        val name = shown?.takeIf { it.layout != null && it.orientation == orientation }?.copy(layout = layout.id)?.toString()
        val gap = if (orientation == TABLETOP) tabletopSkin?.gaps?.get(name ?: "$TABLETOP@${layout.id}") ?: 0 else null
        // The overlay already shown (its own layout picked again) is selected without a word from the engine: no
        // switch is coming to wait for, and a stale wait would swallow the skin's next screens key.
        val asked = NdsShown(orientation, layout.id).takeIf { name != null && name != overlay }
        ndsAsked = asked
        ndsSwitches.execute {
            val reply = name?.let { RaCommand.ask("OVERLAY_SELECT $it", 300) }
            if (reply == null || "no overlay is loaded" in reply) {
                // No overlay switch is coming: nothing to wait for.
                synchronized(ndsLayouts) { if (ndsAsked == asked) ndsAsked = null }
                NdsLayouts.save(filesDir, orientation, layout)
                gap?.let { RaCommand.send("SET_CORE_OPTION desmume_screens_gap $it") }
                RaCommand.send("SET_CORE_OPTION desmume_screens_layout ${layout.value}")
            }
        }
        layout
    }

    /** Tells the pause sheet which layout is in use, from whichever thread changed it. */
    private fun publishNdsShown(orientation: String, layout: String) {
        val shown = NdsShown(orientation, layout)
        // At once on the main thread: R3 on the sheet names the layout right after setting it.
        if (Looper.myLooper() == Looper.getMainLooper()) ndsShown.value = shown else handler.post { ndsShown.value = shown }
    }

    /** Names the layout just switched to, over the game: the sheet, when open, names it itself. */
    private fun showLayoutToast(layout: ScreenLayout) {
        if (sheetOpen || isFinishing) return
        layoutToast?.cancel()
        layoutToast = android.widget.Toast.makeText(this, getString(R.string.game_toast_screens_layout, getString(layout.label)), android.widget.Toast.LENGTH_SHORT)
            .also { it.show() }
    }

    /**
     * Called on the engine thread with R3 while the game plays (never while paused: the sheet gets it then). On the
     * NDS it steps the screen layout, in place of the core's own quick screen switch; both the press and the release
     * are taken, so the core never sees it. Anything else goes on to the core.
     */
    override fun onPadShortcut(keycode: Int, action: Int): Boolean {
        if (!PadShortcut.claims(keycode, ndsScreens)) return false
        if (r3.act(PadShortcut.Place.GAME, action)) runOnUiThread { showLayoutToast(nextNdsLayout()) }
        return true
    }

    /**
     * Called on the engine thread when RetroArch would open its menu. False lets it open its own: once, after the
     * sheet's "RetroArch menu" row.
     */
    override fun onExternalMenuRequested(): Boolean {
        if (retroArchMenu.consume()) return false
        runOnUiThread {
            // Unfocusable, the sheet never sees BACK: the engine does, and asks again.
            if (dialog?.isShowing == true) dialog?.dismiss() else showPauseSheet()
        }
        return true
    }

    /** Asked by the engine at start-up: closing the device saves the state. */
    override fun autoSaveOnPause(): Boolean = true

    /**
     * Called on the engine thread when the game is back after a pause that
     * saved it: the content is paused, so the sheet opens over it. Never a
     * toggle, unlike [onExternalMenuRequested]: a sheet left open stays open.
     */
    override fun onResumedIntoPause() {
        runOnUiThread {
            if (ReturnPause.shouldOpenSheet(dialog?.isShowing == true, isFinishing)) showPauseSheet()
        }
    }

    /**
     * Called on the engine thread with gamepad input while the content is
     * paused. The sheet's window cannot take focus (the engine must keep
     * it), so the sheet is driven from here.
     */
    override fun onExternalMenuInput(type: Int, keycode: Int, action: Int, x: Float, y: Float): Boolean {
        if (!sheetOpen) return false
        runOnUiThread { routeToSheet(type, keycode, action, x, y) }
        return true
    }

    private fun routeToSheet(type: Int, keycode: Int, action: Int, x: Float, y: Float) {
        val view = sheetView ?: return
        val d = dialog ?: return
        InputMode.gamepad = true
        if (type == INPUT_KEY) {
            when (keycode) {
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK ->
                    if (action == KeyEvent.ACTION_UP) d.onBackPressedDispatcher.onBackPressed()
                KeyEvent.KEYCODE_BUTTON_START -> if (action == KeyEvent.ACTION_UP) d.dismiss()
                KeyEvent.KEYCODE_BUTTON_THUMBR -> if (ndsScreens && r3.act(PadShortcut.Place.SHEET, action)) {
                    nextNdsLayout()
                    ndsCycles.intValue++
                }
                KeyEvent.KEYCODE_BUTTON_A -> view.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    view.dispatchKeyEvent(KeyEvent(action, keycode))
                }
            }
            return
        }
        // Stick or hat: one step per push.
        val key = when {
            x < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            x > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
            y < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
            y > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> 0
        }
        if (key != 0 && key != stickKey) {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }
        stickKey = key
    }

    private fun flushPlaytime() {
        if (resumedAt == 0L) return
        val path = intent.getStringExtra(GameExtras.PLAYTIME) ?: return
        val delta = (System.currentTimeMillis() - resumedAt) / 1000
        resumedAt = 0L
        runCatching {
            val file = File(path)
            val total = (runCatching { file.readText().trim().toLong() }.getOrDefault(0L)) + delta
            file.parentFile?.mkdirs()
            file.writeText(total.toString())
        }
        if (!isFinishing) resumedAt = System.currentTimeMillis()
    }

    /**
     * The engine turns fast forward off on any toggle it gets while paused (runloop.c, "Don't allow fastmotion
     * while paused") and ignores slow motion then ("Don't allow slowmotion while paused"), so the sheet's choice is
     * sent only after the resume has taken: wait until the engine reports it is playing, then toggle whatever still
     * differs. Blocks: call from a background thread. Gives up, and leaves the choice to the next sheet, when that
     * sheet opens first.
     */
    private fun applySpeed(sheet: Int) {
        if (!awaitPlaying(attempts = 20) { sheetCount != sheet }) {
            // Never seen running: drop the choice, so the next sheet shows the engine's own state.
            if (sheetCount == sheet) speedWanted = null
            return
        }
        val wanted = speedWanted ?: return
        if (sheetCount != sheet) return
        wanted.commandsFrom(fastForwardOn, slowMotionOn).forEach { RaCommand.send(it) }
        speedWanted = null
    }

    /**
     * Polls until the engine reports it is playing: true once it does, false when [attempts] run out, [deadlineMs]
     * (if given) passes, or [cancelled] says the wait no longer matters. A status request with no reply takes its
     * own timeout on top of the 50 ms between attempts, so only the deadline bounds the wait in time.
     * Blocks: call from a background thread.
     */
    private fun awaitPlaying(attempts: Int, deadlineMs: Long? = null, cancelled: () -> Boolean): Boolean {
        val deadline = deadlineMs?.let { System.currentTimeMillis() + it }
        repeat(attempts) {
            if (cancelled()) return false
            if (RaCommand.isPlaying()) return true
            if (deadline != null && System.currentTimeMillis() >= deadline) return false
            Thread.sleep(50)
        }
        return false
    }

    /**
     * After the sheet's "RetroArch menu" row: once the sheet's resume has taken, let the next menu request through
     * and ask for one. Armed only while the game plays: the engine unpauses after RetroArch's menu closes only when
     * the content was not paused before the request. Blocks: call from a background thread.
     */
    private fun openRetroArchMenu(request: Int) {
        // Never seen running within 2 s, or a newer sheet took over: the game simply resumed.
        if (!awaitPlaying(attempts = 40, deadlineMs = 2000L) { false }) return
        if (!retroArchMenu.arm(request)) return
        RaCommand.send("MENU_TOGGLE")
        // The engine answers within a frame; a request it never got must not open RetroArch's menu later.
        Thread.sleep(1000)
        retroArchMenu.disarm(request)
    }

    private fun showPauseSheet() {
        if (dialog?.isShowing == true || isFinishing) return
        // A "RetroArch menu" request still waiting for the game to play is overtaken by this sheet.
        retroArchMenu.cancel()
        val sheet = ++sheetCount
        // A choice from the previous sheet not sent yet is what the tile shows, and this sheet's dismissal sends it.
        val session = PauseSession.fromIntent(intent, sessionStart, speedWanted ?: Speed.of(fastForwardOn, slowMotionOn))
        // The sheet follows the app's theme; it may have changed since this process started.
        ThemePref.load(this)
        ControllerLayoutPref.load(this)
        InputMode.padConnected = gamepadOverride ?: Gamepads.connected()
        ButtonLabelsPref.load(this)
        // This process has no library screen to follow the pad in use: the pads connected now decide, read again on
        // every sheet so one plugged in (or swapped) since the last pause names the buttons. A debug override still wins.
        PadLabels.scan()
        Oe.syncSystem(this)
        if (ndsScreens) ndsShown.value = currentNdsShown()
        val d = object : ComponentDialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) InputMode.gamepad = false
                return super.dispatchTouchEvent(ev)
            }
        }
        val view = ComposeView(d.context).apply {
            setContent {
                ProvideWindowLayout {
                    PauseSheet(
                        session = session,
                        onResume = { d.dismiss() },
                        onQuit = {
                            sheetOpen = false
                            flushPlaytime()
                            d.setOnDismissListener(null)
                            d.dismiss()
                            Thread { RaCommand.send("QUIT") }.start()
                        },
                        screens = if (ndsScreens) NdsScreens(
                            shown = ndsShown,
                            cycles = ndsCycles,
                            pick = { layout -> applyNdsLayout(ndsShown.value.orientation, layout) },
                        ) else null,
                        onSpeed = { speedWanted = it },
                        onRetroArchMenu = {
                            // The dismissal resumes the game as Back to game does; the menu is asked for after.
                            d.dismiss()
                            val request = retroArchMenu.request()
                            Thread { openRetroArchMenu(request) }.start()
                        },
                    )
                }
            }
        }
        d.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        d.setOnDismissListener {
            sheetOpen = false
            val pending = speedWanted != null
            Thread {
                RaCommand.resume()
                if (pending) applySpeed(sheet)
            }.start()
        }
        d.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Keep the engine's window focused: unfocused, it stops polling
            // the command interface the sheet talks through.
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        dialog = d
        sheetView = view
        d.show()
        // Compose's own view takes key events only while it holds view focus.
        view.post {
            (view.getChildAt(0) ?: view).apply { isFocusableInTouchMode = true; requestFocus() }
        }
        sheetOpen = true
    }

    private companion object {
        /** AINPUT_EVENT_TYPE_KEY, as the engine passes it. */
        const val INPUT_KEY = 1
        const val SKIN_DEBOUNCE_MS = 300L
        const val TAG = "OpenEmuxGame"
        const val SKIN_TAG = "OpenEmuxSkin"
        const val DEBUG_GAMEPAD = "com.openemux.mobile.DEBUG_GAMEPAD"
    }
}
