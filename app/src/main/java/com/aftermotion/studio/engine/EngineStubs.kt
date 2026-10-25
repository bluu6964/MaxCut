package com.aftermotion.studio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Shader as AndroidShader
import android.net.Uri
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.aftermotion.studio.model.AspectRatio
import com.aftermotion.studio.model.ExportSettings
import com.aftermotion.studio.model.ImportedMedia
import com.aftermotion.studio.model.TimelineClip
import com.aftermotion.studio.model.TimelineTrack
import com.aftermotion.studio.model.TrackResult
import com.aftermotion.studio.model.TrackSample

/** UI-only no-op engine so Compose compiles without native/ML. */

object NativePreview {
    var enabled by mutableStateOf(false)
        private set
    var editorsOpen by mutableIntStateOf(0)
    fun init(context: Context) {}
    fun setNativeEnabled(on: Boolean) { enabled = on }
    fun eligible(tracks: List<TimelineTrack>): Boolean = false
    fun active(tracks: List<TimelineTrack>): Boolean = false
}

class NativePreviewEngine(context: Context) {
    fun release() {}
    fun update(tracks: List<TimelineTrack>, playheadMs: () -> Long, canvasArgb: Int) {}
    fun setPlaying(playing: Boolean) {}
    fun requestFrame() {}
    fun surfaceChanged(surface: Surface) {}
    fun surfaceDestroyed() {}
}

object NativeRenderAt {
    /** Full-frame native composite (stub). Clip-level drawing uses model TimelineClip.renderAt. */
    fun renderAt(
        context: Context,
        tracks: List<TimelineTrack>,
        ms: Long,
        width: Int,
        height: Int
    ): Bitmap? = null
}

class AudioPreview(context: Context) {
    fun sync(tracks: List<TimelineTrack>, playheadMs: Long, playing: Boolean, masterMuted: Boolean) {}
    fun release() {}
}

class EditorPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    var onEnded: (() -> Unit)? = null
    fun release() { try { player.release() } catch (_: Throwable) {} }
    fun setScrubbing(scrubbing: Boolean) {}
    fun setClips(clips: List<TimelineClip>, playheadMs: Long) {}
    fun setMuted(muted: Boolean) {}
    fun seekTo(ms: Long, force: Boolean = false) {}
}

class VideoLayerPreview(context: Context) {
    fun release() {}
    fun sync(
        tracks: List<TimelineTrack>,
        playheadMs: Long,
        playing: Boolean,
        masterMuted: Boolean = false,
        videoDisabled: Boolean = false
    ) {}
    fun playerFor(clipId: String): ExoPlayer? = null
}

class ScrubPreview(context: Context) {
    var paused: Boolean = false
    fun release() {}
    fun warmUp(clips: List<TimelineClip>, playheadMs: Long) {}
    fun frames(
        clips: List<TimelineClip>,
        playheadMs: Long,
        fast: Boolean = false,
        onFrame: (String, ScrubShown) -> Unit = { _, _ -> }
    ): Map<String, ScrubShown> = emptyMap()
}

data class ScrubShown(
    val bitmap: Bitmap? = null,
    val sourceMs: Long = 0L
) {
    val srcMs: Long get() = sourceMs
}

