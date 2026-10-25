package com.aftermotion.studio.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aftermotion.studio.engine.NativePreviewEngine
import com.aftermotion.studio.model.TimelineTrack

/**
 * The surface the C++ preview draws on (video / photo layers). Text, shapes and stickers are drawn by
 * Compose on top of it, like they are drawn on top of the Media3 PlayerView in the old path.
 */
@Composable
internal fun NativePreviewSurface(
    engine: NativePreviewEngine,
    tracks: List<TimelineTrack>,
    playheadMs: () -> Long,
    isPlaying: Boolean,
    canvasArgb: Int,
    modifier: Modifier = Modifier
) {
    SideEffect {
        engine.update(tracks, playheadMs, canvasArgb)
        engine.setPlaying(isPlaying)
        engine.requestFrame()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {}
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        engine.surfaceChanged(holder.surface)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        engine.surfaceDestroyed()
                    }
                })
            }
        }
    )
}
