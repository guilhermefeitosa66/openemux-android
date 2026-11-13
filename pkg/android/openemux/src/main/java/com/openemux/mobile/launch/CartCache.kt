package com.openemux.mobile.launch

import java.io.File
import java.io.IOException

/**
 * The cartridge pictures composed for launches, kept under `launch/carts/<id>.png`
 * (issue 917): the pause sheet of a game launched again decodes the picture
 * composed last time instead of a new one. The newest [keep] stay.
 */
class CartCache(private val root: File, private val keep: Int = KEEP) {
    /** The kept picture for [key], touched so it stays among the newest; null when there is none. */
    fun lookup(key: String): File? = file(key).takeIf { it.isFile }?.also { it.setLastModified(System.currentTimeMillis()) }

    /** Writes the picture for [key] through [write], aside first, and keeps it. */
    fun keep(key: String, write: (File) -> Unit): File {
        root.mkdirs()
        val target = file(key)
        val tmp = File(root, "${target.name}.tmp")
        write(tmp)
        if (!tmp.renameTo(target)) throw IOException("could not keep the cartridge")
        root.listFiles { f -> f.isFile && f.name.endsWith(".png") }.orEmpty().sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
        return target
    }

    private fun file(key: String) = File(root, SkinCache.sha256Hex(key).take(24) + ".png")

    companion object {
        const val KEEP = 24
    }
}
