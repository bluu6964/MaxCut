package com.aftermotion.studio.ui.components

import com.aftermotion.studio.model.sourceOffsetMs
import com.aftermotion.studio.engine.StickerLibrary
import com.aftermotion.studio.model.TimelineClip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput as mediaPointerInput
import androidx.compose.ui.layout.onSizeChanged as mediaOnSizeChanged
import androidx.compose.runtime.mutableFloatStateOf as mediaMutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState as mediaRememberUpdatedState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.R
import com.aftermotion.studio.model.AspectRatio
import com.aftermotion.studio.model.LayerType
import com.aftermotion.studio.model.at
import com.aftermotion.studio.model.renderAt
import com.aftermotion.studio.model.TimelineTrack
import com.aftermotion.studio.model.hasBlending
import com.aftermotion.studio.model.opacityFraction
import com.aftermotion.studio.model.effectiveScalePercent
import com.aftermotion.studio.model.depthFactor
import com.aftermotion.studio.model.depthX
import com.aftermotion.studio.model.depthY
import com.aftermotion.studio.model.isBehindCamera
import com.aftermotion.studio.ui.util.layerBlend
import com.aftermotion.studio.ui.theme.CanvasBackground
import com.aftermotion.studio.ui.theme.LocalCanvasColor
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.ui.theme.NeonCyan
import com.aftermotion.studio.ui.theme.NeonGreen

/**
 * Project time of the video picture that is REALLY on screen, for a layer that follows a tracked
 * object ([com.aftermotion.studio.model.TrackFollow]).
 *
 * While playing, the preview video runs on its own clock and is only corrected when it is far off the
 * playhead (see VideoLayerPreview), so the playhead can be ahead of / behind the picture. The follower
 * must use the picture's own position, otherwise it drifts forward and back against the object.
 * Paused or scrubbing, the player is seeked exactly to the playhead, so the playhead is right.
 */
internal fun trackFrameTimeMs(
    c: com.aftermotion.studio.model.TimelineClip,
    playheadMs: Long,
    tracks: List<TimelineTrack>,
    playing: Boolean,
    playerFor: (String) -> Player?,
    scrubbing: Boolean = false,
    scrubFrameOf: ((String) -> com.aftermotion.studio.engine.ScrubShown?)? = null
): Long {
    val f = c.trackFollow ?: return playheadMs
    if (!playing) {
        // Scrubbing: the picture on screen is a scrub frame, which can be a little behind the playhead
        // (it is the newest one that was ready). The layer must sit on the object of THAT frame,
        // otherwise it runs ahead of the picture and looks detached.
        if (!scrubbing || scrubFrameOf == null) return playheadMs
        val vid = tracks.firstNotNullOfOrNull { tr -> tr.clips.firstOrNull { it.id == f.videoId } } ?: return playheadMs
        if (!vid.isActiveAt(playheadMs)) return playheadMs
        val shown = scrubFrameOf(f.videoId) ?: return playheadMs
        return com.aftermotion.studio.model.MotionTrackApply.timelineMsAt(vid, shown.srcMs)
    }
    val video = tracks.firstNotNullOfOrNull { tr -> tr.clips.firstOrNull { it.id == f.videoId } } ?: return playheadMs
    if (!video.isActiveAt(playheadMs)) return playheadMs
    val p = playerFor(f.videoId) ?: return playheadMs
    if (p.playbackState != Player.STATE_READY || !p.isPlaying) return playheadMs
    return com.aftermotion.studio.model.MotionTrackApply.timelineMsAt(video, video.trimStartMs + p.currentPosition)
}

