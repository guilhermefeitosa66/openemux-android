package com.openemux.mobile.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartCommandTest {
    // The bare word is the engine's reset hotkey, which asks for a second press.
    @Test fun theSheetSendsTheActionNotTheHotkey() {
        assertEquals("RESET off", RESTART_COMMAND)
    }

    @Test fun anErrorReplyIsAFailure() {
        assertTrue(restartFailed("RESET ERROR no content is running"))
    }

    @Test fun aReplyOrNoReplyIsNot() {
        assertFalse(restartFailed("RESET"))
        assertFalse(restartFailed(null))
    }
}
