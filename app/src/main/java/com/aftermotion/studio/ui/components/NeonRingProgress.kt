package com.aftermotion.studio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Neon segmented ring loader (export progress). Lit segments glow cyan -> blue, the head of the
 * ring has a bright halo, unlit segments stay dim. Big percentage in the middle.
 */
@Composable
fun NeonRingProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 180f),
        label = "ringProgress"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val segments = 60
            val c = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.46f
            val inner = size.minDimension * 0.37f
            val lit = (segments * p)
            val litA = Color(0xFF00E5FF)
            val litB = Color(0xFF3D7BFF)

            // soft ambient glow behind the ring
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(litB.copy(alpha = 0.22f), Color.Transparent),
                    center = c,
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = c
            )

            for (i in 0 until segments) {
                val a = Math.toRadians((-90.0 + i * 360.0 / segments))
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val start = Offset(c.x + dx * inner, c.y + dy * inner)
                val end = Offset(c.x + dx * outer, c.y + dy * outer)
                val isLit = i + 1 <= lit || (i < lit)
                if (isLit) {
                    val t = i / segments.toFloat()
                    val col = androidx.compose.ui.graphics.lerp(litA, litB, t)
                    val nearHead = (lit - i).coerceAtMost(6f) / 6f  // brighter + longer near the head
                    drawLine(col.copy(alpha = 0.22f), start, end, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(
                        col.copy(alpha = 0.75f + 0.25f * (1f - nearHead)),
                        start, end, strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round
                    )
                } else {
                    drawLine(Color.White.copy(alpha = 0.13f), start, end, strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            // bright halo on the head
            if (p in 0.001f..0.999f) {
                val a = Math.toRadians(-90.0 + 360.0 * p)
                val hr = (inner + outer) / 2f
                val hc = Offset(c.x + cos(a).toFloat() * hr, c.y + sin(a).toFloat() * hr)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f), litA.copy(alpha = 0.35f), Color.Transparent),
                        center = hc,
                        radius = 22.dp.toPx()
                    ),
                    radius = 22.dp.toPx(),
                    center = hc
                )
            }
        }
        Text(
            text = "${(p * 100).toInt()}%",
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp
        )
    }
}
