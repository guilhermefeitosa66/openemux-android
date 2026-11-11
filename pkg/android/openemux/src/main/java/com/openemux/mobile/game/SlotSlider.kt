package com.openemux.mobile.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemux.mobile.ui.DISABLED_ALPHA
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.Oe
import androidx.compose.material3.Text
import com.openemux.mobile.ui.Type
import com.openemux.mobile.ui.gamepadFocus
import com.retroarch.R
import kotlin.math.roundToInt

/** Where the pause sheet's slot slider puts its stops (issue 867): one per slot 0 to 9, and the current one past them. */
object SlotStops {
    /** The slots that always have a stop. Slots past them (up to SaveSlots.RANGE) are picked from "See all". */
    val STOPS = 0..9

    /** The slots shown, left to right: [STOPS], plus [current] at the end while it is past them. */
    fun cells(current: Int): List<Int> = if (current > STOPS.last) STOPS.toList() + current else STOPS.toList()

    /** The cell under [x] on a slider [width] wide split in [count] equal cells; a pointer past an end takes that end. */
    fun at(x: Float, width: Float, count: Int): Int =
        if (width <= 0f || count <= 0) 0 else (x / width * count).toInt().coerceIn(0, count - 1)

    /** The centre of cell [index] of [count] on a slider [width] wide. */
    fun center(index: Int, width: Float, count: Int): Float = (index + 0.5f) * width / count

    /** A pad press of [delta] from [current]: one stop, held in [STOPS]; from a slot past them, left comes back to the last stop. */
    fun step(current: Int, delta: Int): Int = when {
        current > STOPS.last -> if (delta < 0) STOPS.last else current
        else -> (current + delta).coerceIn(STOPS.first, STOPS.last)
    }
}

private val StopSize = 24.dp
private val ThumbSize = 34.dp
private val ThumbHalo = 5.dp
private val SliderHeight = 46.dp

/**
 * "See all ›" on the right, then the slot slider: what sits under Save and Load in every form of the sheet (issue 867).
 * [used] are the slots holding a state; [onPick] makes a slot current; [onSeeAll] opens the save-states page.
 */
@Composable
internal fun SlotStrip(slot: Int, used: Set<Int>, modifier: Modifier, onPick: (Int) -> Unit, onSeeAll: () -> Unit, canSeeAll: Boolean) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val link = stringResource(R.string.game_see_all)
            Row(
                Modifier.height(32.dp).let { if (canSeeAll) it.gamepadFocus(corner = 8.dp) else it.alpha(DISABLED_ALPHA) }
                    .clip(RoundedCornerShape(8.dp)).clickable(enabled = canSeeAll, role = Role.Button, onClick = onSeeAll)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(link, style = Type.Small.copy(color = Oe.AccentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Icon(Icons.Chevron, Oe.AccentText, 14.dp, stroke = 2.4f)
            }
        }
        SlotSlider(slot, used, Modifier.padding(top = 2.dp).fillMaxWidth(), onPick)
    }
}

/**
 * One stop per slot, the current one under the thumb with its number (issue 867). A slot holding a state is filled,
 * an empty one outlined and dimmed; the thumb follows the same code. A tap or a drag picks the stop under the finger,
 * left and right on the pad step one stop.
 */
