package com.openemux.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.openemux.mobile.game.PadInfo
import com.openemux.mobile.ui.ControllerLayoutPref
import com.openemux.mobile.ui.ButtonLabelsPref
import com.openemux.mobile.ui.InputMode
import com.openemux.mobile.ui.LocalPadScope
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.PadLabels
import com.openemux.mobile.ui.OpenEmuxRoot
import com.openemux.mobile.ui.PadPresence
import com.openemux.mobile.ui.PadRouter
import com.openemux.mobile.ui.PadScope
import com.openemux.mobile.ui.ProvideWindowLayout
import com.openemux.mobile.ui.ThemePref
import com.openemux.mobile.ui.padConnected
import com.openemux.mobile.ui.padRoot
import com.retroarch.BuildConfig

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePref.load(this)
        ControllerLayoutPref.load(this)
        ButtonLabelsPref.load(this)
        Oe.syncSystem(this)
        super.onCreate(savedInstanceState)
        registerDebugGamepad()
        // The library keeps the system bars, as the splash before it shows them: hiding them would only make them
        // flash in during the launch transition, which the app cannot reach. Only the game runs full screen.
        systemBars(Oe.dark)
        setContent {
            val dark = Oe.dark
            LaunchedEffect(dark) { systemBars(dark) }
            CompositionLocalProvider(LocalPadScope provides padScope) {
                Box(Modifier.padRoot(padScope)) { ProvideWindowLayout { OpenEmuxRoot() } }
            }
        }
        // A recreated activity, or one relaunched from Recents, carries the link it was opened with: it was handled then.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleShareLink(intent)
            handleImport(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareLink(intent)
        handleImport(intent)
    }

    /** Files from "Open with" or "Share", forwarded by ImportActivity with their read grant: the library imports them. */
    private fun handleImport(intent: Intent?) {
        if (intent?.action != ImportActivity.ACTION_IMPORT) return
        val clip = intent.clipData ?: return
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isNotEmpty()) (application as OpenEmuxApp).externalImports.value = uris
    }

    /**
     * An `openemux-share://` link only fills Connect by Address: it never
     * connects or sends on its own. The incoming-offer notification needs
     * nothing here: bringing the activity up shows the sheet from state.
     */
    private fun handleShareLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "openemux-share") return
        (application as OpenEmuxApp).shareCoordinator.link.value = uri.toString()
    }

    /** Edge to edge, with the status and navigation bar icons following the theme, not the system. */
    private fun systemBars(dark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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
        pad.touch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A pad that reports late counts as connected from its first press.
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) notePad(event.device, event.deviceId)
        // The pad pressed last names the buttons in the hints.
        PadLabels.pressed(event)
        return pad.key(event) { super.dispatchKeyEvent(it) }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
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
    }
}
