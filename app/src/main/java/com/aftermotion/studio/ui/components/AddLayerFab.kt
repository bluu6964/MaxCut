package com.aftermotion.studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class RadialMenuItem(
    val icon: ImageVector,
    val contentDescription: String,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * Bottom-right "+" FAB (cyan/green ring design). Tap expands a radial menu — rocket-fast.
 * Collapsed: button sits in the true corner. Expanded: menu grows from that corner.
 */
@Composable
fun AddLayerFab(
    onAudioClick: () -> Unit,
    onTextClick: () -> Unit,
    onShapeClick: () -> Unit,
    onTransformClick: () -> Unit,
    onMediaClick: () -> Unit,
    onStickersClick: () -> Unit,
    onEffectsClick: () -> Unit = {},
    onBrightnessClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    fabSize: Dp = 58.dp,
    menuRadius: Dp = 112.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val expandProgress = remember { Animatable(0f) }

    // Rocket-fast open/close (~140ms)
    LaunchedEffect(expanded) {
        expandProgress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(durationMillis = 140, easing = LinearEasing)
        )
    }

    val items = remember(
        onStickersClick, onMediaClick, onShapeClick, onTextClick,
        onTransformClick, onEffectsClick, onBrightnessClick, onAudioClick
    ) {
        listOf(
            RadialMenuItem(Icons.Default.Apps, "Stickers", "radial_stickers", onStickersClick),
            RadialMenuItem(Icons.Default.ZoomIn, "Media", "radial_media", onMediaClick),
            RadialMenuItem(Icons.Default.Edit, "Shape", "radial_shape", onShapeClick),
            RadialMenuItem(Icons.Default.TextFields, "Text", "radial_text", onTextClick),
            RadialMenuItem(Icons.Default.SwapVert, "Transform", "radial_transform", onTransformClick),
            RadialMenuItem(Icons.Default.Palette, "Effects", "radial_effects", onEffectsClick),
            RadialMenuItem(Icons.Default.Brightness7, "Brightness", "radial_brightness", onBrightnessClick),
            RadialMenuItem(Icons.Default.VolumeUp, "Audio", "radial_audio", onAudioClick)
        )
    }

    val sectorCount = items.size
    val sectorAngle = 360f / sectorCount
    val progress = expandProgress.value

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_layer_fab_root")
    ) {
        // Dim scrim — tap to close
        if (progress > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        // Anchor: true bottom-end. FAB is the anchor; menu expands around it.
        val pad = 16.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = pad, bottom = pad)
                .size(if (progress > 0.01f) menuRadius * 2 + fabSize else fabSize)
        ) {
            // Radial pie (only while opening / open)
            if (progress > 0.01f) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Grow from bottom-right corner of this box (where FAB sits)
                            val pivotX = size.width
                            val pivotY = size.height
                            this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                pivotX / size.width.coerceAtLeast(1f),
                                pivotY / size.height.coerceAtLeast(1f)
                            )
                            scaleX = 0.35f + 0.65f * progress
                            scaleY = 0.35f + 0.65f * progress
                            alpha = progress
                        }
                ) {
                    val cx = this.size.width - fabSize.toPx() / 2f
                    val cy = this.size.height - fabSize.toPx() / 2f
                    val outerR = menuRadius.toPx()
                    val innerR = fabSize.toPx() / 2f + 4.dp.toPx()

                    drawCircle(
                        color = Color(0xF0121216),
                        radius = outerR,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color(0xFF2A2A32),
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    for (i in 0 until sectorCount) {
                        val angleDeg = -90f + i * sectorAngle
                        val rad = Math.toRadians(angleDeg.toDouble())
                        drawLine(
                            color = Color(0xFF2C2C34),
                            start = Offset(
                                cx + cos(rad).toFloat() * innerR,
                                cy + sin(rad).toFloat() * innerR
                            ),
                            end = Offset(
                                cx + cos(rad).toFloat() * outerR,
                                cy + sin(rad).toFloat() * outerR
                            ),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    // Accent arc (top-left sector)
                    val accentStart = -90f + 7 * sectorAngle
                    drawArc(
                        color = Color(0xFF00E5A8),
                        startAngle = accentStart,
                        sweepAngle = sectorAngle,
                        useCenter = false,
                        topLeft = Offset(cx - outerR, cy - outerR),
                        size = Size(outerR * 2, outerR * 2),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = innerR,
                        center = Offset(cx, cy)
                    )
                }

                // Icons — positioned around FAB at bottom-right of this box
                items.forEachIndexed { index, item ->
                    val midAngle = -90f + index * sectorAngle + sectorAngle / 2f
                    val rad = Math.toRadians(midAngle.toDouble())
                    val iconR = menuRadius.value * 0.62f
                    val iconOffsetX = (cos(rad) * iconR).dp
                    val iconOffsetY = (sin(rad) * iconR).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = (fabSize - 44.dp) / 2, bottom = (fabSize - 44.dp) / 2)
                            .offset(x = iconOffsetX, y = iconOffsetY)
                            .size(44.dp)
                            .graphicsLayer {
                                alpha = progress
                                scaleX = 0.5f + 0.5f * progress
                                scaleY = 0.5f + 0.5f * progress
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                expanded = false
                                item.onClick()
                            }
                            .testTag(item.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // FAB — always bottom-end of the anchor box
            RingPlusButton(
                expanded = expanded,
                size = fabSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .testTag("add_layer_fab_button"),
                onClick = { expanded = !expanded }
            )
        }
    }
}

/** Concentric cyan / green / blue rings + white plus (matches reference art). */
@Composable
private fun RingPlusButton(
    expanded: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(120, easing = LinearEasing),
        label = "plusRot"
    )
    val press by animateFloatAsState(
        targetValue = if (expanded) 0.92f else 1f,
        animationSpec = tween(100, easing = LinearEasing),
        label = "plusScale"
    )
    // Subtle continuous ring shimmer (fast, cheap)
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shimmer.animateTo(1f, tween(900, easing = LinearEasing))
            shimmer.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val min = this.size.minDimension
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = min / 2f

            // Soft outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x5500E5A8),
                        Color(0x2200C8FF),
                        Color(0x00000000)
                    ),
                    center = c,
                    radius = r * 1.35f
                ),
                radius = r * 1.35f,
                center = c
            )

            // Dark core
            drawCircle(color = Color(0xFF0A0E14), radius = r * 0.72f, center = c)

            // Outer thick green ring (partial arcs for depth)
            val outerStroke = r * 0.18f
            drawArc(
                color = Color(0xFF00E8A0),
                startAngle = -40f + shimmer.value * 20f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.92f),
                size = Size(r * 1.84f, r * 1.84f),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF00C878),
                startAngle = 160f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.92f),
                size = Size(r * 1.84f, r * 1.84f),
                style = Stroke(width = outerStroke * 0.85f, cap = StrokeCap.Round)
            )

            // Mid blue ring
            val midR = r * 0.58f
            drawArc(
                color = Color(0xFF4A7AFF),
                startAngle = 20f - shimmer.value * 15f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(c.x - midR, c.y - midR),
                size = Size(midR * 2, midR * 2),
                style = Stroke(width = r * 0.12f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF2A4A9A),
                startAngle = 200f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(c.x - midR, c.y - midR),
                size = Size(midR * 2, midR * 2),
                style = Stroke(width = r * 0.10f, cap = StrokeCap.Round)
            )

            // Inner dark ring edge
            drawCircle(
                color = Color(0xFF1A2230),
                radius = r * 0.38f,
                center = c,
                style = Stroke(width = r * 0.06f)
            )
        }

        // White +
        Canvas(
            Modifier
                .size(size * 0.36f)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val stroke = 4.2.dp.toPx()
            val half = this.size.minDimension / 2f
            val arm = half * 0.55f
            drawLine(
                color = Color.White,
                start = Offset(half - arm, half),
                end = Offset(half + arm, half),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(half, half - arm),
                end = Offset(half, half + arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
