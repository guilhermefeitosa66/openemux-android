package com.openemux.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.content.res.Configuration
import kotlinx.coroutines.flow.update
import com.openemux.mobile.game.PadInfo
import com.openemux.mobile.share.ShareNotifications
import com.openemux.mobile.ui.AppWindow
import com.openemux.mobile.ui.ControllerLayoutPref
import com.openemux.mobile.ui.LibraryZoomPref
import com.openemux.mobile.ui.ButtonLabelsPref
import com.openemux.mobile.ui.InputMode
import com.openemux.mobile.ui.LaunchGate
import com.openemux.mobile.ui.LocalPadScope
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.PadLabels
import com.openemux.mobile.ui.OpenEmuxRoot
import com.openemux.mobile.ui.PadPresence
import com.openemux.mobile.ui.PadRouter
import com.openemux.mobile.ui.PadScope
import com.openemux.mobile.ui.ProvideWindowLayout
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.immersive
import com.openemux.mobile.ui.padConnected
import com.openemux.mobile.ui.padRoot
import com.retroarch.BuildConfig
import com.retroarch.R

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest's launch theme is full screen, so the starting window has no status bar; the live window
        // must not keep FLAG_FULLSCREEN from it, as below API 30 that turns adjustResize off (the keyboard would
        // cover the text fields). Immersive mode hides the bars instead, before the first frame.
        setTheme(R.style.Theme_OpenEmux)
        ThemePref.load(this)
        ControllerLayoutPref.load(this)
        LibraryZoomPref.load(this)
        ButtonLabelsPref.load(this)
        Oe.syncSystem(this)
        super.onCreate(savedInstanceState)
        registerDebugGamepad()
        followLaunch(LaunchGate.fresh(restored = savedInstanceState != null, firstInProcess = AppWindow.firstLaunch))
        AppWindow.firstLaunch = false
        edgeToEdge(Oe.dark)
        immersive()
        setContent {
            val dark = Oe.dark
            LaunchedEffect(dark) { barIcons(dark) }
            CompositionLocalProvider(LocalPadScope provides padScope) {
                Box(Modifier.padRoot(padScope)) { ProvideWindowLayout { OpenEmuxRoot() } }
            }
        }
        // A recreated activity, or one relaunched from Recents, carries the link it was opened with: it was handled then.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleShareLink(intent)
            handleImport(intent)
            handleTransferNotification(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareLink(intent)
        handleImport(intent)
        handleTransferNotification(intent)
    }

    /** Files from "Open with" or "Share", forwarded by ImportActivity with their read grant: the library imports them. */
    private fun handleImport(intent: Intent?) {
        if (intent?.action != ImportActivity.ACTION_IMPORT) return
        val clip = intent.clipData ?: return
        // This activity is exported too: the guard against the app's own files holds here, not only in ImportActivity.
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.filter { isAcceptableImport(it) }
        // Files that arrive while others still wait join them instead of replacing them.
        if (uris.isNotEmpty()) (application as OpenEmuxApp).externalImports.update { (it.orEmpty() + uris).distinct() }
    }

    /**
     * An `openemux-share://` link only fills Connect by Address: it never
     * connects or sends on its own.
     */
    private fun handleShareLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "openemux-share") return
        (application as OpenEmuxApp).shareCoordinator.link.value = uri.toString()
    }

    /** A Share nearby notification reopens the progress view; an offer or a result shows from state anyway. */
    private fun handleTransferNotification(intent: Intent?) {
        if (intent?.getBooleanExtra(ShareNotifications.EXTRA_OPEN_OFFER, false) == true) {
            (application as OpenEmuxApp).shareCoordinator.showProgress()
        }
    }

    /** Edge to edge, with the status and navigation bar icons following the theme when a swipe shows the bars. */
    private fun edgeToEdge(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** A theme picked in the app only flips the bars' icons; the window keeps its full-screen setup. */
    private fun barIcons(dark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    /** Full screen, like the game: a swipe brings the bars back over the app and they leave on their own. */
    private fun immersive() = window.immersive()

    override fun onResume() {
        super.onResume()
        // Back from the game or a picker, the bars may be showing again.
        immersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // After a dialog, the shade or the recent apps.
        if (hasFocus) immersive()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        AppWindow.multiWindow = isInMultiWindowMode
        followGate()
    }

    /** When this launch began, for the gate's cap. */
    private val created = SystemClock.uptimeMillis()

    /** Whether the window last said the system bars are on screen (they are, until the hide reaches it). */
    private var barsVisible = true

    /** The system splash, kept over the app until the gate opens (API 31+). */
    private var heldSplash: View? = null

    /**
     * A launch keeps the splash look until the bars it brought are gone (see [LaunchGate]): from API 31 the system
     * splash itself is handed over and held, and below it the root covers the content with the background the
     * starting window shows.
     */
    private fun followLaunch(fresh: Boolean) {
        AppWindow.multiWindow = isInMultiWindowMode
        gate = LaunchGate(fresh).also { AppWindow.gate = it }
        if (gate.open) return
        if (android.os.Build.VERSION.SDK_INT >= 31) holdSplash()
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            barsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
            followGate()
            ViewCompat.onApplyWindowInsets(view, insets)
        }
        window.decorView.postDelayed(::followGate, LaunchGate.CAP_MS)
    }

    /** This activity's launch gate (the root reads it through [AppWindow]); a recreated instance gets its own. */
    private var gate = LaunchGate(fresh = false)

    @RequiresApi(31)
    private fun holdSplash() {
        splashScreen.setOnExitAnimationListener { splash ->
            heldSplash = splash
            followGate()
        }
    }

    private fun followGate() {
        if (!gate.update(barsVisible, AppWindow.multiWindow, SystemClock.uptimeMillis() - created)) return
        // Open for good: later insets changes (a swipe, the keyboard) have nothing to tell it.
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
        val splash = heldSplash ?: return
        heldSplash = null
        if (android.os.Build.VERSION.SDK_INT >= 31) releaseSplash(splash as SplashScreenView)
    }

    @RequiresApi(31)
    private fun releaseSplash(splash: SplashScreenView) {
        splash.animate().alpha(0f).setDuration(SPLASH_FADE_MS).withEndAction { splash.remove() }.start()
    }

    override fun onStart() {
        super.onStart()
        (application as OpenEmuxApp).retryArtwork()
        (application as OpenEmuxApp).setForeground(true)
        // A controller plugged in or out while the app was away is read again from the device list.
        val input = getSystemService(InputManager::class.java)
        input.registerInputDeviceListener(devices, Handler(Looper.getMainLooper()))
        pads.reset(InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id)?.let { id to padInfo(it) } }.toMap())
        followPads()
        // The pad in use names the buttons in the hints; the listener keeps it current while the app is in front.
        input.registerInputDeviceListener(PadLabels.listener, null)
        PadLabels.scan()
    }

    override fun onStop() {
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(devices)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(PadLabels.listener)
        (application as OpenEmuxApp).setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        debugReceiver?.let(::unregisterReceiver)
        debugReceiver = null
        super.onDestroy()
    }

    /** The controllers connected now, for the gamepad layout. */
    private val pads = PadPresence()

    /** Debug builds: a controller said to be connected or not (DEBUG_GAMEPAD), over what the devices say. */
    private var gamepadOverride: Boolean? = null
    private var debugReceiver: BroadcastReceiver? = null

    private val devices = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            InputDevice.getDevice(deviceId)?.let { pads.added(deviceId, padInfo(it)) }
            followPads()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            pads.removed(deviceId)
            followPads()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            InputDevice.getDevice(deviceId)?.let { pads.changed(deviceId, padInfo(it)) }
            followPads()
        }
    }

    private fun padInfo(device: InputDevice) = PadInfo(device.sources, device.isVirtual)

    private fun followPads() {
        InputMode.padConnected = padConnected(gamepadOverride, pads.connected)
    }

    /**
     * Debug builds only, for the emulator, which has no real controller:
     * `adb shell am broadcast -a com.openemux.mobile.DEBUG_GAMEPAD --ez connected true|false` (`--ez clear true` goes
     * back to the devices). Only a sender holding DUMP (the shell) gets through.
     */
    private fun registerDebugGamepad() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // A vendor (`--ei vendor <id> [--ei product <id>] [--es name <text>]`) says which pad names the buttons.
                if (PadLabels.debug(intent)) return
                gamepadOverride = if (intent.getBooleanExtra("clear", false)) null else intent.getBooleanExtra("connected", true)
                Log.i(TAG, "debug gamepad: $gamepadOverride")
                followPads()
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(DEBUG_GAMEPAD), android.Manifest.permission.DUMP, null, ContextCompat.RECEIVER_EXPORTED,
        )
        debugReceiver = receiver
    }

    /** The library's own window; sheets and dialogs get one each (PadDialogWindow). */
    private val padScope = PadScope()
    private val pad = PadRouter({ padScope }) { onBackPressedDispatcher.onBackPressed() }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // While a launch still covers the content, nothing under the cover takes a tap.
        if (!gate.open) return true
        pad.touch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!gate.open) return true
        // A pad that reports late counts as connected from its first press.
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) notePad(event.device, event.deviceId)
        // The pad pressed last names the buttons in the hints.
        PadLabels.pressed(event)
        return pad.key(event) { super.dispatchKeyEvent(it) }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!gate.open) return true
        // A pad whose first input is its stick counts as connected too: the D-pad keys the router makes from it
        // do not carry the joystick source.
        if (ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.actionMasked == MotionEvent.ACTION_MOVE) notePad(ev.device, ev.deviceId)
        return pad.motion(ev, { super.dispatchGenericMotionEvent(it) }, ::dispatchKeyEvent)
    }

    private fun notePad(device: InputDevice?, deviceId: Int) {
        if (device == null) return
        val was = pads.connected
        pads.padPress(deviceId, padInfo(device))
        if (pads.connected != was) followPads()
    }

    private companion object {
        const val TAG = "OpenEmux"
        const val DEBUG_GAMEPAD = "com.openemux.mobile.DEBUG_GAMEPAD"
        const val SPLASH_FADE_MS = 150L
    }
}
