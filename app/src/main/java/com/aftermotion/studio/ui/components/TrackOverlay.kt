package com.aftermotion.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The circle that selects the object to track, as fractions of the canvas frame:
 * [cx] of its width, [cy] of its height, [r] (radius) of its width.
 */
data class TrackCircle(val cx: Float = 0.5f, val cy: Float = 0.5f, val r: Float = 0.12f) {
    fun resized(factor: Float) = copy(r = (r * factor).coerceIn(MIN_R, MAX_R))

    companion object {
        const val MIN_R = 0.03f
        const val MAX_R = 0.45f
    }
}

/**
 * Drawn on top of the canvas frame while the user chooses what to track: tap to put the circle
 * somewhere, drag to move it, pinch (or the - / + buttons) to make it smaller / bigger.
 */
@Composable
fun BoxScope.TrackSetupOverlay(
    circle: TrackCircle,
    onCircle: (TrackCircle) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val circleNow by rememberUpdatedState(circle)
    val onCircleNow by rememberUpdatedState(onCircle)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        onCircleNow(circleNow.copy(cx = (pos.x / w).coerceIn(0f, 1f), cy = (pos.y / h).coerceIn(0f, 1f)))
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w > 0f && h > 0f) {
                        val c = circleNow
                        onCircleNow(
                            c.copy(
                                cx = (c.cx + pan.x / w).coerceIn(0f, 1f),
                                cy = (c.cy + pan.y / h).coerceIn(0f, 1f),
                                r = (c.r * zoom).coerceIn(TrackCircle.MIN_R, TrackCircle.MAX_R)
                            )
                        )
                    }
                }
            }
            .drawBehind {
                val centre = Offset(circle.cx * size.width, circle.cy * size.height)
                drawTrackCircle(centre, circle.r * size.width)
            }
            .testTag("track_setup_overlay")
    )

    // cancel (top left)
    RoundButton(
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).testTag("track_cancel"),
        onClick = onCancel
    ) { Icon(Icons.Default.Close, contentDescription = "Cancel tracking", tint = Color.White, modifier = Modifier.size(18.dp)) }

    // start (top right)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF4C8DFF))
            .clickable(onClick = onStart)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("track_start"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text("Track", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp))
    }

    // smaller / bigger (bottom left)
    Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(
            modifier = Modifier.testTag("track_smaller"),
            onClick = { onCircleNow(circleNow.resized(0.85f)) }
        ) { Icon(Icons.Default.Remove, contentDescription = "Smaller circle", tint = Color.White, modifier = Modifier.size(18.dp)) }
        Box(Modifier.size(6.dp))
        RoundButton(
            modifier = Modifier.testTag("track_bigger"),
            onClick = { onCircleNow(circleNow.resized(1.18f)) }
        ) { Icon(Icons.Default.Add, contentDescription = "Bigger circle", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

/** Dashed white circle with a centre dot (setup and live tracking look the same). */
private fun DrawScope.drawTrackCircle(centre: Offset, radius: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius, center = centre, style = Stroke(4.dp.toPx()))
    drawCircle(
        Color.White, radius = radius, center = centre,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())))
    )
    drawCircle(Color.White, radius = 3.dp.toPx(), center = centre)
}

/**
 * What the tracker sees right now, as fractions of the canvas frame: circle centre ([cx] of the
 * width, [cy] of the height), radius [r] (of the width) and the followed corners [pts] as x, y, x, y ...
 */
class TrackLive(val cx: Float, val cy: Float, val r: Float, val pts: FloatArray)

/** Draws the moving circle and the followed corners (green) while a track runs. No touch handling. */
@Composable
fun BoxScope.TrackRunOverlay(live: TrackLive?) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                val l = live ?: return@drawBehind
                drawTrackCircle(Offset(l.cx * size.width, l.cy * size.height), l.r * size.width)
                var i = 0
                while (i + 1 < l.pts.size) {
                    drawCircle(
                        Color(0xFF39FF14), radius = 2.dp.toPx(),
                        center = Offset(l.pts[i] * size.width, l.pts[i + 1] * size.height)
                    )
                    i += 2
                }
            }
            .testTag("track_run_overlay")
    )
}

@Composable
private fun RoundButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xCC1B2230))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A layer that can be attached to the tracked object. */
data class TrackTarget(val id: String, val label: String)

/** Asks which layer should follow the tracked object. */
@Composable
fun TrackLayerPickDialog(
    targets: List<TrackTarget>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which layer should follow the object?") },
        text = {
            Column {
                if (targets.isEmpty()) {
                    Text(
                        "There is no other layer yet. Add a text, shape or sticker layer first, then press Track again.",
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        "Pick the layer that will move with the object. It follows the object frame by frame (no keyframes); an earlier track on that layer is replaced.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        targets.forEach { t ->
                            TextButton(
                                onClick = { onPick(t.id) },
                                modifier = Modifier.fillMaxWidth().testTag("track_target_" + t.id)
                            ) { Text(t.label, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
