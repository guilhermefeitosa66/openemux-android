package com.openemux.mobile.data

import org.json.JSONObject
import java.io.File

/**
 * Anchor mode turned on or off in game, on its way to the library (issue 841), the way [SkinPicks] carries skins: the
 * game runs in the `:game` process and the library's metadata lives in the main one, which saves it whole. One small
 * file per change, written aside and renamed into place, so no lock is needed between the two processes.
 */
object AnchorPicks {
    data class Pick(val console: String, val on: Boolean, val at: Long)

    private const val EXT = "pick"
    private const val STALE_TMP_MS = 60_000L

    /** Saves anchor mode [on] for [console]; false when it could not be written (the game goes on regardless). */
    fun record(dir: File, console: String, on: Boolean, at: Long = System.currentTimeMillis()): Boolean = runCatching {
        dir.mkdirs()
        val name = "$at-${System.nanoTime()}"
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(JSONObject().put("console", console).put("on", on).put("at", at).toString())
        if (!tmp.renameTo(File(dir, "$name.$EXT"))) {
            tmp.delete()
            error("rename failed")
        }
    }.isSuccess

    /**
     * Takes every complete pick out of [dir], oldest first, so applying them in order leaves the newest one per
     * console. A file that does not read as a pick is dropped too, and so is a .tmp abandoned by a killed write; a write
     * still in flight is left alone.
     */
    fun drain(dir: File, now: Long = System.currentTimeMillis()): List<Pick> {
        dir.listFiles { f -> f.isFile && f.extension == "tmp" && now - f.lastModified() > STALE_TMP_MS }.orEmpty().forEach { it.delete() }
        val files = dir.listFiles { f -> f.isFile && f.extension == EXT }.orEmpty()
        return files.mapNotNull { f ->
            val pick = runCatching {
                val json = JSONObject(f.readText())
                Pick(json.getString("console"), json.getBoolean("on"), json.getLong("at"))
            }.getOrNull()
            f.delete()
            pick?.let { it to f.name }
        }.sortedWith(compareBy({ it.first.at }, { it.second })).map { it.first }
    }
}
