package com.aftermotion.studio.ui.components

import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aftermotion.studio.model.AnimProp
import com.aftermotion.studio.model.DEPTH_HIDE_Z
import com.aftermotion.studio.model.depthFactorOf
import com.aftermotion.studio.model.AspectRatio
import com.aftermotion.studio.model.Easing
import com.aftermotion.studio.model.indexOnTime
import com.aftermotion.studio.model.segmentIndexAt
import com.aftermotion.studio.model.LayerType
import com.aftermotion.studio.model.TimelineClip
import com.aftermotion.studio.ui.theme.CanvasBackground
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.ui.theme.IconActive
import com.aftermotion.studio.ui.theme.NeonCyan
import com.aftermotion.studio.ui.theme.RulerSubTickColor
import com.aftermotion.studio.ui.theme.RulerTickColor
import com.aftermotion.studio.ui.theme.TextPrimary
import com.aftermotion.studio.ui.theme.TextSecondary
import com.aftermotion.studio.ui.util.bouncyClickable

internal enum class Transform2DPart { POSITION, SCALE, ROTATION }
internal enum class Transform3DPart { POSITION_Z, ROTATION_X, ROTATION_Y }

/** Practically unlimited: the rulers only need *some* end, so these are far beyond anything usable. */
internal const val TRANSFORM_BIG = 100000f
internal const val Z_RANGE_MIN = 5000f
internal const val Z_RANGE_MAX = 1000f
internal const val TRANSFORM_SCALE_MIN = 1f
internal const val TRANSFORM_SCALE_MAX = 100000f

private class RailItem(val icon: ImageVector, val badge: String?, val description: String)

private val rail2d = listOf(
    RailItem(Icons.Default.OpenWith, null, "Position"),
    RailItem(Icons.Default.OpenInFull, null, "Scale"),
    RailItem(Icons.Default.RotateRight, null, "Rotation")
)
private val rail3d = listOf(
    RailItem(Icons.Default.OpenWith, "Z", "Position Z"),
    RailItem(Icons.Default.RotateRight, "X", "Rotation X"),
    RailItem(Icons.Default.RotateRight, "Y", "Rotation Y")
)

enum class TransformTab(val title: String) {
    TWO_D("2D"),
    THREE_D("3D")
}

