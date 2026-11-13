package com.openemux.mobile.game

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object GameExtras {
    const val TITLE = "OE_TITLE"
    const val CONSOLE = "OE_CONSOLE"
    const val CART = "OE_CART"
    const val STATES_DIR = "OE_STATES_DIR"
    const val ROM_BASE = "OE_ROM_BASE"
    const val PLAYTIME = "OE_PLAYTIME"
    const val SHADERS_DIR = "OE_SHADERS_DIR"
    const val SHADER = "OE_SHADER"
    const val CORE_NAME = "OE_CORE_NAME"
    const val LOAD_SLOT = "OE_LOAD_SLOT"
    const val HARDCORE = "OE_HARDCORE"
    const val LAUNCH_DIR = "OE_LAUNCH_DIR"
    const val SKIN_SIZE = "OE_SKIN_SIZE"
    const val SKINS_BASE = "OE_SKINS_BASE"
    const val CORE_ID = "OE_CORE_ID"
    /** The library key of the ROM running, which a shader picked in game is saved for. */
    const val ROM_KEY = "OE_ROM_KEY"
    /** The folder a shader picked in game is dropped into for the library (ShaderPicks). */
    const val SHADER_PICKS = "OE_SHADER_PICKS"
    /** The console's SkinVariant id, so a redraw in game uses the skin the launch did. */
    const val SKIN_VARIANT = "OE_SKIN_VARIANT"
    /** The player's skin the console plays with (SkinStore.ref), absent for the bundled one (issue 782). */
    const val SKIN_CUSTOM = "OE_SKIN_CUSTOM"
    /** The folder a skin picked in game is dropped into for the library (SkinPicks). */
    const val SKIN_PICKS = "OE_SKIN_PICKS"
    /** Whether the game starts with anchor mode on (issue 841), as the console's settings have it. */
    const val ANCHOR = "OE_ANCHOR"
    /** The folder anchor mode turned on or off in game is dropped into for the library (AnchorPicks). */
    const val ANCHOR_PICKS = "OE_ANCHOR_PICKS"
    /** The controller combination that pauses (PauseComboPref), for the one-time hint in game (issue 924). */
    const val PAUSE_COMBO = "OE_PAUSE_COMBO"
}

/**
 * Talks to the running engine through RetroArch's network command
 * interface (network_cmd_enable), which the engine polls on its own
 * thread every frame, paused or not. Call from a background thread.
 */
object RaCommand {
    private const val PORT = 55355
    // The engine binds IPv4 only; getLoopbackAddress() may be ::1.
    private val host: InetAddress by lazy { InetAddress.getByName("127.0.0.1") }

    fun send(command: String) {
        runCatching {
            DatagramSocket().use { socket ->
                val data = command.toByteArray()
                socket.send(DatagramPacket(data, data.size, host, PORT))
            }
        }
    }

    fun ask(command: String, timeoutMs: Int = 400): String? = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val data = command.toByteArray()
            socket.send(DatagramPacket(data, data.size, host, PORT))
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                String(packet.data, 0, packet.length).trim()
            } catch (_: SocketTimeoutException) {
                null
            }
        }
    }.getOrNull()

    // The reply is "GET_STATUS <PAUSED|PLAYING> <system>,<file>": match the state word, not a file name that holds it.
    fun isPaused(): Boolean = ask("GET_STATUS")?.startsWith("GET_STATUS PAUSED") == true

    /** True only on a reply saying the content runs: no reply (a timeout) is not taken as running. */
    fun isPlaying(): Boolean = ask("GET_STATUS")?.startsWith("GET_STATUS PLAYING") == true

    /** Content is up, playing or paused; no reply is not taken as running. */
    fun isRunning(timeoutMs: Int = 400): Boolean = ask("GET_STATUS", timeoutMs)
        ?.let { it.startsWith("GET_STATUS PLAYING") || it.startsWith("GET_STATUS PAUSED") } == true

    /** True from a restart's command until the engine has paused again after it. */
    @Volatile private var restarting = false

    fun resume() {
        // A sheet closed during a restart lands here too: the frames the reset plays read as playing.
        while (restarting) Thread.sleep(30)
        if (isPaused()) send("PAUSE_TOGGLE")
    }

    /**
     * Restarts the content and answers the engine's reply. Reset while paused, the engine plays 8 frames to blank the
     * picture and pauses again: asked in between, [resume] finds it playing, sends nothing, and the game then stays
     * paused on black. So this returns, and [resume] goes on, only once it has paused.
     */
    fun restart(): String? {
        restarting = true
        try {
            return ask(RESTART_COMMAND, 3000).also { if (!replyFailed(it)) awaitPaused(3000) }
        } finally {
            restarting = false
        }
    }

    /** Waits for the content to be paused, for [timeoutMs] at most. */
    private fun awaitPaused(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isPaused()) return
            Thread.sleep(30)
        }
    }
}