class VideoExporter(context: Context) {
    fun start(
        tracks: List<TimelineTrack>,
        aspectRatio: AspectRatio,
        settings: ExportSettings,
        previewFrameWidthPx: Float = 0f,
        previewDensity: Float = 1f,
        onProgress: (Float) -> Unit = {},
        onDone: (Uri?) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {}
    fun cancel() {}
}

object MediaImporter {
    fun read(context: Context, uris: List<Uri>): List<ImportedMedia> = emptyList()
}

object CutoutEngine {
    fun loadPhoto(context: Context, uri: Uri): Bitmap? = null
    fun cutoutAuto(context: Context, src: Bitmap): Pair<Bitmap, Any?> = Pair(src, null)
    fun savePng(context: Context, bmp: Bitmap): String = ""
}

object CutoutVideo {
    data class Result(
        val startMs: Long = 0L,
        val frames: Int = 0,
        val fps: Float = 30f,
        val dir: String? = null,
        val summary: String = ""
    )
    suspend fun generate(
        context: Context,
        clip: TimelineClip,
        onProgress: (Float) -> Unit = {}
    ): Result = Result()
}

object CutoutMasks {
    fun bitmap(clip: TimelineClip, sourceMs: Long): Bitmap? = null
    fun bitmap(context: Context, clip: TimelineClip, sourceMs: Long): Bitmap? = null
    fun sourceMs(clip: TimelineClip, timelineMs: Long): Long = 0L
}

object FaceBlurVideo {
    data class Result(
        val startMs: Long = 0L,
        val frames: Int = 0,
        val fps: Float = 30f,
        val framesWithFace: Int = 0,
        val dir: String? = null,
        val summary: String = ""
    )
    suspend fun generate(
        context: Context,
        clip: TimelineClip,
        region: FaceRegion,
        onProgress: (Float) -> Unit = {}
    ): Result = Result()
}

object FaceBlurMasks {
    fun bitmap(clip: TimelineClip, sourceMs: Long): Bitmap? = null
    fun bitmap(context: Context, clip: TimelineClip, sourceMs: Long): Bitmap? = null
    fun sourceMs(clip: TimelineClip, timelineMs: Long): Long = 0L
}

class FaceBlurScratch {
    var view: Any? = null
    var status: String = ""
    fun release() {}
}

object FaceBlurPreview {
    const val DEBUG = false
    const val SMALL_WIDTH = 160
    fun render(
        scratch: FaceBlurScratch,
        mask: Bitmap?,
        frame: Bitmap?,
        width: Int,
        height: Int,
        strength: Float
    ): Bitmap? = null
}

enum class FaceRegion(val label: String) {
    FULL("Full face"),
    FACE("Face"),
    EYES("Eyes"),
    MOUTH("Mouth"),
    NOSE("Nose"),
    LIPS("Lips");
    companion object {
        fun fromName(name: String?): FaceRegion =
            entries.find { it.name.equals(name, true) || it.label.equals(name, true) } ?: FULL
    }
}

object FaceModelDownloader {
    const val APPROX_SIZE_TEXT = "~0 MB (stub)"
    fun isDownloaded(context: Context): Boolean = false
    suspend fun download(
        context: Context,
        onBytes: ((Long, Long) -> Unit)? = null,
        onProgress: (Float) -> Unit = {}
    ) {}
}

object ModelDownloader {
    suspend fun download(context: Context, onProgress: (Float) -> Unit = {}) {}
}

object WhisperModelDownloader {
    const val APPROX_SIZE_TEXT = "~0 MB (stub)"
    fun isDownloaded(context: Context): Boolean = false
    suspend fun download(
        context: Context,
        onBytes: ((Long, Long) -> Unit)? = null,
        onProgress: (Float) -> Unit = {}
    ) {}
}

object WhisperEngine {
    fun unsupportedReason(): String? = "Engine removed (UI-only build)"
}

object AutoCaptions {
    const val MAX_CHARS = 42
    val LANGUAGES: List<Pair<String, String>> = listOf("en" to "English")
    val LENGTHS: List<Pair<String, Int>> = listOf("Short" to 32, "Medium" to 42, "Long" to 60)
    data class Status(val message: String, val progress: Float) {
        val label: String get() = message
    }
    fun canCaption(clip: TimelineClip): Boolean = false
    suspend fun generate(
        context: Context,
        clip: TimelineClip,
        language: String = "en",
        maxChars: Int = MAX_CHARS,
        onStatus: (Status) -> Unit = {}
    ): List<CaptionBuilder.Caption> = emptyList()
}

object CaptionBuilder {
    data class Caption(
        val text: String = "",
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val relStartMs: Long = 0L,
        val relEndMs: Long = 0L
    )
}

object CaptionProgress {
    enum class Stage(val from: Float) {
        DOWNLOAD(0f), TRANSCRIBE(0.3f), PLACE(0.9f)
    }
}

object MotionTrack {
    fun pictureAspect(context: Context, uri: String): Float = 16f / 9f

    class LiveFrame(
        val srcMs: Long = 0L,
        val u: Float = 0.5f,
        val v: Float = 0.5f,
        val rNorm: Float = 0.1f,
        val aspect: Float = 1f,
        val pts: FloatArray = FloatArray(0),
        val samples: List<TrackSample> = emptyList()
    )

    suspend fun run(
        context: Context,
        clip: TimelineClip,
        startSrcMs: Long,
        x: Float = 0.5f,
        y: Float = 0.5f,
        rNorm: Float = 0.1f,
        onProgress: (Float) -> Unit = {},
        onLive: (LiveFrame) -> Unit = {},
        shouldStop: () -> Boolean = { false }
    ): TrackResult? = null
}

object RvmEngine {
    fun isAvailable(context: Context): Boolean = false
}

object StickerLibrary {
    const val URI_PREFIX = "sticker://"
    data class Item(
        val assetPath: String = "",
        val name: String = "",
        val category: String = "",
        val animated: Boolean = false,
        val frameCount: Int = 1,
        val fps: Float = 12f
    )
    fun init(context: Context) {}
    fun categories(): List<String> = emptyList()
    fun items(category: String): List<Item> = emptyList()
    fun frame(item: Item, index: Int): Bitmap? = null
    fun frameFor(uri: String?, sourceMs: Long): Bitmap? = null
    fun frameIndexAt(item: Item, sourceMs: Long): Int {
        if (!item.animated || item.frameCount <= 1 || item.fps <= 0f) return 0
        val idx = ((sourceMs / 1000.0) * item.fps).toInt()
        return ((idx % item.frameCount) + item.frameCount) % item.frameCount
    }
}

object AudioWaveform {
    const val BUCKET_MS = 50L
    fun compute(context: Context, uri: String, durationMs: Long): FloatArray? = null
    fun peaks(context: Context, uri: String, samples: Int = 100): FloatArray = FloatArray(samples)
}

object FillImages {
    fun init(context: Context) {}
    fun peek(uri: String?): Bitmap? = null
}

fun buildGradientShader(clip: TimelineClip, width: Float, height: Float): AndroidShader {
    return LinearGradient(
        0f, 0f, width.coerceAtLeast(1f), height.coerceAtLeast(1f),
        intArrayOf(0xFF888888.toInt(), 0xFF444444.toInt()),
        null,
        AndroidShader.TileMode.CLAMP
    )
}

fun buildImageShader(
    bmp: Bitmap,
    uri: String,
    mode: Int,
    width: Int,
    height: Int
): AndroidShader {
    return BitmapShader(bmp, AndroidShader.TileMode.CLAMP, AndroidShader.TileMode.CLAMP)
}

object DecoderPool {
    val isAvailable: Boolean = false
}

object CutoutTrace {
    fun incomplete(context: Context): String? = null
}
