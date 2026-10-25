package com.aftermotion.studio.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Apple-style "continuous corner" rounded rectangle (squircle). The curvature eases into the
 * straight edges instead of jumping, which is what makes iOS shapes look different from a plain
 * RoundedCornerShape. The radius is clamped so the shape never turns inside out on small sizes.
 */
class ContinuousRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val rMax = minOf(w, h) / 2f / K
        val r = minOf(with(density) { radius.toPx() }, rMax)
        if (r <= 0.5f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val r1 = K * r
        val p = Path()
        p.moveTo(r1, 0f)
        p.lineTo(w - r1, 0f)
        // top-right
        p.cubicTo(w - 1.08849296f * r, 0f, w - 0.86840694f * r, 0f, w - 0.63149379f * r, 0.07491139f * r)
        p.cubicTo(w - 0.37282383f * r, 0.16905956f * r, w - 0.16905956f * r, 0.37282383f * r, w - 0.07491139f * r, 0.63149379f * r)
        p.cubicTo(w, 0.86840694f * r, w, 1.08849296f * r, w, r1)
        p.lineTo(w, h - r1)
        // bottom-right
        p.cubicTo(w, h - 1.08849296f * r, w, h - 0.86840694f * r, w - 0.07491139f * r, h - 0.63149379f * r)
        p.cubicTo(w - 0.16905956f * r, h - 0.37282383f * r, w - 0.37282383f * r, h - 0.16905956f * r, w - 0.63149379f * r, h - 0.07491139f * r)
        p.cubicTo(w - 0.86840694f * r, h, w - 1.08849296f * r, h, w - r1, h)
        p.lineTo(r1, h)
        // bottom-left
        p.cubicTo(1.08849296f * r, h, 0.86840694f * r, h, 0.63149379f * r, h - 0.07491139f * r)
        p.cubicTo(0.37282383f * r, h - 0.16905956f * r, 0.16905956f * r, h - 0.37282383f * r, 0.07491139f * r, h - 0.63149379f * r)
        p.cubicTo(0f, h - 0.86840694f * r, 0f, h - 1.08849296f * r, 0f, h - r1)
        p.lineTo(0f, r1)
        // top-left
        p.cubicTo(0f, 1.08849296f * r, 0f, 0.86840694f * r, 0.07491139f * r, 0.63149379f * r)
        p.cubicTo(0.16905956f * r, 0.37282383f * r, 0.37282383f * r, 0.16905956f * r, 0.63149379f * r, 0.07491139f * r)
        p.cubicTo(0.86840694f * r, 0f, 1.08849296f * r, 0f, r1, 0f)
        p.close()
        return Outline.Generic(p)
    }

    private companion object {
        const val K = 1.52866483f
    }
}

/** Ready-made squircles. */
val SquircleSmall = ContinuousRoundedShape(10.dp)
val SquircleMedium = ContinuousRoundedShape(16.dp)
val SquircleLarge = ContinuousRoundedShape(26.dp)