@Composable
internal fun SlotSlider(slot: Int, used: Set<Int>, modifier: Modifier, onPick: (Int) -> Unit) {
    val cells = SlotStops.cells(slot)
    val latestCells by rememberUpdatedState(cells)
    val pick by rememberUpdatedState(onPick)
    // The slot shown, or the one just picked while the sheet has not recomposed yet: a drag's moves come faster than
    // frames, and each pick writes the slot's file and tells the engine, so the same slot is never asked for twice.
    val current = remember { IntArray(1) }.also { it[0] = slot }
    fun choose(target: Int) {
        if (target == current[0]) return
        current[0] = target
        pick(target)
    }
    // Eleven labels are drawn per pass: the default cache (8) would miss every one of them on each redraw.
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val view = LocalView.current
    val label = stringResource(R.string.game_slot_slider)
    val state = stringResource(if (slot in used) R.string.game_slot_used else R.string.game_slot_empty, slot)
    val colors = SliderColors(track = Oe.Raised, accent = Oe.Accent, outline = Oe.AccentText, background = Oe.Sheet)
    fun pickAt(x: Float, width: Int) = choose(latestCells[SlotStops.at(x, width.toFloat(), latestCells.size)])
    Canvas(
        modifier.height(SliderHeight)
            .semantics {
                contentDescription = label
                stateDescription = state
                // By stop, not by slot number: past the ten stops the eleventh is the current slot, and the slots between
                // are not on the slider (the pad's left goes from it straight to the last stop too).
                val last = cells.size - 1
                progressBarRangeInfo = ProgressBarRangeInfo(cells.indexOf(slot).toFloat(), 0f..last.toFloat(), steps = last - 1)
                setProgress { value -> choose(cells[value.roundToInt().coerceIn(0, last)]); true }
            }
            .gamepadFocus(corner = 14.dp)
            .onKeyEvent { e ->
                val delta = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent true
                // At an end the press is not taken, so the focus moves on, as a platform seek bar does: in landscape and on
                // the fold, the tiles and Back to game are to the right of the slider.
                val next = SlotStops.step(current[0], delta)
                if (next == current[0]) return@onKeyEvent false
                choose(next)
                true
            }
            // Out of touch mode only, like the clickables around it: a plain focusable would take the focus a touch-opened
            // sheet leaves free, and the pad's first press would start here instead of on Continue.
            .focusProperties { canFocus = !view.isInTouchMode }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { pickAt(it.x, size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { pickAt(it.x, size.width) }) { change, _ ->
                    change.consume()
                    pickAt(change.position.x, size.width)
                }
            },
    ) {
        val y = size.height / 2f
        val count = cells.size
        val bar = 4.dp.toPx()
        val first = SlotStops.center(0, size.width, count)
        val last = SlotStops.center(count - 1, size.width, count)
        drawRoundRect(colors.track, Offset(first, y - bar / 2f), Size(last - first, bar), CornerRadius(bar / 2f))
        cells.forEachIndexed { index, cell ->
            val thumb = cell == slot
            drawStop(measurer, colors, Offset(SlotStops.center(index, size.width, count), y), cell, cell in used, thumb)
        }
    }
}

private class SliderColors(val track: Color, val accent: Color, val outline: Color, val background: Color)

/** A stop, or the thumb when [thumb]: filled with its number when [filled], else outlined and dimmed. */
private fun DrawScope.drawStop(measurer: TextMeasurer, colors: SliderColors, centre: Offset, slot: Int, filled: Boolean, thumb: Boolean) {
    val radius = (if (thumb) ThumbSize else StopSize).toPx() / 2f
    if (thumb) drawCircle(colors.accent.copy(alpha = 0.3f), radius + ThumbHalo.toPx(), centre)
    // The track stops at the circle: an outlined stop shows the sheet behind it, not the bar.
    drawCircle(colors.background, radius, centre)
    val dim = if (thumb) 0.75f else 0.55f
    if (filled) drawCircle(colors.accent, radius, centre)
    else {
        val ring = (if (thumb) 2.5.dp else 1.5.dp).toPx()
        drawCircle(colors.outline.copy(alpha = dim), radius - ring / 2f, centre, style = Stroke(ring))
    }
    val text = measurer.measure(
        "$slot",
        TextStyle(
            color = if (filled) Color.White else colors.outline.copy(alpha = dim),
            fontSize = if (thumb) 14.sp else 11.sp,
            fontWeight = if (thumb || filled) FontWeight.Bold else FontWeight.Medium,
        ),
    )
    drawText(text, topLeft = Offset(centre.x - text.size.width / 2f, centre.y - text.size.height / 2f))
}
