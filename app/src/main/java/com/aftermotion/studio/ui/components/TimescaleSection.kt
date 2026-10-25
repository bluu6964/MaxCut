package com.aftermotion.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.model.TIMELINE_PX_PER_MS
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.ui.theme.IconActive
import com.aftermotion.studio.ui.theme.PlayheadColor
import com.aftermotion.studio.ui.theme.RulerSubTickColor
import com.aftermotion.studio.ui.theme.RulerTickColor
import com.aftermotion.studio.ui.util.bouncyClickable
import java.util.Locale

/**
 * CapCut-style timescale ruler.
 *
 * - Playhead locked at exact horizontal center
 * - Fine tick marks span the full width (left + right of center)
 * - Current time shown in a compact rounded dark capsule centered on the playhead
 */
@Composable
fun TimescaleSection(
    height: Dp,
    playheadMs: () -> Long,
    totalDurationMs: Long,
    pxPerMs: Float = TIMELINE_PX_PER_MS,
    modifier: Modifier = Modifier,
    /** When set, a back chevron sits at the left end of the ruler. */
    onBack: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .testTag("timescale_section")
    ) {

        // Optional panel-back chevron (thumb-reach)
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(48.dp)
                    .fillMaxHeight()
                    .bouncyClickable(onClick = onBack)
                    .testTag("panel_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back",
                    tint = IconActive,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ── Full-width ruler ticks (centered on playhead) ──────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentMs = playheadMs()
            val h = size.height
            val centerPx = size.width / 2f
            val pxPerMsPx = pxPerMs.dp.toPx()

            // Visible time window around the locked playhead
            val halfWidthMs = (centerPx / pxPerMsPx).toLong()
            val minMs = (currentMs - halfWidthMs).coerceAtLeast(0L)
            val maxMs = currentMs + halfWidthMs + 200L

            // Minor every 200 ms, major every 1000 ms
            val startTick = (minMs / 200L) * 200L
            val endTick = ((maxMs / 200L) + 1) * 200L

            for (t in startTick..endTick step 200L) {
                val localX = centerPx + (t - currentMs) * pxPerMsPx
                if (localX < -2f || localX > size.width + 2f) continue

                val isMajor = (t % 1000L == 0L)
                val tickH = if (isMajor) h * 0.55f else h * 0.28f
                // Align ticks toward the top so the time pill has room below
                val y0 = h * 0.12f
                val y1 = y0 + tickH

                drawLine(
                    color = if (isMajor) RulerTickColor else RulerSubTickColor,
                    start = Offset(localX, y0),
                    end = Offset(localX, y1),
                    strokeWidth = if (isMajor) 1.6.dp.toPx() else 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center playhead line (thin, spans the tick area)
            val playheadTop = h * 0.06f
            val playheadBottom = h * 0.72f
            drawLine(
                color = PlayheadColor,
                start = Offset(centerPx, playheadTop),
                end = Offset(centerPx, playheadBottom),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // ── Time capsule centered on the playhead ──────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .background(
                    color = Color(0xFF2A2A2E),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("playhead_time_pill"),
            contentAlignment = Alignment.Center
        ) {
            PlayheadTimeText(playheadMs)
        }
    }
}

/** Only this tiny text recomposes while the playhead moves. */
@Composable
private fun PlayheadTimeText(playheadMs: () -> Long) {
    Text(
        text = formatTimeCode(playheadMs()),
        color = Color.White,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        letterSpacing = 0.3.sp
    )
}

fun formatTimeCode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    // CapCut-style: 00:01:06  (MM:SS:centiseconds)
    return String.format(Locale.US, "%02d:%02d:%02d", minutes, seconds, hundredths)
}
