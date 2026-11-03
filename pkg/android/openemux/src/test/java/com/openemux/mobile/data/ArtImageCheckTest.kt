package com.openemux.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtImageCheckTest {
    private fun padded(vararg head: Int) = ByteArray(ArtImageCheck.MIN_BYTES).also { b -> head.forEachIndexed { i, v -> b[i] = v.toByte() } }
    private fun padded(head: String) = ByteArray(ArtImageCheck.MIN_BYTES).also { b -> head.toByteArray().copyInto(b) }

    @Test
    fun `each image format is told by its first bytes`() {
        assertEquals("png", ArtImageCheck.format(padded(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("jpg", ArtImageCheck.format(padded(0xFF, 0xD8, 0xFF)))
        assertEquals("gif", ArtImageCheck.format(padded("GIF87a")))
        assertEquals("gif", ArtImageCheck.format(padded("GIF89a")))
        assertEquals("webp", ArtImageCheck.format(padded("RIFF\u0000\u0000\u0000\u0000WEBP")))
    }

    @Test
    fun `an error page, a RIFF that is no WebP or a tiny file is no image`() {
        assertNull(ArtImageCheck.format(padded("<!DOCTYPE html>")))
        assertNull(ArtImageCheck.format(padded("RIFF\u0000\u0000\u0000\u0000WAVE")))
        assertNull(ArtImageCheck.format(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun `the digest tells different bytes apart`() {
        assertEquals(ArtImageCheck.sha1(padded("a")), ArtImageCheck.sha1(padded("a")))
        assertNotEquals(ArtImageCheck.sha1(padded("a")), ArtImageCheck.sha1(padded("b")))
        assertEquals(40, ArtImageCheck.sha1(byteArrayOf()).length)
    }
}