@OptIn(UnstableApi::class)
@Composable
fun CanvasArea(
    height: Dp,
    aspectRatio: AspectRatio,
    playheadMs: () -> Long,
    isPlaying: Boolean,
    tracks: List<TimelineTrack>,
    selectedClipId: String? = null,
    onTogglePlayPause: () -> Unit,
    player: Player? = null,
    /** Player of one video layer (several video layers can be on the canvas at once). */
    videoPlayerFor: (String) -> Player? = { null },
    hasMainMedia: Boolean = false,
    /** True while the user is scrubbing/flinging — show the scrub frames instead of waiting on ExoPlayer. */
    isScrubbing: Boolean = false,
    /**
     * Scrub frame of a video layer (picture + the source time it belongs to; the cutout mask and the
     * tracked layers must use that time, not the playhead). Read while drawing, so a new frame
     * redraws only the layer instead of recomposing the editor.
     */
    scrubFrameOf: ((String) -> com.aftermotion.studio.engine.ScrubShown?)? = null,
    onUpdateClipTransform: (clipId: String, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    onClipSelect: (clipId: String) -> Unit = {},
    onTextDoubleTap: (clipId: String) -> Unit = {},
    /** Layer whose Color editor is open: its two gradient points are shown on the canvas. */
    gradientEditId: String? = null,
    onGradientPoints: (id: String, x1: Float, y1: Float, x2: Float, y2: Float) -> Unit = { _, _, _, _, _ -> },
    /** Bounding box handles: called once when a drag starts, then with the new scale / rotation. */
    onSelectionBegin: () -> Unit = {},
    onSelectionTransform: (id: String, scalePercent: Float, rotationDeg: Float) -> Unit = { _, _, _ -> },
    /** Real size of the canvas frame on screen (width in px + screen density): the export scales shapes / text / moves by it. */
    onPreviewFrame: (widthPx: Float, density: Float) -> Unit = { _, _ -> },
    /** Drawn on top of the canvas frame (same size as the frame), e.g. the motion-tracking circle. */
    frameOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    /** C++ preview (beta). When it is on and the project allows it, the video / photo layers are drawn by it. */
    nativePreview: com.aftermotion.studio.engine.NativePreviewEngine? = null,
    modifier: Modifier = Modifier
) {
    val canvasColor = LocalCanvasColor.current
    val nativeActive = remember(tracks, com.aftermotion.studio.engine.NativePreview.enabled, com.aftermotion.studio.engine.NativePreview.editorsOpen) {
        com.aftermotion.studio.engine.NativePreview.active(tracks)
    }
    val nativeOn = nativePreview != null && nativeActive
    val surfaceVersion = PlayerSurfaceNudge.version
    val canvasArgb = canvasColor.toArgb()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .testTag("canvas_preview_area"),
        contentAlignment = Alignment.Center
    ) {
        // Aspect-ratio bounding box for the player
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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

            val frameDensity = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(frameW, frameH) {
                onPreviewFrame(with(frameDensity) { frameW.toPx() }, frameDensity.density)
            }
            // Any visible layer with opacity < 100 % or a blend mode: the preview then uses a
            // TextureView for the video (a SurfaceView cannot take part in blending).
            val blendActive = remember(tracks, nativeOn) {
                !nativeOn && tracks.any { t -> t.clips.any { it.isVisible && it.hasBlending } }
            }
            // The C++ preview shows the picture of the playhead itself: the tracked layers follow the playhead.
            val trackPlayerFor: (String) -> Player? = if (nativeOn) ({ _: String -> null }) else videoPlayerFor
            val trackScrubOf = if (nativeOn) null else scrubFrameOf
            Box(
                modifier = Modifier
                    .size(width = frameW, height = frameH)
                    .clip(RectangleShape)
                    .background(canvasColor)
                    .then(
                        if (blendActive) {
                            // Blending needs one offscreen picture (canvas colour + video + layers)
                            // to blend against.
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .background(canvasColor)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val anyActiveClip by remember(tracks) {
                    derivedStateOf {
                        val t = playheadMs()
                        tracks.any { tr -> tr.clips.any { it.isActiveAt(t) } }
                    }
                }
                // Media layers (videos and pictures) that are on the canvas now, bottom lane first.
                val activeMediaClips by remember(tracks, isPlaying, isScrubbing, nativeOn) {
                    derivedStateOf {
                        val t = playheadMs()
                        tracks
                            .asReversed()
                            .flatMap { it.clips }
                            .filter {
                                it.type == LayerType.VIDEO && it.isVisible && it.uri != null && it.isActiveAt(t)
                            }
                            .map { c: com.aftermotion.studio.model.TimelineClip ->
                                c.renderAt(
                                    timeMs = t,
                                    trackMs = trackFrameTimeMs(c, t, tracks, isPlaying, trackPlayerFor, isScrubbing && !nativeOn, trackScrubOf)
                                )
                            }
                            // Depth: a layer at / behind the camera is gone; the larger the Z the nearer (drawn later).
                            .filter { !it.isBehindCamera }
                            .sortedBy { it.transformZ }
                    }
                }
                // Several video surfaces at once (or blending) need TextureViews: SurfaceViews
                // cannot be blended and do not stack reliably.
                val videoLayerCount = remember(tracks) {
                    tracks.sumOf { t ->
                        t.clips.count { it.type == LayerType.VIDEO && it.isVisible && it.uri != null && !it.isImage }
                    }
                }
                // A cutout layer is masked on a TextureView (a SurfaceView cannot be masked).
                val cutoutActive = remember(tracks) {
                    tracks.any { t -> t.clips.any { it.isVisible && it.cutoutDir != null } }
                }
                // Face AI blur copies the picture into a layer: also needs a TextureView.
                val faceBlurActive = remember(tracks) {
                    tracks.any { t -> t.clips.any { it.isVisible && it.faceBlurDir != null } }
                }
                val useTexture = blendActive || videoLayerCount > 1 || cutoutActive || faceBlurActive
                if (nativeOn && nativePreview != null) {
                    NativePreviewSurface(
                        engine = nativePreview,
                        tracks = tracks,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        canvasArgb = canvasArgb,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                (if (nativeOn) emptyList<com.aftermotion.studio.model.TimelineClip>() else activeMediaClips).forEach { mClip ->
                    key(mClip.id) {
                        MediaLayerOnCanvas(
                            mc = mClip,
                            player = if (mClip.isImage) null else videoPlayerFor(mClip.id),
                            useTexture = useTexture,
                            canvasArgb = canvasArgb,
                            surfaceVersion = surfaceVersion,
                            isScrubbing = isScrubbing,
                            scrubFrameOf = scrubFrameOf,
                            playheadMs = playheadMs
                        )
                    }
                }
                if (activeMediaClips.isNotEmpty()) {
                    // Tap a picture / video to select it, drag to move it (like text and shapes).
                    MediaGestureLayer(
                        clips = activeMediaClips,
                        selectedId = selectedClipId,
                        onSelect = onClipSelect,
                        onBegin = onSelectionBegin,
                        onMove = onUpdateClipTransform,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (activeMediaClips.isEmpty() && !anyActiveClip) {
                    // Clean Alight Motion style canvas with subtle center indicator
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val dotColor = Color.White.copy(alpha = 0.25f)
                            drawCircle(color = dotColor, radius = 2.dp.toPx())
                            drawLine(color = dotColor, start = Offset(size.width / 2f, 0f), end = Offset(size.width / 2f, size.height), strokeWidth = 1.dp.toPx())
                            drawLine(color = dotColor, start = Offset(0f, size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
                        }
                    }
                }

                // Render dynamic text or sticker overlays if active at current playhead
                // Lane order = stacking order: the bottom lane is drawn first, the top lane last.
                val activeOverlayClips by remember(tracks, isPlaying, isScrubbing, nativeOn) {
                    derivedStateOf {
                        val t = playheadMs()
                        tracks
                            .asReversed()
                            .flatMap { it.clips }
                            .filter { it.isVisible && (it.type == LayerType.SHAPE || it.type == LayerType.STICKER || it.text != null) && it.isActiveAt(t) }
                            .map { c: com.aftermotion.studio.model.TimelineClip ->
                                c.renderAt(
                                    timeMs = t,
                                    trackMs = trackFrameTimeMs(c, t, tracks, isPlaying, trackPlayerFor, isScrubbing && !nativeOn, trackScrubOf)
                                )
                            }
                            .filter { !it.isBehindCamera }
                            .sortedBy { it.transformZ }
                    }
                }

                val textSizes = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Size>() }
                activeOverlayClips.forEach { clip ->
                // Every layer sits in a full-canvas box that applies its opacity / blend mode.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBlend(clip.blendMode, clip.opacityFraction),
                    contentAlignment = Alignment.Center
                ) {
                  if (clip.type == LayerType.STICKER) {
                    StickerLayerOnCanvas(
                        clip = clip,
                        timeMs = playheadMs(),
                        isSelected = clip.id == selectedClipId,
                        onMove = onUpdateClipTransform,
                        onTap = onClipSelect,
                        modifier = Modifier.align(Alignment.Center)
                    )
                  } else if (clip.type != LayerType.SHAPE) {
                    TextLayerOnCanvas(
                        clip = clip,
                        frameWidth = frameW,
                        isSelected = clip.id == selectedClipId,
                        onMove = onUpdateClipTransform,
                        onTap = onClipSelect,
                        onDoubleTap = onTextDoubleTap,
                        modifier = Modifier.align(Alignment.Center),
                        onLayoutSize = { id, w, h -> textSizes[id] = androidx.compose.ui.geometry.Size(w, h) },
                        overlay = {
                            if (clip.id == gradientEditId && clip.fillMode == com.aftermotion.studio.model.FILL_GRADIENT) {
                                GradientHandleLayer(
                                    clip = clip,
                                    onPoints = onGradientPoints,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                    )
                  } else {
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
                    val isSelected = clip.id == selectedClipId
                    val latestShape by androidx.compose.runtime.rememberUpdatedState(clip)

                    val effectiveTransX = clip.depthX
                    val effectiveTransY = clip.depthY
                    val effectiveScale = clip.effectiveScalePercent / 100f
                    val baseSize = 130.dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                translationX = effectiveTransX
                                translationY = effectiveTransY
                                scaleX = effectiveScale.coerceAtLeast(0.01f) * (if (clip.isFlippedH) -1f else 1f)
                                scaleY = effectiveScale.coerceAtLeast(0.01f) * (if (clip.isFlippedV) -1f else 1f)
                                rotationZ = clip.rotationDeg
                                rotationX = clip.rotation3dX
                                rotationY = clip.rotation3dY
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(clip.pivotX, clip.pivotY)
                                cameraDistance = 12f * density
                            }
                            .size(baseSize)
                            .pointerInput(clip.id) {
                                var currentDragX = 0f
                                var currentDragY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        currentDragX = latestShape.transformX
                                        currentDragY = latestShape.transformY
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // The finger moves in the layer's own space (scaled, rotated and
                                        // maybe flipped): turn it back into canvas pixels.
                                        val l = latestShape
                                        val rad = Math.toRadians(l.rotationDeg.toDouble())
                                        val c = Math.cos(rad).toFloat()
                                        val sn = Math.sin(rad).toFloat()
                                        val sc = (l.scalePercent / 100f).coerceAtLeast(0.01f) // stored moves are before the depth factor
                                        val sx = sc * (if (l.isFlippedH) -1f else 1f)
                                        val sy = sc * (if (l.isFlippedV) -1f else 1f)
                                        currentDragX += dragAmount.x * sx * c - dragAmount.y * sy * sn
                                        currentDragY += dragAmount.x * sx * sn + dragAmount.y * sy * c
                                        onUpdateClipTransform(l.id, currentDragX, currentDragY)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().cropMask(clip)) {
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
                                        val cX = size.width / 2f
                                        val cY = size.height / 2f
                                        val outer = size.minDimension / 2f
                                        val isStar = pattern == "STAR"
                                        val pointCount = if (isStar) 10 else 5
                                        val inner = outer * 0.45f
                                        for (i in 0 until pointCount) {
                                            val angle = Math.toRadians((i * 360.0 / pointCount) - 90.0)
                                            val radius = if (isStar && i % 2 == 1) inner else outer
                                            val px = (cX + radius * Math.cos(angle)).toFloat()
                                            val py = (cY + radius * Math.sin(angle)).toFloat()
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

                        if (clip.id == gradientEditId && clip.fillMode == com.aftermotion.studio.model.FILL_GRADIENT) {
                            GradientHandleLayer(clip = clip, onPoints = onGradientPoints)
                        }

                    }
                }
                }
                }

                // Bounding box of the selected shape / text layer (Alight Motion style).
                val selOverlay = activeOverlayClips.find { it.id == selectedClipId }
                if (selOverlay != null && selOverlay.id != gradientEditId) {
                    val dens = androidx.compose.ui.platform.LocalDensity.current.density
                    val isShape = selOverlay.type == LayerType.SHAPE || selOverlay.type == LayerType.STICKER
                    val size = if (isShape) androidx.compose.ui.geometry.Size(130f * dens, 130f * dens)
                    else textSizes[selOverlay.id]
                    if (size != null && size.width > 0f && size.height > 0f) {
                        SelectionFrameOverlay(
                            clip = selOverlay,
                            layerWidthPx = size.width,
                            layerHeightPx = size.height,
                            minScale = 0.01f,
                            onBegin = onSelectionBegin,
                            onTransform = onSelectionTransform,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Main Track Framing Box & Center Crosshair Target
                val selectedClipType = tracks.flatMap { it.clips }.firstOrNull { it.id == selectedClipId }?.type
                val isMainTrackSelected = selectedClipType == LayerType.VIDEO
                if (isMainTrackSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) {
                            val greenNeon = Color(0xFF00E676)
                            val strokeW = 2.dp.toPx()
                            val cX = size.width / 2f
                            val cY = size.height / 2f

                            // Center dot
                            drawCircle(color = greenNeon, radius = 2.dp.toPx())

                            // 4 Crosshair lines
                            drawLine(color = greenNeon, start = Offset(cX, 0f), end = Offset(cX, 7.dp.toPx()), strokeWidth = strokeW)
                            drawLine(color = greenNeon, start = Offset(cX, size.height - 7.dp.toPx()), end = Offset(cX, size.height), strokeWidth = strokeW)
                            drawLine(color = greenNeon, start = Offset(0f, cY), end = Offset(7.dp.toPx(), cY), strokeWidth = strokeW)
                            drawLine(color = greenNeon, start = Offset(size.width - 7.dp.toPx(), cY), end = Offset(size.width, cY), strokeWidth = strokeW)
                        }
                    }
                }

            }

            // Bounding box + corner / rotate handles of the selected picture / video layer.
            // Drawn outside the clipped canvas, so handles at the canvas edge can still be grabbed.
            val selMedia by remember(tracks, selectedClipId, isPlaying, isScrubbing, nativeOn) {
                derivedStateOf {
                    val t = playheadMs()
                    tracks.flatMap { it.clips }.firstOrNull {
                        it.id == selectedClipId && it.type == LayerType.VIDEO &&
                            it.isVisible && it.uri != null && it.isActiveAt(t)
                    }?.let { c: com.aftermotion.studio.model.TimelineClip ->
                        c.renderAt(
                            timeMs = t,
                            trackMs = trackFrameTimeMs(c, t, tracks, isPlaying, trackPlayerFor, isScrubbing && !nativeOn, trackScrubOf)
                        )
                    }
                }
            }
            val mediaSel = selMedia
            if (mediaSel != null && mediaSel.id != gradientEditId) {
                val d = androidx.compose.ui.platform.LocalDensity.current
                val fwPx = with(d) { frameW.toPx() }
                val fhPx = with(d) { frameH.toPx() }
                val box = mediaVisibleSize(mediaSel, fwPx, fhPx)
                SelectionFrameOverlay(
                    // Flip mirrors the picture inside its box, so the box itself stays the same.
                    clip = mediaSel.copy(isFlippedH = false, isFlippedV = false),
                    layerWidthPx = box.width,
                    layerHeightPx = box.height,
                    minScale = 0.01f,
                    onBegin = onSelectionBegin,
                    onTransform = onSelectionTransform,
                    modifier = Modifier.fillMaxSize(),
                    pivotRefW = fwPx,
                    pivotRefH = fhPx
                )
            }
            if (frameOverlay != null) {
                Box(modifier = Modifier.size(width = frameW, height = frameH)) { this.frameOverlay() }
            }
            if (nativePreview != null) {
                // Beta switch of the C++ preview (blend / cutout / face blur use the old preview for now).
                val enabled = com.aftermotion.studio.engine.NativePreview.enabled
                androidx.compose.material3.Text(
                    text = if (!enabled) "C++ preview: off" else if (nativeOn) "C++ preview: ON" else "C++ preview: fallback",
                    color = Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { com.aftermotion.studio.engine.NativePreview.setNativeEnabled(!enabled) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** Size (px) of the visible part of a media layer: the picture fitted into the canvas, after crop. */
internal fun mediaVisibleSize(mc: com.aftermotion.studio.model.TimelineClip, fw: Float, fh: Float): androidx.compose.ui.geometry.Size {
    val cW = (mc.cropWidthPercent / 100f).coerceIn(0.05f, 1f)
    val cH = (mc.cropHeightPercent / 100f).coerceIn(0.05f, 1f)
    val cropActive = cW < 0.999f || cH < 0.999f
    val a = if (mc.sourceAspect > 0f) mc.sourceAspect else fw / fh
    val contentW = if (a > fw / fh) fw else fh * a
    val contentH = if (a > fw / fh) fw / a else fh
    if (!cropActive) return androidx.compose.ui.geometry.Size(contentW, contentH)
    val s = minOf(fw / (cW * contentW), fh / (cH * contentH))
    return androidx.compose.ui.geometry.Size(cW * contentW * s, cH * contentH * s)
}

/**
 * Invisible touch layer above the media layers: finds which picture / video is under the finger
 * (the selected one first, else the top one), selects it and moves it while dragging.
 * Text and shape layers sit above this layer and keep their own gestures.
 */
@Composable
private fun MediaGestureLayer(
    clips: List<com.aftermotion.studio.model.TimelineClip>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBegin: () -> Unit,
    onMove: (id: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var fw by remember { mediaMutableFloatStateOf(1f) }
    var fh by remember { mediaMutableFloatStateOf(1f) }
    val clipsNow by mediaRememberUpdatedState(clips)
    val selNow by mediaRememberUpdatedState(selectedId)
    val selectNow by mediaRememberUpdatedState(onSelect)
    val beginNow by mediaRememberUpdatedState(onBegin)
    val moveNow by mediaRememberUpdatedState(onMove)

    Box(
        modifier = modifier
            .mediaOnSizeChanged { fw = it.width.toFloat(); fh = it.height.toFloat() }
            .mediaPointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    fun geo(c: com.aftermotion.studio.model.TimelineClip): FrameGeo {
                        val b = mediaVisibleSize(c, fw, fh)
                        return FrameGeo(
                            c.copy(isFlippedH = false, isFlippedV = false),
                            b.width, b.height, fw, fh, 0.05f, fw, fh
                        )
                    }
                    val list = clipsNow
                    val sel = list.find { it.id == selNow }
                    val hit = if (sel != null && geo(sel).contains(down.position)) sel
                    else list.lastOrNull { geo(it).contains(down.position) }
                    if (hit == null) return@awaitEachGesture

                    if (hit.id != selNow) selectNow(hit.id)
                    if (hit.isLocked) return@awaitEachGesture

                    var tx = hit.transformX
                    var ty = hit.transformY
                    // The picture is drawn at (stored move x depth factor): finger px -> stored move.
                    val depthF = hit.depthFactor
                    var total = androidx.compose.ui.geometry.Offset.Zero
                    var dragging = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed || c.isConsumed) break
                        val d = c.positionChange()
                        if (!dragging) {
                            total += d
                            if (total.getDistance() > slop) {
                                dragging = true
                                beginNow() // one undo step for the whole drag
                                tx += total.x / depthF
                                ty += total.y / depthF
                                moveNow(hit.id, tx, ty)
                                c.consume()
                            }
                        } else {
                            tx += d.x / depthF
                            ty += d.y / depthF
                            moveNow(hit.id, tx, ty)
                            c.consume()
                        }
                    }
                }
            }
    )
}

/**
 * One media layer (video or picture) on the canvas: its own opacity / blend mode, transform,
 * flip and crop, exactly like the export. Video layers show their own player.
 */
@Composable
internal fun MediaLayerOnCanvas(
    mc: com.aftermotion.studio.model.TimelineClip,
    player: Player?,
    useTexture: Boolean,
    canvasArgb: Int,
    surfaceVersion: Any?,
    isScrubbing: Boolean = false,
    scrubFrameOf: ((String) -> com.aftermotion.studio.engine.ScrubShown?)? = null,
    /** Playhead (read while drawing): picks the cutout mask frame. Null = no cutout mask. */
    playheadMs: (() -> Long)? = null
) {
    // Cutout: the person's mask of the current frame is applied to the layer's picture (DstIn).
    val maskModifier = if (mc.cutoutDir != null && playheadMs != null && !mc.isImage) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Mask of the frame that is really on screen: the preview player runs on its own and
                // can be a little behind the playhead, so its own position is used (the playhead
                // only while scrubbing or when the player is not ready).
                val t = playheadMs()
                val shown = if (isScrubbing) scrubFrameOf?.invoke(mc.id) else null
                val srcMs = if (shown != null) shown.srcMs
                else if (player != null && player.playbackState == Player.STATE_READY
                ) mc.trimStartMs + player.currentPosition
                else com.aftermotion.studio.engine.CutoutMasks.sourceMs(mc, t)
                val bmp = com.aftermotion.studio.engine.CutoutMasks.bitmap(mc, srcMs)
                if (bmp != null) {
                    val a = bmp.width.toFloat() / bmp.height
                    val boxA = size.width / size.height
                    val cw = if (a > boxA) size.width else size.height * a
                    val ch = if (a > boxA) size.width / a else size.height
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            ((size.width - cw) / 2f).toInt(), ((size.height - ch) / 2f).toInt()
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1)),
                        colorFilter = CUTOUT_MASK_FILTER,
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
            }
    } else Modifier
    // Face AI blur (preview): a small blurred copy of the picture, shown through the face mask of the
    // frame on screen (see FaceBlurPreview: works on every Android version).
    val faceBlurOn = mc.faceBlurDir != null && playheadMs != null && !mc.isImage
    val scratch = remember { com.aftermotion.studio.engine.FaceBlurScratch() }
    val faceBlurModifier = if (faceBlurOn) {
        Modifier.drawWithContent {
            drawContent()
            val t = playheadMs!!.invoke()
            val shown = if (isScrubbing) scrubFrameOf?.invoke(mc.id) else null
            val srcMs = if (shown != null) shown.srcMs
            else if (player != null && player.playbackState == Player.STATE_READY
            ) mc.trimStartMs + player.currentPosition
            else com.aftermotion.studio.engine.FaceBlurMasks.sourceMs(mc, t)
            val bmp = com.aftermotion.studio.engine.FaceBlurMasks.bitmap(mc, srcMs)
            if (bmp == null) {
                scratch.status = "no mask file"
            } else if (size.width > 0f && size.height > 0f) {
                val a = bmp.width.toFloat() / bmp.height
                val boxA = size.width / size.height
                val cw = if (a > boxA) size.width else size.height * a
                val ch = if (a > boxA) size.width / a else size.height
                val sw = com.aftermotion.studio.engine.FaceBlurPreview.SMALL_WIDTH
                val sh = (sw * ch / cw).toInt().coerceAtLeast(8)
                val patch = com.aftermotion.studio.engine.FaceBlurPreview.render(
                    scratch, bmp, shown?.bitmap, sw, sh, mc.faceBlurStrength
                )
                if (patch != null) {
                    drawImage(
                        image = patch.asImageBitmap(),
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            ((size.width - cw) / 2f).toInt(), ((size.height - ch) / 2f).toInt()
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1))
                    )
                }
            }
            if (com.aftermotion.studio.engine.FaceBlurPreview.DEBUG) {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 26f
                    setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
                }
                drawContext.canvas.nativeCanvas.drawText("Face blur: " + scratch.status, 12f, 36f, paint)
            }
        }
    } else Modifier
    val clipScale = mc.effectiveScalePercent / 100f
    val cW = (mc.cropWidthPercent / 100f).coerceIn(0.05f, 1f)
    val cH = (mc.cropHeightPercent / 100f).coerceIn(0.05f, 1f)
    val cL = (1f - cW) * (mc.cropHorizontalPercent / 100f)
    val cT = (1f - cH) * (mc.cropVerticalPercent / 100f)
    val cropActive = cW < 0.999f || cH < 0.999f
    val srcAspect = mc.sourceAspect

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBlend(mc.blendMode, mc.opacityFraction)
            .graphicsLayer {
                scaleX = clipScale
                scaleY = clipScale
                translationX = mc.depthX
                translationY = mc.depthY
                rotationZ = mc.rotationDeg
                rotationX = mc.rotation3dX
                rotationY = mc.rotation3dY
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(mc.pivotX, mc.pivotY)
                cameraDistance = 12f * density
            }
    ) {
        // Crop: fit the cropped region into the frame (same as export), clipped to the region.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Flip H / V mirror the picture (after the crop, like the export).
                    val fx = if (mc.isFlippedH) -1f else 1f
                    val fy = if (mc.isFlippedV) -1f else 1f
                    scaleX = fx
                    scaleY = fy
                    if (cropActive) {
                        val fw = size.width
                        val fh = size.height
                        val a = if (srcAspect > 0f) srcAspect else fw / fh
                        val contentW = if (a > fw / fh) fw else fh * a
                        val contentH = if (a > fw / fh) fw / a else fh
                        val s = minOf(fw / (cW * contentW), fh / (cH * contentH))
                        scaleX = s * fx
                        scaleY = s * fy
                        translationX = -((cL + cW / 2f) - 0.5f) * contentW * s * fx
                        translationY = -((cT + cH / 2f) - 0.5f) * contentH * s * fy
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (cropActive) {
                            clip = true
                            shape = object : androidx.compose.ui.graphics.Shape {
                                override fun createOutline(
                                    size: androidx.compose.ui.geometry.Size,
                                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                    density: androidx.compose.ui.unit.Density
                                ): androidx.compose.ui.graphics.Outline {
                                    val a = if (srcAspect > 0f) srcAspect else size.width / size.height
                                    val contentW = if (a > size.width / size.height) size.width else size.height * a
                                    val contentH = if (a > size.width / size.height) size.width / a else size.height
                                    val x0 = (size.width - contentW) / 2f
                                    val y0 = (size.height - contentH) / 2f
                                    return androidx.compose.ui.graphics.Outline.Rectangle(
                                        androidx.compose.ui.geometry.Rect(
                                            x0 + cL * contentW, y0 + cT * contentH,
                                            x0 + (cL + cW) * contentW, y0 + (cT + cH) * contentH
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .then(maskModifier)
                    .then(faceBlurModifier)
            ) {
                if (mc.isImage) {
                    coil.compose.AsyncImage(
                        model = mc.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (player != null) {
                    key(useTexture) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                (if (useTexture) {
                                    android.view.LayoutInflater.from(ctx)
                                        .inflate(R.layout.player_texture, null) as PlayerView
                                } else PlayerView(ctx)).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    this.player = player
                                    scratch.view = this
                                }
                            },
                            update = {
                                if (it.player !== player) it.player = player
                                if (surfaceVersion != it.getTag(0x7f0a0001)) {
                                    // A full-screen editor had the video surface: attach the player again.
                                    it.player = null
                                    it.player = player
                                    it.setTag(0x7f0a0001, surfaceVersion)
                                }
                            },
                            onRelease = {
                                it.player = null
                                PlayerSurfaceNudge.bump()
                            }
                        )
                    }
                    if (isScrubbing && scrubFrameOf != null) {
                        // The scrub frame is read while drawing: a new frame redraws this canvas only,
                        // nothing is recomposed (the old Image(bitmap) recomposed on every frame).
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val bmp = scrubFrameOf.invoke(mc.id)?.bitmap
                            if (bmp != null && !bmp.isRecycled && size.width > 0f && size.height > 0f) {
                                val a = bmp.width.toFloat() / bmp.height
                                val boxA = size.width / size.height
                                val cw = if (a > boxA) size.width else size.height * a
                                val ch = if (a > boxA) size.width / a else size.height
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(
                                        ((size.width - cw) / 2f).toInt(), ((size.height - ch) / 2f).toInt()
                                    ),
                                    dstSize = androidx.compose.ui.unit.IntSize(
                                        cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * All the given media layers (bottom first) stacked in one offscreen picture, so blend modes see
 * what lies beneath. Used by the full-screen editors that show the canvas themselves.
 */
@Composable
internal fun MediaLayersStack(
    layers: List<com.aftermotion.studio.model.TimelineClip>,
    videoPlayerFor: (String) -> Player?,
    useTexture: Boolean,
    modifier: Modifier = Modifier
) {
    val canvasColor = com.aftermotion.studio.ui.theme.LocalCanvasColor.current
    // A full-screen editor shows the video through the Media3 players: the C++ preview steps aside meanwhile.
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.aftermotion.studio.engine.NativePreview.editorsOpen++
        onDispose { com.aftermotion.studio.engine.NativePreview.editorsOpen-- }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(canvasColor)
    ) {
        layers.forEach { layer ->
            key(layer.id) {
                MediaLayerOnCanvas(
                    mc = layer,
                    player = if (layer.isImage) null else videoPlayerFor(layer.id),
                    useTexture = useTexture,
                    canvasArgb = 0,
                    surfaceVersion = PlayerSurfaceNudge.version
                )
            }
        }
    }
}

/**
 * Sticker layer on the preview canvas: the same 130 dp box and the same move / scale / rotate
 * placement as a shape layer. Animated stickers show the frame for the playhead time, so the
 * preview matches the export frame for frame.
 */
@Composable
private fun StickerLayerOnCanvas(
    clip: TimelineClip,
    timeMs: Long,
    isSelected: Boolean,
    onMove: (clipId: String, offsetX: Float, offsetY: Float) -> Unit,
    onTap: (clipId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val latest by androidx.compose.runtime.rememberUpdatedState(clip)
    val frame = remember(clip.uri, timeMs - clip.startMs) {
        StickerLibrary.frameFor(clip.uri, clip.sourceOffsetMs(timeMs - clip.startMs))?.asImageBitmap()
    }
    val effectiveScale = clip.effectiveScalePercent / 100f

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = clip.depthX
                translationY = clip.depthY
                scaleX = effectiveScale.coerceAtLeast(0.01f) * (if (clip.isFlippedH) -1f else 1f)
                scaleY = effectiveScale.coerceAtLeast(0.01f) * (if (clip.isFlippedV) -1f else 1f)
                rotationZ = clip.rotationDeg
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(clip.pivotX, clip.pivotY)
                cameraDistance = 12f * density
            }
            .size(130.dp)
            .pointerInput(clip.id) {
                detectTapGestures(onTap = { onTap(clip.id) })
            }
            .pointerInput(clip.id) {
                var currentDragX = 0f
                var currentDragY = 0f
                detectDragGestures(
                    onDragStart = {
                        currentDragX = latest.transformX
                        currentDragY = latest.transformY
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val l = latest
                        val rad = Math.toRadians(l.rotationDeg.toDouble())
                        val c = Math.cos(rad).toFloat()
                        val sn = Math.sin(rad).toFloat()
                        val sc = (l.scalePercent / 100f).coerceAtLeast(0.01f) // stored moves are before the depth factor
                        val sx = sc * (if (l.isFlippedH) -1f else 1f)
                        val sy = sc * (if (l.isFlippedV) -1f else 1f)
                        currentDragX += dragAmount.x * sx * c - dragAmount.y * sy * sn
                        currentDragY += dragAmount.x * sx * sn + dragAmount.y * sy * c
                        onMove(l.id, currentDragX, currentDragY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = clip.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}


/** Takes the red channel of the (grey) mask picture as its alpha, for the DstIn cutout. */
private val CUTOUT_MASK_FILTER = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f
        )
    )
)
