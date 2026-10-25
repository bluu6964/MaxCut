package com.aftermotion.studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
    val style: GlowStyle,
    val onClick: () -> Unit
)

/**
 * Bottom-right "+" FAB. Tap fans out 8 glowing squircle tiles (3 inner + 5 outer) up and left of
 * the corner. Every tool has its own colour. Tiles spring out one after another from the "+"
 * (Apple-style soft overshoot) and fall back into it on close.
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
    menuRadius: Dp = 176.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val expandProgress = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        expandProgress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 330f)
        )
    }

    val items = remember(
        onStickersClick, onMediaClick, onShapeClick, onTextClick,
        onTransformClick, onEffectsClick, onBrightnessClick, onAudioClick
    ) {
        listOf(
            // inner ring (3)
            RadialMenuItem(Icons.Default.ZoomIn, "Media", "radial_media", GlowStyles.Indigo, onMediaClick),
            RadialMenuItem(Icons.Default.TextFields, "Text", "radial_text", GlowStyles.Lime, onTextClick),
            RadialMenuItem(Icons.Default.VolumeUp, "Audio", "radial_audio", GlowStyles.Ember, onAudioClick),
            // outer ring (5)
            RadialMenuItem(Icons.Default.Apps, "Stickers", "radial_stickers", GlowStyles.Rose, onStickersClick),
            RadialMenuItem(Icons.Default.Edit, "Shape", "radial_shape", GlowStyles.Aqua, onShapeClick),
            RadialMenuItem(Icons.Default.SwapVert, "Transform", "radial_transform", GlowStyles.Violet, onTransformClick),
            RadialMenuItem(Icons.Default.Palette, "Effects", "radial_effects", GlowStyles.Mint, onEffectsClick),
            RadialMenuItem(Icons.Default.Brightness7, "Brightness", "radial_brightness", GlowStyles.Amber, onBrightnessClick)
        )
    }

    val innerCount = 3
    val outerCount = items.size - innerCount
    val fanStart = 180f
    val fanSweep = 90f
    val innerSector = fanSweep / innerCount
    val outerSector = fanSweep / outerCount
    val innerItemR = 88f   // dp
    val outerItemR = 148f  // dp
    val tileSize = 42.dp
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
                    .graphicsLayer { alpha = progress.coerceIn(0f, 1f) * 0.55f }
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF05060A), Color(0xFF000000)),
                            center = Offset.Unspecified,
                            radius = 2400f
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        val pad = 16.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = pad, bottom = pad)
                .size(if (expanded || progress > 0.01f) menuRadius + fabSize / 2 else fabSize)
        ) {
            if (expanded || progress > 0.01f) {
                items.forEachIndexed { index, item ->
                    val isInner = index < innerCount
                    val idx = if (isInner) index else index - innerCount
                    val sector = if (isInner) innerSector else outerSector
                    val r = if (isInner) innerItemR else outerItemR
                    val midAngle = fanStart + idx * sector + sector / 2f
                    val rad = Math.toRadians(midAngle.toDouble())
                    val ox = (cos(rad) * r).toFloat()
                    val oy = (sin(rad) * r).toFloat()
                    // each tile starts a little after the previous one
                    val delay = index * 0.035f
                    val p = ((progress - delay) / (1f - delay)).coerceAtLeast(0f)

                    GlowTile(
                        style = item.style,
                        cornerRadius = 14.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = (fabSize - tileSize) / 2, bottom = (fabSize - tileSize) / 2)
                            .size(tileSize)
                            .graphicsLayer {
                                translationX = ox.dp.toPx() * p
                                translationY = oy.dp.toPx() * p
                                val sc = (0.3f + 0.7f * p).coerceAtLeast(0f)
                                scaleX = sc
                                scaleY = sc
                                alpha = (p * 1.4f).coerceIn(0f, 1f)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                expanded = false
                                item.onClick()
                            }
                            .testTag(item.testTag)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

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
