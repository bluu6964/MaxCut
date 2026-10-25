package com.aftermotion.studio.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import com.aftermotion.studio.model.segmentIndexAt
import com.aftermotion.studio.model.indexOnTime
import com.aftermotion.studio.model.TimelineClip
import com.aftermotion.studio.model.Easing
import com.aftermotion.studio.model.AnimProp
import com.aftermotion.studio.model.MIN_SPEED
import com.aftermotion.studio.model.MAX_SPEED
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.ui.theme.EditorSurface
import com.aftermotion.studio.ui.theme.EditorSurfaceVariant
import com.aftermotion.studio.ui.theme.NeonCyan
import com.aftermotion.studio.ui.theme.TextPrimary
import com.aftermotion.studio.ui.theme.TextSecondary
import com.aftermotion.studio.ui.util.bouncyClickable
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = NeonCyan,
    activeTrackColor = NeonCyan
)

/** Volume of one audio layer, 0..100 %. Changes are applied live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioVolumeSheet(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Volume", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${(volume * 100f).roundToInt()}%", color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = sliderColors()
            )
        }
    }
}

/** Fade in / fade out length of one audio layer. Changes are applied live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFadeSheet(
    fadeInMs: Long,
    fadeOutMs: Long,
    maxFadeMs: Long,
    onChange: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val maxFade = maxFadeMs.coerceIn(500L, 10_000L).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Fade", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fade in", color = TextSecondary, fontSize = 14.sp)
                Text(String.format(Locale.US, "%.1fs", fadeInMs / 1000f), color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = fadeInMs.toFloat().coerceIn(0f, maxFade),
                onValueChange = { onChange(it.toLong(), fadeOutMs) },
                valueRange = 0f..maxFade,
                colors = sliderColors()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fade out", color = TextSecondary, fontSize = 14.sp)
                Text(String.format(Locale.US, "%.1fs", fadeOutMs / 1000f), color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = fadeOutMs.toFloat().coerceIn(0f, maxFade),
                onValueChange = { onChange(fadeInMs, it.toLong()) },
                valueRange = 0f..maxFade,
                colors = sliderColors()
            )
        }
    }
}

/**
 * Speed of one layer (0.1x..4x, pitch stays the same). Without speed keyframes a change sets the
 * base speed (the layer gets shorter / longer on the timeline). With speed keyframes a change is
 * stored as a keyframe at the playhead, and the diamond / easing work like the other panels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    rawClip: TimelineClip? = null,
    playheadMs: Long = 0L,
    onLiveSpeed: ((Float) -> Unit)? = null,
    onToggleKeyframes: (List<AnimProp>) -> Unit = {},
    onEasingBegin: () -> Unit = {},
    onEasingChange: (AnimProp, Long, Easing) -> Unit = { _, _, _ -> }
) {
    val props = remember { listOf(AnimProp.SPEED) }
    val relMs = if (rawClip != null) playheadMs - rawClip.startMs else 0L
    val keys = rawClip?.keyframes?.get(AnimProp.SPEED).orEmpty()
    val keyed = keys.isNotEmpty()
    val insideClip = rawClip != null && playheadMs >= rawClip.startMs && playheadMs <= rawClip.endMs
    val onKeyframeNow = keys.indexOnTime(relMs) >= 0
    val segIdx = keys.segmentIndexAt(relMs)
    val easingEnabled = segIdx >= 0
    val segStart = if (segIdx >= 0) keys[segIdx] else null
    val segEnd = if (segIdx >= 0) keys[segIdx + 1] else null
    var showEasing by remember { mutableStateOf(false) }
    LaunchedEffect(easingEnabled) { if (!easingEnabled) showEasing = false }

    var shown by remember { mutableFloatStateOf(speed) }
    LaunchedEffect(speed) { shown = speed }

    // With keyframes every change is written at the playhead right away; without them the base
    // speed changes (on finger lift for the slider, at once for the presets).
    fun applySpeed(v: Float) {
        shown = v
        if (keyed && onLiveSpeed != null) onLiveSpeed(v) else onSpeedChange(v)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TransformValueHeader(
            valueText = String.format(Locale.US, "%.2fx", shown),
            onBack = null,
            keyframeEnabled = insideClip,
            hasKeyframes = keyed,
            onKeyframeNow = onKeyframeNow,
            onKeyframe = { onToggleKeyframes(props) },
            easingEnabled = easingEnabled,
            easingOpen = showEasing && easingEnabled,
            easing = segStart?.easing,
            onEasing = { if (easingEnabled) showEasing = !showEasing }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            if (showEasing && segStart != null && segEnd != null) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    EasingEditor(
                        easing = segStart.easing,
                        progress = ((relMs - segStart.timeMs).toFloat() /
                            (segEnd.timeMs - segStart.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f),
                        onBegin = onEasingBegin,
                        onChange = { e -> onEasingChange(AnimProp.SPEED, segStart.timeMs, e) }
                    )
                }
            } else {
                Slider(
                    value = shown,
                    onValueChange = { v -> if (keyed) applySpeed(v) else shown = v },
                    onValueChangeFinished = { if (!keyed) onSpeedChange(shown) },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    colors = sliderColors()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.1f, 0.5f, 1f, 2f, 4f).forEach { preset ->
                    Text(
                        text = String.format(Locale.US, "%.1fx", preset),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(EditorSurfaceVariant)
                            .bouncyClickable { applySpeed(preset) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
