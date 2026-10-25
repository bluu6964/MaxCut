package com.aftermotion.studio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.ui.theme.IconActive
import com.aftermotion.studio.ui.theme.IconDisabled
import com.aftermotion.studio.ui.util.bouncyClickable

/**
 * Transport / play-control bar — CapCut-inspired minimal layout.
 *
 * Layout (dark theme):
 *   [Undo · Redo]  ·  [|◀  ▶  ▶|]  ·  [Fullscreen · Resolution]
 *
 * Play is a clean filled triangle (no heavy circle). Icons stay light and evenly spaced.
 */
@Composable
fun PlayControlsSection(
    height: Dp,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onFullScreenClick: () -> Unit,
    onResolutionClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playPauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playPauseSpring"
    )

    val active = IconActive
    val inactive = IconDisabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(EditorBackground)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: Undo · Redo ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ControlIcon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                enabled = canUndo,
                activeTint = active,
                inactiveTint = inactive,
                size = 22.dp,
                onClick = onUndoClick,
                testTag = "undo_button"
            )
            ControlIcon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                enabled = canRedo,
                activeTint = active,
                inactiveTint = inactive,
                size = 22.dp,
                onClick = onRedoClick,
                testTag = "redo_button"
            )
        }

        // ── Center: Prev · Play/Pause · Next ───────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ControlIcon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Jump Previous",
                enabled = true,
                activeTint = active,
                inactiveTint = inactive,
                size = 26.dp,
                onClick = onPreviousClick,
                testTag = "prev_frame_button"
            )

            // Play / Pause — prominent but clean (no circle background)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = playPauseScale
                        scaleY = playPauseScale
                    }
                    .bouncyClickable(
                        pressedScale = 0.88f,
                        hoverScale = 1.04f,
                        onClick = onPlayPauseClick
                    )
                    .testTag("play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            ControlIcon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Jump Next",
                enabled = true,
                activeTint = active,
                inactiveTint = inactive,
                size = 26.dp,
                onClick = onNextClick,
                testTag = "next_frame_button"
            )
        }

        // ── Right: Fullscreen · Resolution ─────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ControlIcon(
                imageVector = Icons.Default.CropFree,
                contentDescription = "Full Screen",
                enabled = true,
                activeTint = active,
                inactiveTint = inactive,
                size = 22.dp,
                onClick = onFullScreenClick,
                testTag = "fullscreen_button"
            )

            // Resolution = Visibility + small Settings badge (same as before)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .bouncyClickable(onClick = onResolutionClick)
                    .testTag("preview_resolution_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Preview Resolution",
                        tint = active,
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = active,
                        modifier = Modifier
                            .size(10.dp)
                            .offset(x = 7.dp, y = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    size: Dp,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) activeTint else inactiveTint,
            modifier = Modifier.size(size)
        )
    }
}
