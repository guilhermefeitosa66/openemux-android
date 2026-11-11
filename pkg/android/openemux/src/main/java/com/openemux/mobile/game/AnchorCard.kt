package com.openemux.mobile.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemux.mobile.ui.Icon
import com.openemux.mobile.ui.Icons
import com.openemux.mobile.ui.Oe
import com.openemux.mobile.ui.OpenEmuxTheme
import com.openemux.mobile.ui.Type
import com.retroarch.R
import kotlinx.coroutines.launch

/**
 * What anchor mode does, shown at the bottom of the screen each time the player turns it on (issue 841). It slides up
 * from the bottom edge like a system sheet and stays until swiped down: it follows the finger and slides out
 * ([AnchorCardSwipe]); [onClosed] is called once it is gone.
 */
@Composable
fun AnchorCard(onClosed: () -> Unit) {
    OpenEmuxTheme {
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(0f) }
        var height by remember { mutableIntStateOf(0) }
        // Hidden until measured, then it rises from below its window's bottom edge (a margin above the screen's), which
        // clips it on the way in, so it fades in as it rises.
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(height) {
            if (!entered && height > 0) {
                offset.snapTo(height.toFloat())
                entered = true
                offset.animateTo(0f, tween(OPEN_MS))
            }
        }
        fun close() {
            scope.launch {
                offset.animateTo(height.toFloat().coerceAtLeast(offset.value), tween(CLOSE_MS))
                onClosed()
            }
        }
        // Where the finger has the card, kept as each delta comes: the snaps run later, and letting go is judged here.
        var dragged by remember { mutableFloatStateOf(0f) }
        val drag = rememberDraggableState { delta ->
            dragged = AnchorCardSwipe.drag(dragged, delta)
            val to = dragged
            scope.launch { offset.snapTo(to) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .graphicsLayer {
                    translationY = offset.value
                    alpha = if (entered) AnchorCardSwipe.alpha(offset.value, height.toFloat()) else 0f
                }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragged = offset.value },
                    onDragStopped = { velocity ->
                        if (AnchorCardSwipe.closes(dragged, velocity, height.toFloat())) close()
                        else { dragged = 0f; offset.animateTo(0f) }
                    },
                )
                .semantics { dismiss { close(); true } }
                .background(Oe.Sheet, RoundedCornerShape(20.dp))
                .border(1.dp, Oe.Border, RoundedCornerShape(20.dp))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The drag handle: what says "swipe down to close".
            Box(Modifier.size(40.dp, 4.dp).background(Oe.Chevron, RoundedCornerShape(2.dp)))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(36.dp).background(Color(AnchorLayer.RING_COLOR).copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Anchor, Color(AnchorLayer.RING_COLOR), size = 22.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.game_anchor_card_title), style = Type.Section)
                    Text(stringResource(R.string.game_anchor_card_body), style = Type.Small)
                }
            }
            Text(
                stringResource(R.string.game_anchor_card_hint),
                style = Type.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val OPEN_MS = 220
private const val CLOSE_MS = 180
