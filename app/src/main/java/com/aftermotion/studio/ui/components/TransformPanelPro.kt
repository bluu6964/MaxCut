package com.aftermotion.studio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.model.AnimProp
import com.aftermotion.studio.model.Easing
import com.aftermotion.studio.model.TimelineClip
import com.aftermotion.studio.model.indexOnTime
import com.aftermotion.studio.model.segmentIndexAt
import com.aftermotion.studio.ui.theme.ContinuousRoundedShape
import com.aftermotion.studio.ui.util.appleSnap
import com.aftermotion.studio.ui.util.appleSpring
import com.aftermotion.studio.ui.util.bouncyClickable
import kotlin.math.roundToInt

private class ProRailItem(
    val icon: ImageVector,
    val badge: String?,
    val description: String,
    val style: GlowStyle
)

private val pro2d = listOf(
    ProRailItem(Icons.Default.OpenWith, null, "Position", GlowStyles.Indigo),
    ProRailItem(Icons.Default.OpenInFull, null, "Scale", GlowStyles.Mint),
    ProRailItem(Icons.Default.RotateRight, null, "Rotation", GlowStyles.Amber)
)
private val pro3d = listOf(
    ProRailItem(Icons.Default.OpenWith, "Z", "Depth", GlowStyles.Aqua),
    ProRailItem(Icons.Default.RotateRight, "X", "Rotate X", GlowStyles.Rose),
    ProRailItem(Icons.Default.RotateRight, "Y", "Rotate Y", GlowStyles.Violet)
)

/**
 * Apple-style Transform panel: dark glass sheet, glowing coloured tool tiles (one colour per
 * property), big live value, glass keyframe / easing buttons, orb position pad, pill ruler and a
 * sliding 2D | 3D segmented control. Every change springs in.
 */
