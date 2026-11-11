package com.openemux.mobile.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * What anchor mode holds, drawn over the game (issue 841), as the study's "Anchor button" animation shows it: an amber
 * dashed ring a little outside each anchored button and, while its finger is off it, a dashed white line from the
 * button's centre to the finger. Lives in a window of its own that takes no touch: the game's own window has its
 * surface taken by the engine, so nothing can be drawn into it.
 *
 * [screen] is the game window's view: the marks are fractions of it, placed wherever this layer's window sits.
 */
@SuppressLint("ViewConstructor")
class AnchorLayer(context: Context, private val screen: View) : View(context) {
    /** The marks shown; setting them redraws. */
    var marks: List<AnchorMark> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val gap = RING_GAP_DP * dp
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = RING_COLOR
        strokeWidth = RING_STROKE_DP * dp
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(6f * dp, 4f * dp), 0f)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = LINE_COLOR
        strokeWidth = LINE_STROKE_DP * dp
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(3f * dp, 5f * dp), 0f)
    }
    private val box = RectF()
    private val here = IntArray(2)
    private val there = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        if (marks.isEmpty() || screen.width == 0 || screen.height == 0) return
        // The game's window and this one may not start at the same place (a cutout, a bar): draw in the game's.
        getLocationOnScreen(here)
        screen.getLocationOnScreen(there)
        val dx = (there[0] - here[0]).toFloat()
        val dy = (there[1] - here[1]).toFloat()
        val w = screen.width.toFloat()
        val h = screen.height.toFloat()
        for (m in marks) {
            box.set(dx + m.left * w, dy + m.top * h, dx + (m.left + m.width) * w, dy + (m.top + m.height) * h)
            if (m.fingerOff()) canvas.drawLine(box.centerX(), box.centerY(), dx + m.fingerX * w, dy + m.fingerY * h, line)
            box.inset(-gap, -gap)
            if (m.rect) {
                val r = minOf(box.width(), box.height()) / 4f
                canvas.drawRoundRect(box, r, r, ring)
            } else {
                canvas.drawOval(box, ring)
            }
        }
    }

    companion object {
        /** The study's amber, #F2A23A. */
        const val RING_COLOR = 0xFFF2A23A.toInt()
        /** White at about 70%. */
        const val LINE_COLOR = 0xB3FFFFFF.toInt()
        const val RING_GAP_DP = 4f
        const val RING_STROKE_DP = 2.5f
        const val LINE_STROKE_DP = 2f
    }
}
