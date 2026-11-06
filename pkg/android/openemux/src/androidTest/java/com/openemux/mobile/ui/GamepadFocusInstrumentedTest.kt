package com.openemux.mobile.ui

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openemux.mobile.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Issue 701, on a device: right after each navigation with a gamepad, the focused node is the game or
 * Play, never the back arrow, the star, the ⋯ menu, a chip or search, and the hint bar's A names it.
 * Needs a device past the first-run setup; seeds four games named to sort first among the SNES ones.
 */
@RunWith(AndroidJUnit4::class)
class GamepadFocusInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val titles = listOf("AAA Focus 1", "AAA Focus 2", "AAA Focus 3", "AAA Focus 4")
    private val dir get() = File(context.getExternalFilesDir(null), "roms/SFC")

    @Before
    fun seed() {
        dir.mkdirs()
        titles.forEach { File(dir, "$it.sfc").writeBytes(ByteArray(16)) }
    }

    @After
    fun clean() {
        titles.forEach { File(dir, "$it.sfc").delete() }
    }

    @Test
    fun portrait() = walk(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    @Test
    fun landscape() = walk(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

    private fun walk(orientation: Int) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = orientation }
            val ready = waitFor(20_000) { hasText("SNES") }
            assumeTrue("first-run setup not done on this device", ready)
            // The first press only enters gamepad mode; then walk the shelf to the SNES shelf.
            pad(KeyEvent.KEYCODE_DPAD_DOWN)
            // Portrait is one column of shelves; landscape puts them in a column right of "All" and "Favorites".
            val route = List(8) { KeyEvent.KEYCODE_DPAD_DOWN } + KeyEvent.KEYCODE_DPAD_RIGHT +
                List(8) { KeyEvent.KEYCODE_DPAD_DOWN } + List(8) { KeyEvent.KEYCODE_DPAD_UP }
            for (key in route) { if (focused()?.contains("SNES") == true) break; pad(key) }
            assertTrue("SNES shelf focused, got ${focused()}", focused()?.contains("SNES") == true)

            pad(KeyEvent.KEYCODE_BUTTON_A)
            expectFocus(titles[0], "the console's first game")
            assertTrue("hint A: Details", hasText("Details"))

            pad(KeyEvent.KEYCODE_BUTTON_A)
            expectFocus(Regex("Play|Continue"), "Play on the game's detail")
            // Above Play the pencil after the name is a stop (issue 790); the header's back arrow, star (X)
            // and Share (Y) are not.
            pad(KeyEvent.KEYCODE_DPAD_UP)
            expectFocus(Regex("Rename"), "the pencil after the game's name")
            repeat(2) {
                pad(KeyEvent.KEYCODE_DPAD_UP)
                val label = focused().orEmpty()
                assertTrue("the header took focus: '$label'", chrome.none { label.equals(it, ignoreCase = true) })
            }

            pad(KeyEvent.KEYCODE_BUTTON_B)
            expectFocus(titles[0], "the game that was opened")

            // Away to the previous filter (Favorites may be empty) and back: the first game, not the chrome.
            pad(KeyEvent.KEYCODE_BUTTON_L1)
            pad(KeyEvent.KEYCODE_BUTTON_R1)
            expectFocus(titles[0], "the first game after L1 and R1 back to the console")

            // Up the header: only content and actions without a button of their own take focus.
            repeat(4) {
                pad(KeyEvent.KEYCODE_DPAD_UP)
                val label = focused().orEmpty()
                assertTrue("chrome took focus: '$label'", chrome.none { label.equals(it, ignoreCase = true) })
            }
        }
    }

    private val chrome = listOf("Shelf", "Search", "Back", "Share Nearby", "All", "Favorites", "SNES", "NES", "GBA")

    private fun expectFocus(title: String, what: String) = expectFocus(Regex(Regex.escape(title)), what)

    private fun expectFocus(pattern: Regex, what: String) {
        val ok = waitFor(4_000) { focused()?.let { pattern.containsMatchIn(it) } == true }
        assertTrue("expected focus on $what, got '${focused()}'", ok)
        // And it stays there once the screen has settled.
        SystemClock.sleep(FocusHoldMs + 300)
        assertEquals("focus moved away from $what", true, focused()?.let { pattern.containsMatchIn(it) })
    }

    private fun pad(code: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(
                KeyEvent(now, SystemClock.uptimeMillis(), action, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD),
            )
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
    }

    private fun waitFor(ms: Long, check: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(check).getOrDefault(false)) return true
            SystemClock.sleep(200)
        }
        return false
    }

    private fun root(): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow

    private fun nodes(n: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (n == null) return@sequence
        yield(n)
        for (i in 0 until n.childCount) yieldAll(nodes(n.getChild(i)))
    }

    private fun label(n: AccessibilityNodeInfo): String? =
        nodes(n).firstNotNullOfOrNull { (it.text ?: it.contentDescription)?.toString()?.takeIf(String::isNotBlank) }

    /** The label of the focused node (its own text, or its first descendant's), null when nothing holds focus. */
    private fun focused(): String? = nodes(root()).firstOrNull { it.isFocused }?.let(::label)

    private fun hasText(text: String): Boolean = nodes(root()).any { it.text?.toString() == text }
}
