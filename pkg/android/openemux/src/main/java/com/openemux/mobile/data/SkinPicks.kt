package com.openemux.mobile.data

import org.json.JSONObject
import java.io.File

/**
 * Skins picked in game, on their way to the library (issue 827), the way [ShaderPicks] carries shaders: the pause sheet
 * runs in the `:game` process and the library's metadata lives in the main one, which saves it whole. One small file
 * per pick, written aside and renamed into place, so no lock is needed between the two processes.
 *
 * A pick is either a bundled skin ([Pick.skin], a [SkinVariant] id) or one of the player's ([Pick.custom], a
 * [SkinStore] ref), never both.
 */
object SkinPicks {
    data class Pick(val console: String, val skin: String?, val custom: String?, val at: Long)

    private const val EXT = "pick"
    private const val STALE_TMP_MS = 60_000L

    /** Saves the pick for [console]; false when it could not be written or is not a pick (the game goes on regardless). */
    fun record(dir: File, console: String, skin: String?, custom: String?, at: Long = System.currentTimeMillis()): Boolean = runCatching {
        require((skin == null) != (custom == null)) { "a pick is one skin" }
        dir.mkdirs()
        val name = "$at-${System.nanoTime()}"
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(JSONObject().put("console", console).put("skin", skin).put("custom", custom).put("at", at).toString())
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
                val skin = json.optString("skin").ifEmpty { null }
                val custom = json.optString("custom").ifEmpty { null }
                require((skin == null) != (custom == null))
                Pick(json.getString("console"), skin, custom, json.getLong("at"))
            }.getOrNull()
            f.delete()
            pick?.let { it to f.name }
        }.sortedWith(compareBy({ it.first.at }, { it.second })).map { it.first }
    }
}
