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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.aftermotion.studio.ui.util.bouncyClickable
import java.util.Locale

private val OrangeBright = Color(0xFFFF8A1F)
private val OrangeDark = Color(0xFF8A3A0A)
private val GreyNear = Color(0xFF8E8E8E)
private val GreyFar = Color(0xFF3C3C3C)

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

        // ── Full-width ruler: pill bars. Before the playhead = glowing orange, after = dim grey.
        //    Both sides fade out toward the screen edges. ───────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentMs = playheadMs()
            val h = size.height
            val centerPx = size.width / 2f
            val pxPerMsPx = pxPerMs.dp.toPx()

            val halfWidthMs = (centerPx / pxPerMsPx).toLong()
            val minMs = (currentMs - halfWidthMs).coerceAtLeast(0L)
            val maxMs = currentMs + halfWidthMs + 200L

            val startTick = (minMs / 200L) * 200L
            val endTick = ((maxMs / 200L) + 1) * 200L

            val barW = 3.6.dp.toPx()
            val barH = h * 0.52f
            val y0 = h * 0.10f
            val corner = CornerRadius(barW / 2f, barW / 2f)

            for (t in startTick..endTick step 200L) {
                val x = centerPx + (t - currentMs) * pxPerMsPx
                if (x < -barW || x > size.width + barW) continue

                // 0 at the playhead .. 1 at the screen edge
                val f = (kotlin.math.abs(x - centerPx) / centerPx).coerceIn(0f, 1f)
                val fade = 1f - 0.7f * f * f
                val played = x <= centerPx + 0.5f

                val color = if (played) {
                    lerp(OrangeBright, OrangeDark, f).copy(alpha = fade)
                } else {
                    lerp(GreyNear, GreyFar, f).copy(alpha = fade)
                }

                // soft glow behind the played (orange) bars
                if (played) {
                    drawRoundRect(
                        color = color.copy(alpha = fade * 0.18f),
                        topLeft = Offset(x - barW, y0 - barW * 0.5f),
                        size = Size(barW * 2f, barH + barW),
                        cornerRadius = CornerRadius(barW, barW)
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x - barW / 2f, y0),
                    size = Size(barW, barH),
                    cornerRadius = corner
                )
            }
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