@Composable
fun TransformView(
    clip: TimelineClip?,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    player: Player? = null,
    hasMainMedia: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDismiss: () -> Unit,
    panelOnly: Boolean = false,
    timescaleHeight: androidx.compose.ui.unit.Dp = 28.dp,
    layerRow: (@Composable () -> Unit)? = null,
    resetTick: Int = 0,
    /** Diamond button: add / remove a keyframe of this property at the playhead. */
    onToggleKeyframe: (AnimProp) -> Unit = {},
    /** Easing editor: a change is about to start (one undo step) / the curve of the segment that starts at the keyframe time changed. */
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> },
    onApplyTransform: (
        horizontal: Float,
        vertical: Float,
        scale: Float,
        rotation: Float,
        pivotX: Float,
        pivotY: Float,
        rot3dX: Float,
        rot3dY: Float,
        warp: Float,
        posZ: Float
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current active mode tab: 2D, 3D
    var activeTab by remember { mutableStateOf(TransformTab.TWO_D) }
    var part2d by remember { mutableStateOf(Transform2DPart.POSITION) }
    var part3d by remember { mutableStateOf(Transform3DPart.POSITION_Z) }

    // 2D Transform state
    var horizontal by remember(clip?.id) { mutableFloatStateOf(clip?.transformX ?: 0.0f) }
    var vertical by remember(clip?.id) { mutableFloatStateOf(clip?.transformY ?: 0.0f) }
    var scale by remember(clip?.id) { mutableFloatStateOf(clip?.scalePercent ?: 100.0f) }
    var rotation by remember(clip?.id) { mutableFloatStateOf(clip?.rotationDeg ?: 0.0f) }

    // Pivot state
    var pivotX by remember(clip?.id) { mutableFloatStateOf(clip?.pivotX ?: 0.5f) }
    var pivotY by remember(clip?.id) { mutableFloatStateOf(clip?.pivotY ?: 0.5f) }

    // 3D Transform state
    var rot3dX by remember(clip?.id) { mutableFloatStateOf(clip?.rotation3dX ?: 0.0f) }
    var rot3dY by remember(clip?.id) { mutableFloatStateOf(clip?.rotation3dY ?: 0.0f) }

    // Depth (Z) position
    var posZ by remember(clip?.id) { mutableFloatStateOf(clip?.transformZ ?: 0.0f) }

    // Warp state
    var warpDistort by remember(clip?.id) { mutableFloatStateOf(clip?.warpDistort ?: 0.0f) }

    // With keyframes the clip given here is the animated one at the playhead: follow it while the
    // playhead moves or a keyframe changes, so the rulers / pad always show the real current value.
    // Every change of the layer (also one made on the canvas by dragging / scaling / rotating it)
    // re-syncs the panel, not only keyframe changes.
    val animSyncKey: Any? = if (clip != null) {
        listOf(
            currentPlayheadMs, clip.keyframes,
            clip.transformX, clip.transformY, clip.scalePercent, clip.rotationDeg,
            clip.pivotX, clip.pivotY, clip.rotation3dX, clip.rotation3dY,
            clip.transformZ, clip.warpDistort
        )
    } else null
    LaunchedEffect(clip?.id, animSyncKey) {
        if (clip != null) {
            horizontal = clip.transformX
            vertical = clip.transformY
            scale = clip.scalePercent
            rotation = clip.rotationDeg
            pivotX = clip.pivotX
            pivotY = clip.pivotY
            rot3dX = clip.rotation3dX
            rot3dY = clip.rotation3dY
            warpDistort = clip.warpDistort
            posZ = clip.transformZ
        }
    }

    fun emitTransform() {
        onApplyTransform(
            horizontal,
            vertical,
            scale,
            rotation,
            pivotX,
            pivotY,
            rot3dX,
            rot3dY,
            warpDistort,
            posZ
        )
    }

    fun resetAll() {
        horizontal = 0.0f
        vertical = 0.0f
        scale = 100.0f
        rotation = 0.0f
        pivotX = 0.5f
        pivotY = 0.5f
        rot3dX = 0.0f
        rot3dY = 0.0f
        warpDistort = 0.0f
        posZ = 0.0f
        emitTransform()
    }

    androidx.compose.runtime.LaunchedEffect(resetTick) { if (resetTick > 0) resetAll() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (panelOnly) EditorBackground else Color(0xFF0F0F0F))
            .testTag("transform_screen")
    ) {
        if (panelOnly) {
            // Play / undo-redo stays universal (main PlayControlsSection). Only timescale + layer strip here.
            TimescaleSection(
                height = timescaleHeight,
                playheadMs = { currentPlayheadMs },
                totalDurationMs = totalDurationMs
            )
            if (layerRow != null) layerRow() else TransformLayerStrip(
                clip = clip,
                currentPlayheadMs = currentPlayheadMs,
                totalDurationMs = totalDurationMs,
                onSeekTo = onSeekTo,
                onReset = { resetAll() }
            )
        }

        if (!panelOnly) {
        // 1. Header: Left down arrow button, Center "Transform" title, Right "Done" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .bouncyClickable(onClick = {
                        emitTransform()
                        onDismiss()
                    })
                    .testTag("transform_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse Transform",
                    tint = IconActive,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Transform",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan)
                    .bouncyClickable(onClick = {
                        emitTransform()
                        onDismiss()
                    })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = EditorBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Video Preview Canvas with Transformed frame and Magenta Reticle Crosshair
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 0.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val targetRatio = aspectRatio.ratioFloat

            val (frameW, frameH) = remember(maxW, maxH, targetRatio) {
                if (maxW.value / maxH.value > targetRatio) {
                    (maxH * targetRatio) to maxH
                } else {
                    maxW to (maxW / targetRatio)
                }
            }

            val isMainVideo = (clip == null || clip.type == LayerType.VIDEO)

            Box(
                modifier = Modifier
                    .size(width = frameW, height = frameH)
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .background(com.aftermotion.studio.ui.theme.LocalCanvasColor.current)
                    .pointerInput(clip?.id) {
                        var startDragH = horizontal
                        var startDragV = vertical
                        detectDragGestures(
                            onDragStart = {
                                startDragH = horizontal
                                startDragV = vertical
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                startDragH += dragAmount.x / depthFactorOf(posZ)
                                startDragV += dragAmount.y / depthFactorOf(posZ)
                                horizontal = startDragH
                                vertical = startDragV
                                emitTransform()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isMainVideo) {
                    // Transforming the main video track
                    if (player != null && hasMainMedia) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = horizontal * depthFactorOf(posZ)
                                    translationY = vertical * depthFactorOf(posZ)
                                    // Z: past the camera = gone
                                    alpha = if (posZ >= DEPTH_HIDE_Z) 0f else 1f
                                    scaleX = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    scaleY = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    rotationZ = rotation
                                    rotationX = rot3dX
                                    rotationY = rot3dY
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, pivotY)
                                    cameraDistance = 12f * density
                                },
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    this.player = player
                                }
                            },
                            update = { it.player = player },
                        onRelease = {
                            it.player = null
                            PlayerSurfaceNudge.bump()
                        }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = horizontal * depthFactorOf(posZ)
                                    translationY = vertical * depthFactorOf(posZ)
                                    // Z: past the camera = gone
                                    alpha = if (posZ >= DEPTH_HIDE_Z) 0f else 1f
                                    scaleX = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    scaleY = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    rotationZ = rotation
                                    rotationX = rot3dX
                                    rotationY = rot3dY
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, pivotY)
                                    cameraDistance = 12f * density
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(color = CanvasBackground)
                                val w = size.width
                                val h = size.height
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(w / 2f, 0f),
                                    end = Offset(w / 2f, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(0f, h / 2f),
                                    end = Offset(w, h / 2f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                } else {
                    // Transforming an overlay (e.g. Shape, Text, Sticker)
                    // 1. Base background media/canvas
                    if (player != null && hasMainMedia) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    this.player = player
                                }
                            },
                            update = { it.player = player },
                        onRelease = {
                            it.player = null
                            PlayerSurfaceNudge.bump()
                        }
                        )
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(color = CanvasBackground)
                            val w = size.width
                            val h = size.height
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(w / 2f, 0f),
                                end = Offset(w / 2f, h),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, h / 2f),
                                end = Offset(w, h / 2f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // 2. Transformed overlay layer
                    if (clip.type == LayerType.SHAPE) {
                        val rawColor = if (clip.textColor != 0L) clip.textColor else clip.color
                        val a = ((rawColor shr 24) and 0xFF).toInt()
                        val r = ((rawColor shr 16) and 0xFF).toInt()
                        val g = ((rawColor shr 8) and 0xFF).toInt()
                        val b = (rawColor and 0xFF).toInt()
                        val shapeColor = if (a == 0 && r == 0 && g == 0 && b == 0) {
                            Color(0xFFABE41D)
                        } else {
                            Color(r, g, b, if (a == 0) 255 else a)
                        }

                        val strokeRaw = clip.strokeColor
                        val strA = ((strokeRaw shr 24) and 0xFF).toInt()
                        val strR = ((strokeRaw shr 16) and 0xFF).toInt()
                        val strG = ((strokeRaw shr 8) and 0xFF).toInt()
                        val strB = (strokeRaw and 0xFF).toInt()
                        val strokeCol = Color(strR, strG, strB, if (strA == 0) 255 else strA)

                        val pattern = clip.shapePattern ?: "CIRCLE"
                    val fillBrush = clip.fillBrush()
                        val baseSize = 130.dp

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationX = horizontal * depthFactorOf(posZ)
                                    translationY = vertical * depthFactorOf(posZ)
                                    // Z: past the camera = gone
                                    alpha = if (posZ >= DEPTH_HIDE_Z) 0f else 1f
                                    scaleX = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    scaleY = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    rotationZ = rotation
                                    rotationX = rot3dX
                                    rotationY = rot3dY
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, pivotY)
                                    cameraDistance = 12f * density
                                }
                                .size(baseSize)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                when (pattern) {
                                    "CIRCLE" -> {
                                        drawCircle(brush = fillBrush)
                                        if (clip.strokeWidth > 0f) {
                                            drawCircle(color = strokeCol, style = Stroke(clip.strokeWidth.dp.toPx()))
                                        }
                                    }
                                    "SQUARE" -> {
                                        drawRect(brush = fillBrush)
                                        if (clip.strokeWidth > 0f) {
                                            drawRect(color = strokeCol, style = Stroke(clip.strokeWidth.dp.toPx()))
                                        }
                                    }
                                    "ROUNDED_SQUARE", "ROUNDED_RECT" -> {
                                        drawRoundRect(
                                            brush = fillBrush,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                        )
                                        if (clip.strokeWidth > 0f) {
                                            drawRoundRect(
                                                color = strokeCol,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                                                style = Stroke(clip.strokeWidth.dp.toPx())
                                            )
                                        }
                                    }
                                    "WIDE_ROUNDED_RECT" -> {
                                        drawRoundRect(
                                            brush = fillBrush,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                        )
                                        if (clip.strokeWidth > 0f) {
                                            drawRoundRect(
                                                color = strokeCol,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                                                style = Stroke(clip.strokeWidth.dp.toPx())
                                            )
                                        }
                                    }
                                    "OVAL" -> {
                                        drawOval(brush = fillBrush)
                                        if (clip.strokeWidth > 0f) {
                                            drawOval(color = strokeCol, style = Stroke(clip.strokeWidth.dp.toPx()))
                                        }
                                    }
                                    "PENTAGON", "STAR" -> {
                                        val path = Path().apply {
                                            val rad = size.minDimension / 2f
                                            val cX = size.width / 2f
                                            val cY = size.height / 2f
                                            val points = 5
                                            for (i in 0 until points) {
                                                val angle = Math.toRadians((i * 360.0 / points) - 90.0)
                                                val px = (cX + rad * Math.cos(angle)).toFloat()
                                                val py = (cY + rad * Math.sin(angle)).toFloat()
                                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                                            }
                                            close()
                                        }
                                        drawPath(path, brush = fillBrush)
                                        if (clip.strokeWidth > 0f) {
                                            drawPath(path, color = strokeCol, style = Stroke(clip.strokeWidth.dp.toPx()))
                                        }
                                    }
                                    else -> drawCircle(brush = fillBrush)
                                }
                            }

                            // Pink selection outline on the transformed shape
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.5.dp, Color(0xFFABE41D), RoundedCornerShape(2.dp))
                            )
                        }
                    } else if (clip.text != null || clip.type == LayerType.TEXT) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationX = horizontal * depthFactorOf(posZ)
                                    translationY = vertical * depthFactorOf(posZ)
                                    // Z: past the camera = gone
                                    alpha = if (posZ >= DEPTH_HIDE_Z) 0f else 1f
                                    scaleX = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    scaleY = (scale / 100f).coerceAtLeast(0.05f) * depthFactorOf(posZ)
                                    rotationZ = rotation
                                    rotationX = rot3dX
                                    rotationY = rot3dY
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, pivotY)
                                    cameraDistance = 12f * density
                                }
                                .border(1.dp, Color.White)
                        ) {
                            TextLayerContent(clip = clip, frameWidth = frameW)
                        }
                    }
                }
            }
        }

        // 3. Playback Controls Row: Skip Prev, Play/Pause, Skip Next, Undo, Redo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(36.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyClickable(onClick = { onSeekTo(0L) }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = IconActive,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyClickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = IconActive,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyClickable(onClick = { onSeekTo(totalDurationMs) }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = IconActive,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable(enabled = canUndo, onClick = onUndo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) IconActive else Color(0xFF4A4A4A),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable(enabled = canRedo, onClick = onRedo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) IconActive else Color(0xFF4A4A4A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. Timescale: 00:03 47 | 00:19 49 with ruler ticks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(0.36f))
            Text(
                text = formatTimeCode(currentPlayheadMs),
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(text = "  |  ", color = TextSecondary, fontSize = 13.sp)
            Text(
                text = formatTimeCode(totalDurationMs),
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(0.48f)
                    .height(14.dp)
            ) {
                val tickSpacing = 8.dp.toPx()
                val count = (size.width / tickSpacing).toInt()
                for (i in 0..count) {
                    val isMajor = (i % 5 == 0)
                    drawLine(
                        color = if (isMajor) RulerTickColor else RulerSubTickColor,
                        start = Offset(i * tickSpacing, 0f),
                        end = Offset(i * tickSpacing, if (isMajor) size.height else size.height * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        // 5. Mini Layer Strip: [Reset button on left] [Timeline strip with keyframe diamond and easing icon]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset Button on left (matching screenshot)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1D1D1D))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                    .bouncyClickable(onClick = {
                        horizontal = 0.0f
                        vertical = 0.0f
                        scale = 100.0f
                        rotation = 0.0f
                        pivotX = 0.5f
                        pivotY = 0.5f
                        rot3dX = 0.0f
                        rot3dY = 0.0f
                        warpDistort = 0.0f
                        posZ = 0.0f
                        emitTransform()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Transform",
                    tint = IconActive,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Thumbnail strip container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Keyframe diamond indicator & Easing curve icon on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(Color(0xFF3B3B3B), RoundedCornerShape(2.dp))
                            .bouncyClickable(onClick = {}),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Keyframe",
                            tint = Color.White,
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer { rotationZ = -45f }
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Easing S-Curve Icon (matching screenshot)
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height)
                            cubicTo(
                                size.width * 0.4f, size.height,
                                size.width * 0.6f, 0f,
                                size.width, 0f
                            )
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF8E8E8E),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        }

        // 6. Bottom Transform Panel — Apple-style pro panel (see TransformPanelPro.kt)
        TransformBottomPanel(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (panelOnly) Modifier.weight(1f) else Modifier),
            panelOnly = panelOnly,
            clip = clip,
            currentPlayheadMs = currentPlayheadMs,
            activeTab = activeTab,
            onTabChange = { activeTab = it },
            part2d = part2d,
            onPart2d = { part2d = it },
            part3d = part3d,
            onPart3d = { part3d = it },
            horizontal = horizontal,
            vertical = vertical,
            scale = scale,
            rotation = rotation,
            rot3dX = rot3dX,
            rot3dY = rot3dY,
            posZ = posZ,
            onPosition = { nx, ny -> horizontal = nx; vertical = ny; emitTransform() },
            onScale = { scale = it; emitTransform() },
            onRotation = { rotation = it; emitTransform() },
            onPosZ = { posZ = it; emitTransform() },
            onRotX = { rot3dX = it; emitTransform() },
            onRotY = { rot3dY = it; emitTransform() },
            onDismiss = onDismiss,
            onToggleKeyframe = onToggleKeyframe,
            onEasingBegin = onEasingBegin,
            onEasingChange = onEasingChange
        )
    }
}

