package com.openemux.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.openemux.mobile.ui.LocalPadScope
import com.openemux.mobile.ui.PadRouter
import com.openemux.mobile.ui.PadScope
import com.openemux.mobile.ui.padRoot
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OpenEmuxRoot
import com.openemux.mobile.ui.ProvideWindowLayout
import com.openemux.mobile.ui.ThemePref

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePref.load(this)
        Oe.syncSystem(this)
        super.onCreate(savedInstanceState)
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
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handleShareLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareLink(intent)
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
    }

    override fun onStop() {
        (application as OpenEmuxApp).setForeground(false)
        super.onStop()
    }

    /** The library's own window; sheets and dialogs get one each (PadDialogWindow). */
    private val padScope = PadScope()
    private val pad = PadRouter({ padScope }) { onBackPressedDispatcher.onBackPressed() }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        pad.touch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pad.key(event) { super.dispatchKeyEvent(it) }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        pad.motion(ev, { super.dispatchGenericMotionEvent(it) }, ::dispatchKeyEvent)
}
