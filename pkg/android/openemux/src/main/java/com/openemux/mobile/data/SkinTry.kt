package com.openemux.mobile.data

import com.openemux.mobile.launch.SkinBinds

/** The things a control sends that the editor's Try mode names in words of the app's language (issue 785). */
enum class TryWord {
    DPAD, DPAD2, STICK, RIGHT_STICK, UP, DOWN, LEFT, RIGHT, MENU, QUICK_SAVE, QUICK_LOAD, FAST_FORWARD_HOLD,
    FAST_FORWARD, TURBO, SLOW_MOTION, SCREENSHOT, RESTART, SCREENS, KEYPAD_PAGE, DISK,
}

/** What a control sends, as Try mode says it: a word, a key of the keypad, or the name printed on the button. */
sealed interface BindWords {
    data class Word(val word: TryWord) : BindWords

    /** A keypad key: its digit, `*` or `#`. */
    data class Key(val key: String) : BindWords

    /** A button by the name a console prints on it: A, L2, Start, C-↑… */
    data class Name(val text: String) : BindWords
}

object SkinTry {
    /** How many presses the "Last" row keeps. */
    const val RECENT = 4

    private val WORDS = mapOf(
        SkinFormat.DPAD_BIND to TryWord.DPAD, SkinBinds.DPAD2 to TryWord.DPAD2,
        "analogStick" to TryWord.STICK, "rightAnalogStick" to TryWord.RIGHT_STICK,
        "up" to TryWord.UP, "down" to TryWord.DOWN, "left" to TryWord.LEFT, "right" to TryWord.RIGHT,
        "menu" to TryWord.MENU, "quickSave" to TryWord.QUICK_SAVE, "quickLoad" to TryWord.QUICK_LOAD,
        "fastForward" to TryWord.FAST_FORWARD_HOLD, "toggleFastForward" to TryWord.FAST_FORWARD,
        "turbo" to TryWord.TURBO, "slowMotion" to TryWord.SLOW_MOTION, "screenshot" to TryWord.SCREENSHOT,
        "restart" to TryWord.RESTART, "reset" to TryWord.RESTART, "screens" to TryWord.SCREENS,
        SkinFormat.KEYPAD_TOGGLE to TryWord.KEYPAD_PAGE, SkinBinds.DISK_INPUT to TryWord.DISK,
    )

    private val NAMES = mapOf(
        "l1" to "L", "r1" to "R", "start" to "Start", "select" to "Select", "mode" to "Mode",
        "option1" to "Option 1", "option2" to "Option 2",
        "cUp" to "C-↑", "cDown" to "C-↓", "cLeft" to "C-←", "cRight" to "C-→",
    )

    /** What [bind] sends, in words. */
    fun words(bind: String): BindWords {
        WORDS[bind]?.let { return BindWords.Word(it) }
        NAMES[bind]?.let { return BindWords.Name(it) }
        return when {
            bind == "keypadStar" -> BindWords.Key("*")
            bind == "keypadHash" -> BindWords.Key("#")
            bind.startsWith("keypad") -> BindWords.Key(bind.removePrefix("keypad"))
            else -> BindWords.Name(bind.uppercase())
        }
    }

    /** [recent] with [name] pressed last, at most [RECENT] long. */
    fun remember(recent: List<String>, name: String): List<String> = (recent + name).takeLast(RECENT)
}
