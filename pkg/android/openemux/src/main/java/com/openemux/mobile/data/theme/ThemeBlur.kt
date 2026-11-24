package com.openemux.mobile.data.theme

/**
 * A blur for the theme's background pictures (issue 1018) that works the same on every Android version
 * (`Modifier.blur` needs API 31): three box passes each way, close to a gaussian of the same radius. Runs once per
 * change of picture or radius, off the main thread, on a downsized copy.
 */
object ThemeBlur {
    /** The longest side the blur works at: a blurred picture loses nothing by being small. */
    const val WORK_SIDE = 512

    /** [pixels] (ARGB, [width] × [height]) blurred by [radius] pixels; a copy, the input untouched. */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        require(pixels.size == width * height) { "size mismatch" }
        if (radius <= 0 || width == 0 || height == 0) return pixels.copyOf()
        var a = pixels.copyOf()
        var b = IntArray(a.size)
        repeat(3) {
            pass(a, b, width, height, radius, horizontal = true)
            pass(b, a, width, height, radius, horizontal = false)
        }
        return a
    }

    /** One box pass along rows ([horizontal]) or columns, edges clamped. */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = at(i)
                sa += p ushr 24; sr += p shr 16 and 0xFF; sg += p shr 8 and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                val out = ((sa / window) shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val gone = at(i - r)
                val come = at(i + r + 1)
                sa += (come ushr 24) - (gone ushr 24)
                sr += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
                sg += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
                sb += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }

    /** The blur radius at the working size for a radius of [blurDp] on a picture [side] px long shown at [shownPx]. */
    fun workRadius(blurDp: Float, density: Float, workSide: Int, shownSide: Float): Int =
        if (blurDp <= 0f || shownSide <= 0f) 0 else maxOf(1, (blurDp * density * workSide / shownSide).toInt())
}
