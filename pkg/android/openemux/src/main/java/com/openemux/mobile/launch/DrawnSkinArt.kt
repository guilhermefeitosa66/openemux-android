package com.openemux.mobile.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.retroarch.R
import java.io.File

/**
 * Paints the images of a drawn skin's copy ([DrawnSkinCopy.Art], issue 798) the way [SkinRenderer] draws those parts in
 * game: the body with the console's name, the D-pad's cross with its arrow marks and its ring, the round menu key.
 */
class DrawnSkinArt(context: Context) {
    private val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD

    /** [art] as a PNG in [file]. */
    fun paint(art: DrawnSkinCopy.Art, file: File) {
        val bitmap = Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bitmap)
            when (art) {
                is DrawnSkinCopy.Art.Background -> background(c, art)
                is DrawnSkinCopy.Art.Dpad -> dpad(c, art)
                is DrawnSkinCopy.Art.Ball -> ball(c, art)
                is DrawnSkinCopy.Art.Menu -> menu(c, art)
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    /** SkinRenderer's body fill and its name label (Layout.label): centred, 70% opaque, shrunk to fit its width. */
    private fun background(c: Canvas, art: DrawnSkinCopy.Art.Background) {
        c.drawColor(art.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (art.ink and 0x00FFFFFF) or (0xB3 shl 24); typeface = inter; textSize = art.font
            textAlign = Paint.Align.CENTER; letterSpacing = art.spacing
        }
        val room = art.nameBox.width * 0.9f
        val measured = paint.measureText(art.name)
        if (measured > room) paint.textSize *= room / measured
        c.drawText(art.name, art.nameBox.x + art.nameBox.width / 2f, art.nameBox.y - paint.ascent(), paint)
    }

    /** SkinRenderer.Layout.dpad's cross and marks, on its 120-unit grid. */
    private fun dpad(c: Canvas, art: DrawnSkinCopy.Art.Dpad) {
        val side = minOf(art.width, art.height).toFloat()
        val u = side / 120f
        val left = (art.width - side) / 2f
        val top = (art.height - side) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = art.pad }
        c.drawRoundRect(RectF(left + 40 * u, top + 2 * u, left + 80 * u, top + 118 * u), 9 * u, 9 * u, paint)
        c.drawRoundRect(RectF(left + 2 * u, top + 40 * u, left + 118 * u, top + 80 * u), 9 * u, 9 * u, paint)
        paint.color = art.mark
        for (tri in listOf(
            listOf(60f to 14f, 51f to 26f, 69f to 26f), listOf(60f to 106f, 51f to 94f, 69f to 94f),
            listOf(14f to 60f, 26f to 51f, 26f to 69f), listOf(106f to 60f, 94f to 51f, 94f to 69f),
        )) {
            val path = Path()
            tri.forEachIndexed { i, (px, py) -> if (i == 0) path.moveTo(left + px * u, top + py * u) else path.lineTo(left + px * u, top + py * u) }
            path.close()
            c.drawPath(path, paint)
        }
    }

    /** SkinRenderer.Layout.dpad's ring: a 70% disc of radius 14 and a 2-wide edge at 13, at its share of the image. */
    private fun ball(c: Canvas, art: DrawnSkinCopy.Art.Ball) {
        val r = minOf(art.width, art.height) * DrawnSkinCopy.RING / 2f
        val u = r / 14f
        val cx = art.width / 2f
        val cy = art.height / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (art.mark and 0x00FFFFFF) or (0xB3 shl 24) }
        c.drawCircle(cx, cy, 14 * u, ring)
        ring.apply { style = Paint.Style.STROKE; strokeWidth = 2 * u; color = art.mark }
        c.drawCircle(cx, cy, 13 * u, ring)
    }

    /** SkinRenderer.Layout.menu: the round key and its three lines, 20 of the key's 44 units across. */
    private fun menu(c: Canvas, art: DrawnSkinCopy.Art.Menu) {
        val box = RectF(0f, 0f, art.width.toFloat(), art.height.toFloat())
        val r = minOf(box.width(), box.height()) / 2f
        c.drawRoundRect(box, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = art.fill })
        val u = box.height() * 20f / 44f / 24f
        val left = box.centerX() - 12f * u
        val top = box.centerY() - 12f * u
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * u; color = art.ink; strokeCap = Paint.Cap.ROUND }
        for (ly in floatArrayOf(7f, 12f, 17f)) c.drawLine(left + 5 * u, top + ly * u, left + 19 * u, top + ly * u, stroke)
    }
}
