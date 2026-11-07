package com.openemux.mobile

import android.app.Dialog
import android.app.LocaleManager
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Picking a language from the phone's sheet crashed 1.21.0: the sheet is a dialog, its context a
 * ContextThemeWrapper, and the picker cast it to an Activity. Here [findActivity] reaches the activity
 * through a real dialog's context, and the sheet itself switches the language without the process
 * dying (a crash fails the run). The language the app had is put back before the activity closes.
 */
@RunWith(AndroidJUnit4::class)
class ActivityContextInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val locales get() = context.getSystemService(LocaleManager::class.java)

    @Test
    fun aDialogsContextLeadsToItsActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = Dialog(activity)
                assertTrue("a dialog's context is a wrapper, not the activity", dialog.context !== activity)
                assertSame(activity, dialog.context.findActivity())
                assertSame(activity, ContextThemeWrapper(ContextThemeWrapper(activity, 0), 0).findActivity())
                assertSame(activity, activity.findActivity())
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun theApplicationHasNoActivity() {
        context.applicationContext.findActivity()
    }

    @Test
    fun pickingALanguageFromThePhoneSheetSwitchesIt() {
        assumeTrue("per-app languages need API 33", Build.VERSION.SDK_INT >= 33)
        val before = locales.applicationLocales
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Languages change only with the activity in front: with none, the system restarts the
            // process, and the test runner with it.
            try {
                // Start from English, so the labels below are known and German is a change.
                setLanguage(LocaleList.forLanguageTags("en"))
                // Portrait on a phone puts the language list in a sheet, the path that crashed.
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                assumeTrue("first-run setup not done on this device", waitFor(20_000) { find("Settings") != null })
                tap("Settings")
                assumeTrue("a split layout shows a pane, not the sheet", waitFor(5_000) { find("Language") != null })
                tap("Language")
                assertTrue("the language sheet opened", waitFor(5_000) { find("Deutsch") != null })
                tap("Deutsch")
                // The activity is recreated in German; a ClassCastException would have killed the process.
                assertTrue("the app came back in German", waitFor(10_000) { find("Einstellungen") != null })
                assertEquals("de", locales.applicationLocales.get(0)?.language)
            } finally {
                setLanguage(before)
            }
        }
    }

    private fun setLanguage(list: LocaleList) {
        if (locales.applicationLocales == list) return
        instrumentation.runOnMainSync { locales.applicationLocales = list }
        // Let the activity be recreated in it before going on.
        instrumentation.waitForIdleSync()
        SystemClock.sleep(1_500)
    }

    private fun waitFor(ms: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(check).getOrDefault(false)) return true
            SystemClock.sleep(200)
        }
        return false
    }

    private fun nodes(n: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (n == null) return@sequence
        yield(n)
        for (i in 0 until n.childCount) yieldAll(nodes(n.getChild(i)))
    }

    // The active window: the activity's, or the sheet's own once it is open.
    private fun find(text: String): AccessibilityNodeInfo? =
        nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == text }

    /** A real tap at the label's centre, the way a finger reaches the clickable row around it. */
    private fun tap(text: String) {
        val bounds = Rect().also { checkNotNull(find(text)) { "'$text' not on screen" }.getBoundsInScreen(it) }
        instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").close()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
    }
}
