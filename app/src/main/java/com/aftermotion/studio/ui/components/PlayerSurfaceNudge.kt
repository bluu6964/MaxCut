package com.aftermotion.studio.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Only one PlayerView can show the player at a time. When a full-screen editor (Crop, Effects...)
 * closes, its PlayerView lets go of the surface; the canvas PlayerView watches [version] and
 * attaches the player again so the video does not stay black.
 */
object PlayerSurfaceNudge {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}