/**
 * 4-lobed Pink Crosshair Pivot Anchor Target exactly matching screenshot.
 */
@Composable
fun PivotTargetCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(36.dp)) {
        val pinkColor = Color(0xFFABE41D)
        val cX = size.width / 2f
        val cY = size.height / 2f
        val centerRadius = 3.5.dp.toPx()
        val armLength = 7.dp.toPx()
        val armWidth = 3.dp.toPx()
        val gap = 4.dp.toPx()

        // Center circle
        drawCircle(color = pinkColor, radius = centerRadius)
        drawCircle(color = Color.White, radius = 1.2.dp.toPx())

        // Top arm
        drawRoundRect(
            color = pinkColor,
            topLeft = Offset(cX - armWidth / 2f, cY - gap - armLength),
            size = Size(armWidth, armLength),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(armWidth / 2f, armWidth / 2f)
        )
        // Bottom arm
        drawRoundRect(
            color = pinkColor,
            topLeft = Offset(cX - armWidth / 2f, cY + gap),
            size = Size(armWidth, armLength),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(armWidth / 2f, armWidth / 2f)
        )
        // Left arm
        drawRoundRect(
            color = pinkColor,
            topLeft = Offset(cX - gap - armLength, cY - armWidth / 2f),
            size = Size(armLength, armWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(armWidth / 2f, armWidth / 2f)
        )
        // Right arm
        drawRoundRect(
            color = pinkColor,
            topLeft = Offset(cX + gap, cY - armWidth / 2f),
            size = Size(armLength, armWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(armWidth / 2f, armWidth / 2f)
        )
    }
}

/**
 * 2D position pad (Alight Motion style): put a finger on the pad and move it, the layer's X and Y
 * change together by the same distance the finger moves (1 px = 1 unit). Shows the live X / Y.
 */
@Composable
private fun PositionPad(
    x: Float,
    y: Float,
    onChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val curX by rememberUpdatedState(x)
    val curY by rememberUpdatedState(y)
    val change by rememberUpdatedState(onChange)
    val range = 100000f   // practically unlimited
    val visualRange = 500f // the dot shows a +-500 window, then stays at the edge
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var px = curX
                        var py = curY
                        var last = down.position
                        var lastX = curX
                        var lastY = curY
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            val d = c.position - last
                            last = c.position
                            px = (px + d.x).coerceIn(-range, range)
                            py = (py + d.y).coerceIn(-range, range)
                            val nx = px.roundToInt().toFloat()
                            val ny = py.roundToInt().toFloat()
                            if (nx != lastX || ny != lastY) {
                                lastX = nx
                                lastY = ny
                                change(nx, ny)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val grid = 24.dp.toPx()
                var gx = w / 2f % grid
                while (gx < w) {
                    drawLine(Color(0xFF262626), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1.dp.toPx())
                    gx += grid
                }
                var gy = h / 2f % grid
                while (gy < h) {
                    drawLine(Color(0xFF262626), Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
                    gy += grid
                }
                // Center axes
                drawLine(Color(0xFF4A4A4A), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.5.dp.toPx())
                drawLine(Color(0xFF4A4A4A), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.5.dp.toPx())
                // Current position
                val r = 9.dp.toPx()
                val dx = (w / 2f + (x / visualRange) * (w / 2f)).coerceIn(r, w - r)
                val dy = (h / 2f + (y / visualRange) * (h / 2f)).coerceIn(r, h - r)
                drawCircle(Color(0xFFABE41D).copy(alpha = 0.25f), radius = r * 1.9f, center = Offset(dx, dy))
                drawCircle(Color(0xFFABE41D), radius = r, center = Offset(dx, dy))
            }
        }
    }
}

