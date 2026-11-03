package com.openemux.mobile.data

import java.security.MessageDigest

/**
 * What a download really is, from its first bytes (desktop `scraper.py`): an error page or a
 * placeholder sent with a 200 must never become art.
 */
object ArtImageCheck {
    /** Anything smaller is no image worth showing. */
    const val MIN_BYTES = 64

    /** The image format [bytes] hold ("png", "jpg", "gif", "webp"), or null when they are no image. */
    fun format(bytes: ByteArray): String? {
        if (bytes.size < MIN_BYTES) return null
        fun starts(vararg b: Int) = b.indices.all { bytes[it] == b[it].toByte() }
        fun ascii(at: Int, text: String) = text.indices.all { bytes[at + it] == text[it].code.toByte() }
        return when {
            starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "png"
            starts(0xFF, 0xD8, 0xFF) -> "jpg"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
            else -> null
        }
    }

    /** The formats a file picked for import may be (the issue's PNG, JPG, JPEG, WebP). */
    val IMPORTABLE = setOf("png", "jpg", "webp")

    fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