@Composable
internal fun TransformBottomPanel(
    panelOnly: Boolean,
    clip: TimelineClip?,
    currentPlayheadMs: Long,
    activeTab: TransformTab,
    onTabChange: (TransformTab) -> Unit,
    part2d: Transform2DPart,
    onPart2d: (Transform2DPart) -> Unit,
    part3d: Transform3DPart,
    onPart3d: (Transform3DPart) -> Unit,
    horizontal: Float,
    vertical: Float,
    scale: Float,
    rotation: Float,
    rot3dX: Float,
    rot3dY: Float,
    posZ: Float,
    onPosition: (Float, Float) -> Unit,
    onScale: (Float) -> Unit,
    onRotation: (Float) -> Unit,
    onPosZ: (Float) -> Unit,
    onRotX: (Float) -> Unit,
    onRotY: (Float) -> Unit,
    onDismiss: () -> Unit,
    onToggleKeyframe: (AnimProp) -> Unit,
    onEasingBegin: () -> Unit,
    onEasingChange: (AnimProp, Long, Easing) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    val headerText = when (activeTab) {
        TransformTab.TWO_D -> when (part2d) {
            Transform2DPart.POSITION -> "X ${horizontal.roundToInt()}   Y ${vertical.roundToInt()}"
            Transform2DPart.SCALE -> "${scale.roundToInt()}%"
            Transform2DPart.ROTATION -> "${rotation.roundToInt()}°"
        }
        TransformTab.THREE_D -> when (part3d) {
            Transform3DPart.POSITION_Z -> "${posZ.roundToInt()}"
            Transform3DPart.ROTATION_X -> "${rot3dX.roundToInt()}°"
            Transform3DPart.ROTATION_Y -> "${rot3dY.roundToInt()}°"
        }
    }
    val currentItem = when (activeTab) {
        TransformTab.TWO_D -> pro2d[part2d.ordinal]
        TransformTab.THREE_D -> pro3d[part3d.ordinal]
    }
    val curProp = when (activeTab) {
        TransformTab.TWO_D -> when (part2d) {
            Transform2DPart.POSITION -> AnimProp.POSITION
            Transform2DPart.SCALE -> AnimProp.SCALE
            Transform2DPart.ROTATION -> AnimProp.ROTATION
        }
        TransformTab.THREE_D -> when (part3d) {
            Transform3DPart.POSITION_Z -> AnimProp.POS_Z
            Transform3DPart.ROTATION_X -> AnimProp.ROT_X
            Transform3DPart.ROTATION_Y -> AnimProp.ROT_Y
        }
    }
    val accent by animateColorAsState(currentItem.style.glow, tween(260), label = "panelAccent")

    val relMs = currentPlayheadMs - (clip?.startMs ?: 0L)
    val kfList = clip?.keyframes?.get(curProp).orEmpty()
    val insideClip = clip != null && currentPlayheadMs >= clip.startMs && currentPlayheadMs <= clip.endMs
    val onKeyframe = kfList.indexOnTime(relMs) >= 0
    val segIdx = kfList.segmentIndexAt(relMs)
    val easingEnabled = segIdx >= 0
    var showEasing by remember { mutableStateOf(false) }
    LaunchedEffect(easingEnabled, curProp) { if (!easingEnabled) showEasing = false }
    LaunchedEffect(curProp) { showEasing = false }
    val segStart = if (segIdx >= 0) kfList[segIdx] else null
    val segEnd = if (segIdx >= 0) kfList[segIdx + 1] else null

    Column(
        modifier = modifier
            .clip(sheetShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1C22), Color(0xFF0D0D10))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                sheetShape
            )
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 8.dp)
            .testTag("transform_panel")
    ) {
        // Grabber
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(38.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(6.dp))

        // ── Header: back | property + live value | keyframe | easing ───────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (panelOnly) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyClickable(pressedScale = 0.9f, onClick = onDismiss)
                        .glassSurface(ContinuousRoundedShape(14.dp))
                        .testTag("panel_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                Spacer(Modifier.width(40.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentItem.description.uppercase(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = headerText,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
            }

            // Keyframe diamond
            val kfScale by animateFloatAsState(
                targetValue = if (onKeyframe) 1.12f else 0.92f,
                animationSpec = appleSpring(),
                label = "kfScale"
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { alpha = if (insideClip) 1f else 0.35f }
                    .bouncyClickable(pressedScale = 0.88f, onClick = { if (insideClip) clip?.let { onToggleKeyframe(curProp) } })
                    .glassSurface(ContinuousRoundedShape(14.dp))
                    .testTag("transform_keyframe_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = 45f; scaleX = kfScale; scaleY = kfScale }
                        .background(
                            if (onKeyframe) accent else Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(4.dp)
                        )
                        .then(
                            if (kfList.isNotEmpty() && !onKeyframe) Modifier.border(1.5.dp, accent, RoundedCornerShape(4.dp))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!onKeyframe) {
                        Icon(
                            Icons.Default.Add, "Keyframe", tint = Color.White,
                            modifier = Modifier.size(11.dp).graphicsLayer { rotationZ = -45f }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            // Easing curve (only between two keyframes)
            val easeOpen = showEasing && easingEnabled
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .bouncyClickable(pressedScale = 0.88f, onClick = { if (easingEnabled) showEasing = !showEasing })
                    .glassSurface(
                        ContinuousRoundedShape(14.dp),
                        tint = if (easeOpen) accent else Color.White,
                        strength = if (easeOpen) 1.6f else 1f
                    )
                    .testTag("transform_easing_button"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    drawEasingIcon(
                        easing = if (easingEnabled) segStart?.easing else null,
                        color = when {
                            !easingEnabled -> Color.White.copy(alpha = 0.25f)
                            easeOpen -> accent
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Body ───────────────────────────────────────────────────────────────────────
        val bodyMod = if (panelOnly) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(116.dp)
        if (showEasing && segStart != null && segEnd != null) {
            Box(modifier = bodyMod) {
                EasingEditor(
                    easing = segStart.easing,
                    progress = ((relMs - segStart.timeMs).toFloat() /
                        (segEnd.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                    onBegin = onEasingBegin,
                    onChange = { e -> onEasingChange(curProp, segStart.timeMs, e) }
                )
            }
        } else AnimatedContent(
            targetState = activeTab,
            modifier = bodyMod,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(0.85f, 400f)) { dir * it / 4 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(180)) { -dir * it / 4 } + fadeOut(tween(120)))
            },
            label = "transformTab"
        ) { tab ->
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                when (tab) {
                    TransformTab.TWO_D -> {
                        ProRail(items = pro2d, selectedIndex = part2d.ordinal, onSelect = { onPart2d(Transform2DPart.values()[it]) })
                        Spacer(Modifier.width(10.dp))
                        AnimatedContent(
                            targetState = part2d,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                (fadeIn(tween(200, delayMillis = 60)) + scaleIn(spring(0.85f, 400f), initialScale = 0.94f)) togetherWith
                                    fadeOut(tween(100))
                            },
                            label = "part2d"
                        ) { part ->
                            when (part) {
                                Transform2DPart.POSITION -> ProPositionPad(
                                    x = horizontal, y = vertical,
                                    accent = GlowStyles.Indigo.glow,
                                    onChange = onPosition
                                )
                                Transform2DPart.SCALE -> RulerDialSlider(
                                    label = "", labelWidth = 0.dp, showValue = false,
                                    value = scale, onValueChange = onScale,
                                    min = TRANSFORM_SCALE_MIN, max = TRANSFORM_SCALE_MAX,
                                    unit = "", isPercentage = true,
                                    accent = GlowStyles.Mint.glow, pillStyle = true
                                )
                                Transform2DPart.ROTATION -> RulerDialSlider(
                                    label = "", labelWidth = 0.dp, showValue = false,
                                    value = rotation, onValueChange = onRotation,
                                    min = -TRANSFORM_BIG, max = TRANSFORM_BIG,
                                    unit = "°", isPercentage = false,
                                    accent = GlowStyles.Amber.glow, pillStyle = true
                                )
                            }
                        }
                    }
                    TransformTab.THREE_D -> {
                        ProRail(items = pro3d, selectedIndex = part3d.ordinal, onSelect = { onPart3d(Transform3DPart.values()[it]) })
                        Spacer(Modifier.width(10.dp))
                        AnimatedContent(
                            targetState = part3d,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                (fadeIn(tween(200, delayMillis = 60)) + scaleIn(spring(0.85f, 400f), initialScale = 0.94f)) togetherWith
                                    fadeOut(tween(100))
                            },
                            label = "part3d"
                        ) { part ->
                            when (part) {
                                Transform3DPart.POSITION_Z -> RulerDialSlider(
                                    label = "", labelWidth = 0.dp, showValue = false,
                                    value = posZ, onValueChange = onPosZ,
                                    min = -Z_RANGE_MIN, max = Z_RANGE_MAX,
                                    unit = "", isPercentage = false,
                                    accent = GlowStyles.Aqua.glow, pillStyle = true
                                )
                                Transform3DPart.ROTATION_X -> RulerDialSlider(
                                    label = "", labelWidth = 0.dp, showValue = false,
                                    value = rot3dX, onValueChange = onRotX,
                                    min = -TRANSFORM_BIG, max = TRANSFORM_BIG,
                                    unit = "°", isPercentage = false,
                                    accent = GlowStyles.Rose.glow, pillStyle = true
                                )
                                Transform3DPart.ROTATION_Y -> RulerDialSlider(
                                    label = "", labelWidth = 0.dp, showValue = false,
                                    value = rot3dY, onValueChange = onRotY,
                                    min = -TRANSFORM_BIG, max = TRANSFORM_BIG,
                                    unit = "°", isPercentage = false,
                                    accent = GlowStyles.Violet.glow, pillStyle = true
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        ProSegmented(selected = activeTab, onSelect = onTabChange)
    }
}

/** Vertical glass capsule of tool tiles. The chosen tile lights up in its own colour (spring). */
@Composable
private fun ProRail(
    items: List<ProRailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(54.dp)
            .fillMaxHeight()
            .glassSurface(ContinuousRoundedShape(22.dp), strength = 0.7f)
            .padding(vertical = 5.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { i, item ->
            val sel by animateFloatAsState(
                targetValue = if (i == selectedIndex) 1f else 0f,
                animationSpec = appleSpring(),
                label = "railSel$i"
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .bouncyClickable(pressedScale = 0.88f, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center
            ) {
                GlowTile(
                    style = item.style,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            val s = 0.6f + 0.4f * sel
                            scaleX = s
                            scaleY = s
                            alpha = sel.coerceIn(0f, 1f)
                        }
                )
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.description,
                    tint = Color.White.copy(alpha = 0.55f + 0.45f * sel.coerceIn(0f, 1f)),
                    modifier = Modifier.size(21.dp)
                )
                if (item.badge != null) {
                    Text(
                        text = item.badge,
                        color = Color.White.copy(alpha = 0.55f + 0.45f * sel.coerceIn(0f, 1f)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/** Sliding 2D | 3D control: a light glass pill glides under the labels with a soft spring. */
@Composable
private fun ProSegmented(
    selected: TransformTab,
    onSelect: (TransformTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = TransformTab.values()
    val frac by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = appleSpring(),
        label = "segFrac"
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .glassSurface(ContinuousRoundedShape(18.dp), strength = 0.8f)
            .padding(3.dp)
    ) {
        val segW = maxWidth / tabs.size
        Box(
            modifier = Modifier
                .offset { IntOffset((segW.toPx() * frac).roundToInt(), 0) }
                .width(segW)
                .fillMaxHeight()
                .clip(ContinuousRoundedShape(15.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF7F7FA), Color(0xFFDADAE2))))
        )
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isSel = tab == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSel) Color(0xFF111114) else Color.White.copy(alpha = 0.85f),
                    animationSpec = tween(200),
                    label = "segText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bouncyClickable(pressedScale = 0.96f, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 2D position pad: dotted grid, a glowing orb that swells while touched. Same control as before
 * (1 px of finger = 1 unit), only the look and feel changed.
 */
@Composable
private fun ProPositionPad(
    x: Float,
    y: Float,
    accent: Color,
    onChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val curX by rememberUpdatedState(x)
    val curY by rememberUpdatedState(y)
    val change by rememberUpdatedState(onChange)
    val range = 100000f
    val visualRange = 500f
    var pressed by remember { mutableStateOf(false) }
    val orb by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = appleSnap(),
        label = "orb"
    )
    val padShape = ContinuousRoundedShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 2.dp)
            .clip(padShape)
            .background(
                Brush.radialGradient(listOf(Color(0xFF22222B), Color(0xFF101014)))
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.12f))
                ),
                padShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
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
                    pressed = false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val grid = 22.dp.toPx()
            val dot = 1.2.dp.toPx()
            var gx = (w / 2f) % grid
            while (gx < w) {
                var gy = (h / 2f) % grid
                while (gy < h) {
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = dot, center = Offset(gx, gy))
                    gy += grid
                }
                gx += grid
            }
            // centre cross
            drawLine(Color.White.copy(alpha = 0.18f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx())

            val r = 10.dp.toPx() * orb
            val dx = (w / 2f + (x / visualRange) * (w / 2f)).coerceIn(r, w - r)
            val dy = (h / 2f + (y / visualRange) * (h / 2f)).coerceIn(r, h - r)
            val c = Offset(dx, dy)
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 3.2f),
                radius = r * 3.2f,
                center = c
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, accent),
                    center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                    radius = r * 1.3f
                ),
                radius = r,
                center = c
            )
            drawCircle(Color.White.copy(alpha = 0.55f), radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}