/** Left rail of icons: tap one to show only that section. [RailItem.badge] is a small axis letter. */
@Composable
private fun IconRail(
    items: List<RailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(44.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val tint = if (selected) Color(0xFFABE41D) else TextPrimary
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF2E2E2E) else Color.Transparent)
                    .bouncyClickable(onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.description,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
                if (item.badge != null) {
                    Text(
                        text = item.badge,
                        color = tint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Top of the panel (Alight Motion style): back on the left, the live value in the middle,
 * keyframe diamond + easing curve on the right.
 *
 * Diamond: filled = the playhead sits on a keyframe (tap removes it), outlined = this property is
 * animated but there is no keyframe here, grey = not animated yet (tap adds a keyframe).
 * Easing: only usable between two keyframes; it shows the curve of that segment.
 */
@Composable
internal fun TransformValueHeader(
    valueText: String,
    onBack: (() -> Unit)?,
    keyframeEnabled: Boolean,
    hasKeyframes: Boolean,
    onKeyframeNow: Boolean,
    onKeyframe: () -> Unit,
    easingEnabled: Boolean,
    easingOpen: Boolean,
    easing: Easing?,
    onEasing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFABE41D)
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .bouncyClickable(onClick = onBack)
                    .testTag("panel_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back",
                    tint = IconActive,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }

        Text(
            text = valueText,
            color = TextPrimary,
            fontSize = 15.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // Keyframe diamond
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { alpha = if (keyframeEnabled) 1f else 0.35f }
                .bouncyClickable(onClick = { if (keyframeEnabled) onKeyframe() })
                .testTag("transform_keyframe_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = 45f }
                    .background(if (onKeyframeNow) accent else Color(0xFF3B3B3B), RoundedCornerShape(3.dp))
                    .then(
                        if (hasKeyframes && !onKeyframeNow) Modifier.border(1.5.dp, accent, RoundedCornerShape(3.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!onKeyframeNow) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Keyframe",
                        tint = Color.White,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = -45f }
                    )
                }
            }
        }

        // Easing curve (between two keyframes only)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (easingOpen) Color(0xFF2E2E2E) else Color.Transparent)
                .bouncyClickable(onClick = { if (easingEnabled) onEasing() })
                .testTag("transform_easing_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawEasingIcon(
                    easing = if (easingEnabled) easing else null,
                    color = when {
                        !easingEnabled -> Color(0xFF4A4A4A)
                        easingOpen -> accent
                        else -> Color(0xFFE5E7EB)
                    },
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
