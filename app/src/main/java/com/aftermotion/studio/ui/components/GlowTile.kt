package com.aftermotion.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.aftermotion.studio.ui.theme.ContinuousRoundedShape
import androidx.compose.ui.unit.dp

/**
 * One colour "recipe" for a glowing squircle tile: bright [edge] colour, a dark [deep] core in the
 * middle and a [glow] light that pools at the bottom edge (the iOS 18 / ColorOS look).
 */
data class GlowStyle(val edge: Color, val glow: Color, val deep: Color)

object GlowStyles {
    val Ember = GlowStyle(Color(0xFFFF3B14), Color(0xFFFF9A4D), Color(0xFF1E0408))
    val Lime = GlowStyle(Color(0xFFC8F31D), Color(0xFFE9FF5E), Color(0xFF3A1AA8))
    val Mint = GlowStyle(Color(0xFF37D67A), Color(0xFF7BFFB0), Color(0xFF03140A))
    val Aqua = GlowStyle(Color(0xFF4FD0FF), Color(0xFFB4F0FF), Color(0xFF072A38))
    val Rose = GlowStyle(Color(0xFFFF7ACB), Color(0xFFFFB3E6), Color(0xFF1A0420))
    val Indigo = GlowStyle(Color(0xFF3A5BFF), Color(0xFF6FA8FF), Color(0xFF050A2E))
    val Violet = GlowStyle(Color(0xFF9B3BFF), Color(0xFFD08CFF), Color(0xFF12031F))
    val Amber = GlowStyle(Color(0xFFFFA81F), Color(0xFFFFE08A), Color(0xFF2B1300))
}

/** Soft glass surface: translucent white gradient + thin light rim. Put it AFTER bouncyClickable. */
fun Modifier.glassSurface(
    shape: Shape,
    tint: Color = Color.White,
    strength: Float = 1f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f * strength), tint.copy(alpha = 0.06f * strength)))
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.45f * strength),
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.18f * strength)
            )
        ),
        shape
    )

/**
 * Glowing squircle tile (mesh-gradient look): bright rim colour outside, dark core inside, light
 * pooling at the bottom, glass sheen on top, thin white rim and a soft coloured halo behind it.
 */
@Composable
fun GlowTile(
    style: GlowStyle,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    halo: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val shape = ContinuousRoundedShape(cornerRadius)
    Box(
        modifier = modifier
            .drawBehind {
                if (halo) {
                    val r = maxOf(size.width, size.height) * 0.78f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(style.glow.copy(alpha = 0.34f), Color.Transparent),
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center
                    )
                }
            }
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.60f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.28f)
                    )
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(style.edge)
            drawRect(
                Brush.radialGradient(
                    listOf(style.deep.copy(alpha = 0.95f), style.deep.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w / 2f, h * 0.46f),
                    radius = minOf(w, h) * 0.64f
                )
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, style.glow.copy(alpha = 0.60f)),
                    startY = h * 0.45f,
                    endY = h
                )
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.42f
                )
            )
        }
        content()
    }
}
