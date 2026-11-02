package com.openemux.mobile.data

import org.json.JSONObject
import java.io.File

/**
 * Shaders picked in game, on their way to the library (issue 717). The pause sheet runs in the `:game` process while
 * the library's metadata lives in the main one, which keeps it in memory and saves it whole: a write from the game
 * would be lost on the main process's next save. So the game drops one small file per pick into a folder and the
 * main process folds them into the ROM's shader override ([Library.absorbShaderPicks]).
 *
 * Every pick gets a file name of its own, written aside and renamed into place, so the reader never sees half a file
 * and the writer never rewrites one being read: no lock between the two processes.
 */
object ShaderPicks {
    data class Pick(val romKey: String, val shader: String, val at: Long)

    private const val EXT = "pick"

    /** Saves [shader] as the pick for [romKey]; false when it could not be written (the game goes on regardless). */
    fun record(dir: File, romKey: String, shader: String, at: Long = System.currentTimeMillis()): Boolean = runCatching {
        dir.mkdirs()
        val name = "$at-${System.nanoTime()}"
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(JSONObject().put("rom", romKey).put("shader", shader).put("at", at).toString())
        if (!tmp.renameTo(File(dir, "$name.$EXT"))) {
            tmp.delete()
            error("rename failed")
        }
    }.isSuccess

    /**
     * Takes every complete pick out of [dir], oldest first, so applying them in order leaves the newest one per ROM.
     * A file that does not read as a pick is dropped too, so nothing piles up; a write still in flight is left alone.
     */
    fun drain(dir: File): List<Pick> {
        val files = dir.listFiles { f -> f.isFile && f.extension == EXT }.orEmpty()
        return files.mapNotNull { f ->
            val pick = runCatching {
                val json = JSONObject(f.readText())
                Pick(json.getString("rom"), json.getString("shader"), json.getLong("at"))
            }.getOrNull()
            f.delete()
            pick?.let { it to f.name }
        }.sortedWith(compareBy({ it.first.at }, { it.second })).map { it.first }
    }
}
