package com.aftermotion.studio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aftermotion.studio.ui.components.TextEditSheet
import com.aftermotion.studio.ui.components.swipeDownToDismiss
import com.aftermotion.studio.ui.util.enterPop
import com.aftermotion.studio.ui.components.pickColorFromWindow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import android.content.Intent
import android.graphics.Bitmap
import com.aftermotion.studio.engine.AudioPreview
import com.aftermotion.studio.engine.VideoLayerPreview
import com.aftermotion.studio.engine.EditorPlayer
import com.aftermotion.studio.engine.MediaImporter
import com.aftermotion.studio.engine.ScrubPreview
import com.aftermotion.studio.engine.ScrubShown
import com.aftermotion.studio.engine.VideoExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aftermotion.studio.model.LayerType
import com.aftermotion.studio.model.at
import com.aftermotion.studio.model.hasBlending
import com.aftermotion.studio.model.TIMELINE_PX_PER_MS
import com.aftermotion.studio.ui.components.AudioClipToolsPanel
import com.aftermotion.studio.ui.components.AudioFadeSheet
import com.aftermotion.studio.ui.components.AudioSpeedSheet
import com.aftermotion.studio.ui.components.AudioVolumeSheet
import com.aftermotion.studio.ui.components.AddShapeSheet
import com.aftermotion.studio.ui.components.StickerPickerSheet
import com.aftermotion.studio.ui.components.StickerSpeedSheet
import com.aftermotion.studio.ui.components.AddTextSheet
import com.aftermotion.studio.ui.components.AppliedEffectsView
import com.aftermotion.studio.ui.components.AspectRatioSheet
import com.aftermotion.studio.ui.components.AddLayerFab
import com.aftermotion.studio.ui.components.CanvasArea
import com.aftermotion.studio.ui.components.ClipEditToolsPanel
import com.aftermotion.studio.ui.components.ClipEffectsSheet
import com.aftermotion.studio.ui.components.ClipAnimationsSheet
import com.aftermotion.studio.ui.components.ClipReplaceSheet
import com.aftermotion.studio.ui.components.ClipBlendSheet
import com.aftermotion.studio.ui.components.ClipCropSheet
import com.aftermotion.studio.ui.components.EditorHeader
import com.aftermotion.studio.ui.components.ExportModalDialog
import com.aftermotion.studio.ui.components.FullscreenPlayerModal
import com.aftermotion.studio.ui.components.PlayControlsSection
import com.aftermotion.studio.ui.components.ResolutionSheet
import com.aftermotion.studio.ui.components.TimelineLayerPanel
import com.aftermotion.studio.ui.components.TimescaleSection
import com.aftermotion.studio.ui.theme.EditorBackground
import com.aftermotion.studio.viewmodel.VideoEditorViewModel
import com.aftermotion.studio.ui.components.TrackLayerPickDialog
import com.aftermotion.studio.ui.components.TrackRunOverlay
import com.aftermotion.studio.ui.components.TrackSetupOverlay
import com.aftermotion.studio.ui.components.TrackTarget
import kotlinx.coroutines.launch
import com.aftermotion.studio.model.sourceOffsetMs
import com.aftermotion.studio.model.ImportedMedia
import com.aftermotion.studio.engine.CutoutEngine
import androidx.core.net.toUri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** The video layer being prepared for tracking and the picture's width / height. */
private data class TrackSetupState(val clipId: String, val aspect: Float, val targetId: String)

@OptIn(UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VideoEditorScreen(
    viewModel: VideoEditorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    // The playhead changes every frame. It is held as a State and only READ in lambdas / draw /
    // layout phases (or inside modes that are open), so the whole editor no longer recomposes per frame.
    val playheadState = viewModel.currentPlayheadMs.collectAsState()
    val playheadProvider: () -> Long = remember { { playheadState.value } }
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val rawAspectRatio by viewModel.selectedAspectRatio.collectAsState()
    val canvasColor by viewModel.canvasColor.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val multiSelectIds by viewModel.multiSelectIds.collectAsState()
    val isMasterMuted by viewModel.isMasterMuted.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    // "Original" = the shape of the first video / photo.
    val originalAspect = remember(tracks) {
        tracks.flatMap { it.clips }
            .filter { it.type == LayerType.VIDEO && it.uri != null && it.sourceAspect > 0f }
            .minByOrNull { it.startMs }?.sourceAspect ?: 0f
    }
    val selectedAspectRatio = remember(rawAspectRatio, originalAspect) {
        rawAspectRatio.withOriginalAspect(originalAspect)
    }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val waveforms by viewModel.waveforms.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Full-video cutout (RVM): progress 0..1 while it runs, null when idle.
    var cutoutProgress by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    var cutoutJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cutoutLabel by remember { androidx.compose.runtime.mutableStateOf("Cutting out the person…") }
    // Video layer waiting for the user to agree to download the cutout model.
    var askModelDownload by remember { androidx.compose.runtime.mutableStateOf<com.aftermotion.studio.model.TimelineClip?>(null) }

    // Runs the video cutout; downloads the model first when it is not on the phone yet.
    val runVideoCutout: (com.aftermotion.studio.model.TimelineClip, Boolean) -> Unit = { target, needDownload ->
        cutoutProgress = 0f
        cutoutLabel = if (needDownload) "Downloading cutout model…" else "Cutting out the person…"
        cutoutJob = scope.launch {
            try {
                if (needDownload) {
                    withContext(Dispatchers.IO) {
                        com.aftermotion.studio.engine.ModelDownloader.download(context) { p -> cutoutProgress = p }
                    }
                    cutoutLabel = "Cutting out the person…"
                    cutoutProgress = 0f
                }
                val r = withContext(Dispatchers.Default) {
                    com.aftermotion.studio.engine.CutoutVideo.generate(context, target) { p -> cutoutProgress = p }
                }
                viewModel.beginAdjust()
                viewModel.updateClip(target.id) {
                    it.copy(
                        cutoutDir = r.dir, cutoutFps = r.fps,
                        cutoutStartMs = r.startMs, cutoutFrames = r.frames
                    )
                }
                cutoutProgress = null
                snackbarHostState.showSnackbar("Cutout done" + (if (r.summary.isNotEmpty()) " · " + r.summary else ""))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                cutoutProgress = null
                snackbarHostState.showSnackbar("Cutout failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                cutoutProgress = null
                cutoutJob = null
            }
        }
    }
    askModelDownload?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askModelDownload = null },
            title = { Text("Download cutout model?") },
            text = { Text("Cutout needs a one-time download of about 14 MB. It is kept on your phone and works offline after that.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askModelDownload = null
                    runVideoCutout(pending, true)
                }) { Text("Download") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askModelDownload = null }) { Text("Not now") }
            }
        )
    }
    val exportSettings by viewModel.exportSettings.collectAsState()
    // Size of the canvas frame in the preview: the export draws shapes / text at the same relative size.
    var previewFrameWidthPx by remember { mutableFloatStateOf(0f) }
    var previewDensity by remember { mutableFloatStateOf(1f) }

    // ---- Face AI (MediaPipe Face Landmarker): blur that follows the face / eyes / nose / lips ----
    var faceProgress by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    var faceLabel by remember { androidx.compose.runtime.mutableStateOf("Finding the face…") }
    var faceJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var askFaceAi by remember { androidx.compose.runtime.mutableStateOf<com.aftermotion.studio.model.TimelineClip?>(null) }
    var faceRegion by remember {
        androidx.compose.runtime.mutableStateOf(
            com.aftermotion.studio.engine.FaceRegion.fromName(
                context.getSharedPreferences("face_ai", android.content.Context.MODE_PRIVATE).getString("region", null)
            )
        )
    }
    var faceStrength by remember {
        androidx.compose.runtime.mutableStateOf(
            context.getSharedPreferences("face_ai", android.content.Context.MODE_PRIVATE).getFloat("strength", 0.5f)
        )
    }

    val requestFaceAi: (String?) -> Unit = req@{ id ->
        val clip = tracks.flatMap { it.clips }.find { it.id == id } ?: return@req
        snackbarHostState.currentSnackbarData?.dismiss()
        if (clip.type != LayerType.VIDEO || clip.isImage || clip.uri == null) {
            scope.launch { snackbarHostState.showSnackbar("Face AI works on video layers") }
        } else if (faceProgress != null) {
            scope.launch { snackbarHostState.showSnackbar("Face AI is already running") }
        } else {
            // Start with what this layer already has, so the dialog shows the current choice.
            if (clip.faceBlurDir != null) {
                faceRegion = com.aftermotion.studio.engine.FaceRegion.fromName(clip.faceBlurRegion)
                faceStrength = clip.faceBlurStrength
            }
            askFaceAi = clip
        }
    }

    // Downloads the face model when it is missing, finds the face in every frame, saves the masks.
    val runFaceBlur: (com.aftermotion.studio.model.TimelineClip, com.aftermotion.studio.engine.FaceRegion, Float) -> Unit = { target, region, strength ->
        context.getSharedPreferences("face_ai", android.content.Context.MODE_PRIVATE)
            .edit().putString("region", region.name).putFloat("strength", strength).apply()
        faceProgress = 0f
        faceJob = scope.launch {
            try {
                if (!com.aftermotion.studio.engine.FaceModelDownloader.isDownloaded(context)) {
                    faceLabel = "Downloading face model…"
                    withContext(Dispatchers.IO) {
                        com.aftermotion.studio.engine.FaceModelDownloader.download(context) { p -> faceProgress = p }
                    }
                    faceProgress = 0f
                }
                faceLabel = "Finding the face…"
                val r = withContext(Dispatchers.Default) {
                    com.aftermotion.studio.engine.FaceBlurVideo.generate(context, target, region) { p -> faceProgress = p }
                }
                viewModel.beginAdjust()
                viewModel.updateClip(target.id) {
                    it.copy(
                        faceBlurDir = r.dir, faceBlurFps = r.fps,
                        faceBlurStartMs = r.startMs, faceBlurFrames = r.frames,
                        faceBlurStrength = strength, faceBlurRegion = region.name
                    )
                }
                faceProgress = null
                val pct = (100f * r.framesWithFace / r.frames.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                snackbarHostState.showSnackbar(
                    region.label + " blur done · face in $pct% of the frames · " + r.summary
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                faceProgress = null
                scope.launch { snackbarHostState.showSnackbar("Face AI cancelled") }
                throw e
            } catch (e: Throwable) {
                faceProgress = null
                snackbarHostState.showSnackbar("Face AI failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                faceProgress = null
                faceJob = null
            }
        }
    }
    askFaceAi?.let { pending ->
        val modelReady = com.aftermotion.studio.engine.FaceModelDownloader.isDownloaded(context)
        val hasBlur = pending.faceBlurDir != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askFaceAi = null },
            title = { Text("Face AI · Blur") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (modelReady) "The blur follows the face in every frame. Works offline."
                        else "Face AI needs a one-time download of the face model (" +
                            com.aftermotion.studio.engine.FaceModelDownloader.APPROX_SIZE_TEXT +
                            "). It is kept on your phone and works offline after that.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("What to blur", fontSize = 12.sp, color = Color(0xFF9AA4B5))
                    com.aftermotion.studio.engine.FaceRegion.values().forEach { r ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { faceRegion = r }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = faceRegion == r, onClick = { faceRegion = r })
                            Text(r.label, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Blur strength", fontSize = 12.sp, color = Color(0xFF9AA4B5))
                    listOf("Low" to 0.15f, "Medium" to 0.5f, "High" to 0.9f).forEach { (name, v) ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { faceStrength = v }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = kotlin.math.abs(faceStrength - v) < 0.2f &&
                                    listOf(0.15f, 0.5f, 0.9f).minByOrNull { kotlin.math.abs(it - faceStrength) } == v,
                                onClick = { faceStrength = v }
                            )
                            Text(name, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askFaceAi = null
                    runFaceBlur(pending, faceRegion, faceStrength)
                }) { Text(if (modelReady) "Process" else "Download & process") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    if (hasBlur) {
                        androidx.compose.material3.TextButton(onClick = {
                            askFaceAi = null
                            viewModel.beginAdjust()
                            viewModel.updateClip(pending.id) {
                                it.copy(faceBlurDir = null, faceBlurFps = 0f, faceBlurStartMs = 0L, faceBlurFrames = 0, faceBlurRegion = "")
                            }
                            scope.launch { snackbarHostState.showSnackbar("Face blur removed") }
                        }) { Text("Remove") }
                    }
                    androidx.compose.material3.TextButton(onClick = { askFaceAi = null }) { Text("Cancel") }
                }
            }
        )
    }

    // ---- Auto Captions: speech -> text layers (whisper.cpp, model "small" q5_1, downloaded on first use) ----
    // ONE state for the bar (label + %), so a label never shows next to the % of another stage. null = not running.
    var captionStatus by remember { mutableStateOf<com.aftermotion.studio.engine.AutoCaptions.Status?>(null) }
    var captionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Layer waiting for the user to pick the language (and agree to the model download).
    var askCaptions by remember { mutableStateOf<com.aftermotion.studio.model.TimelineClip?>(null) }
    var captionLang by remember {
        mutableStateOf(
            context.getSharedPreferences("captions", android.content.Context.MODE_PRIVATE)
                .getString("lang", "auto") ?: "auto"
        )
    }
    // Maximum characters per caption (Short / Normal / Long in the dialog), remembered.
    var captionMaxChars by remember {
        mutableStateOf(
            context.getSharedPreferences("captions", android.content.Context.MODE_PRIVATE)
                .getInt("maxChars", com.aftermotion.studio.engine.AutoCaptions.MAX_CHARS)
        )
    }

    // Tool "Captions" on a video / audio layer: checks the phone, then asks language + download.
    val requestCaptions: (String?) -> Unit = req@{ id ->
        val clip = tracks.flatMap { it.clips }.find { it.id == id } ?: return@req
        snackbarHostState.currentSnackbarData?.dismiss()
        val unsupported = com.aftermotion.studio.engine.WhisperEngine.unsupportedReason()
        if (!com.aftermotion.studio.engine.AutoCaptions.canCaption(clip)) {
            scope.launch { snackbarHostState.showSnackbar("Captions work on video and audio layers") }
        } else if (unsupported != null) {
            scope.launch { snackbarHostState.showSnackbar(unsupported) }
        } else if (captionStatus != null) {
            scope.launch { snackbarHostState.showSnackbar("Captions are already being made") }
        } else {
            askCaptions = clip
        }
    }

    // Downloads the speech model when it is missing, then makes the captions and adds them as text layers.
    // Every step has its own label and its own 0..100 % (the download is separate from the captions).
    val runCaptions: (com.aftermotion.studio.model.TimelineClip, String, Int) -> Unit = { target, lang, maxChars ->
        context.getSharedPreferences("captions", android.content.Context.MODE_PRIVATE)
            .edit().putString("lang", lang).putInt("maxChars", maxChars).apply()
        captionStatus = com.aftermotion.studio.engine.AutoCaptions.Status("Starting", 0f)
        captionJob = scope.launch {
            try {
                if (!com.aftermotion.studio.engine.WhisperModelDownloader.isDownloaded(context)) {
                    captionStatus = com.aftermotion.studio.engine.AutoCaptions.Status("Downloading speech model", 0f)
                    var mb = ""
                    withContext(Dispatchers.IO) {
                        com.aftermotion.studio.engine.WhisperModelDownloader.download(
                            context,
                            onBytes = { done, total ->
                                mb = " (" + done / 1_000_000L + " / " + total / 1_000_000L + " MB)"
                            }
                        ) { p ->
                            captionStatus = com.aftermotion.studio.engine.AutoCaptions.Status("Downloading speech model$mb", p)
                        }
                    }
                }
                val made = withContext(Dispatchers.Default) {
                    com.aftermotion.studio.engine.AutoCaptions.generate(
                        context, target, lang, maxChars,
                        onStatus = { captionStatus = it }
                    )
                }
                // the layer may have been moved while the captions were made: use where it is now
                val parentStart = tracks.flatMap { it.clips }.find { it.id == target.id }?.startMs ?: target.startMs
                val fw = previewFrameWidthPx
                val fh = if (fw > 0f) fw / selectedAspectRatio.ratioFloat else 0f
                // Show the last stage before the (short) adding step, and let one frame draw it.
                captionStatus = com.aftermotion.studio.engine.AutoCaptions.Status(
                    "Adding " + made.size + " captions to the timeline",
                    com.aftermotion.studio.engine.CaptionProgress.Stage.PLACE.from
                )
                androidx.compose.runtime.withFrameNanos { }
                val added = viewModel.addCaptionLayers(parentStart, made, fh * 0.30f)
                captionStatus = null
                snackbarHostState.showSnackbar(if (added > 0) "$added captions added" else "No speech found")
            } catch (e: kotlinx.coroutines.CancellationException) {
                captionStatus = null
                scope.launch { snackbarHostState.showSnackbar("Captions cancelled") }
                throw e
            } catch (e: Throwable) {
                captionStatus = null
                snackbarHostState.showSnackbar("Captions failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                captionStatus = null
                captionJob = null
            }
        }
    }
    askCaptions?.let { pending ->
        val modelReady = com.aftermotion.studio.engine.WhisperModelDownloader.isDownloaded(context)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askCaptions = null },
            title = { Text("Auto Captions") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (modelReady) "The speech of this layer becomes text layers on the timeline. Works offline."
                        else "Captions need a one-time download of the speech model (" +
                            com.aftermotion.studio.engine.WhisperModelDownloader.APPROX_SIZE_TEXT +
                            "). Wi-Fi is recommended. It is kept on your phone and works offline after that.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Language of the speech", fontSize = 12.sp, color = Color(0xFF9AA4B5))
                    com.aftermotion.studio.engine.AutoCaptions.LANGUAGES.forEach { (code, name) ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { captionLang = code }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = captionLang == code,
                                onClick = { captionLang = code }
                            )
                            Text(name, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Length of one caption", fontSize = 12.sp, color = Color(0xFF9AA4B5))
                    com.aftermotion.studio.engine.AutoCaptions.LENGTHS.forEach { (name, chars) ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { captionMaxChars = chars }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = captionMaxChars == chars,
                                onClick = { captionMaxChars = chars }
                            )
                            Text(name, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askCaptions = null
                    runCaptions(pending, captionLang, captionMaxChars)
                }) { Text(if (modelReady) "Start" else "Download & start") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askCaptions = null }) { Text("Not now") }
            }
        )
    }

    // ---- Motion tracking: pick a layer, put a circle on the object, watch the layer follow live ----
    var trackPickFor by remember { mutableStateOf<String?>(null) } // video waiting for "which layer follows?"
    var trackSetup by remember { mutableStateOf<TrackSetupState?>(null) }
    var trackCircle by remember { mutableStateOf(com.aftermotion.studio.ui.components.TrackCircle()) }
    var trackRunning by remember { mutableStateOf(false) }
    var trackProgress by remember { mutableStateOf<Float?>(null) }
    var trackLive by remember { mutableStateOf<com.aftermotion.studio.ui.components.TrackLive?>(null) }
    var trackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val trackStop = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Tool "Track" (video layer selected): first ask which layer should follow the object.
    val requestTrack: (String?) -> Unit = req@{ id ->
        if (trackRunning) {
            scope.launch { snackbarHostState.showSnackbar("A track is already running") }
            return@req
        }
        trackPickFor = id
    }

    // A follower layer was picked: show the circle over the canvas (the track starts at the playhead).
    val openTrackSetup: (String, String) -> Unit = openSetup@{ clipId, targetId ->
        val video = tracks.flatMap { it.clips }.find { it.id == clipId } ?: return@openSetup
        val uri = video.uri ?: return@openSetup
        if (!video.isActiveAt(playheadState.value)) viewModel.seekTo(video.startMs)
        viewModel.pause()
        scope.launch {
            val a = withContext(Dispatchers.IO) { com.aftermotion.studio.engine.MotionTrack.pictureAspect(context, uri) }
            trackCircle = com.aftermotion.studio.ui.components.TrackCircle()
            trackSetup = TrackSetupState(video.id, if (a > 0f) a else video.sourceAspect, targetId)
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("Put the circle on a part of the object, then press Track")
        }
    }

    // "Track" button of the circle: follows the object from the playhead to the end of the layer.
    // The circle, the followed corners and the follower layer move live; Stop keeps what is done.
    val startTracking: () -> Unit = start@{
        val setup = trackSetup ?: return@start
        val all = tracks.flatMap { it.clips }
        val video = all.find { it.id == setup.clipId } ?: return@start
        val target = all.find { it.id == setup.targetId }
        if (target == null) {
            trackSetup = null
            scope.launch { snackbarHostState.showSnackbar("The layer that should follow is gone") }
            return@start
        }
        val fw = previewFrameWidthPx
        if (fw <= 0f) return@start
        val fh = fw / selectedAspectRatio.ratioFloat
        val t = playheadState.value
        if (!video.isActiveAt(t)) {
            scope.launch { snackbarHostState.showSnackbar("Move the playhead onto the video layer") }
            return@start
        }
        val placement = com.aftermotion.studio.model.VideoPlacement(video.at(t), setup.aspect, fw, fh)
        val c = trackCircle
        val p = placement.fromCanvas(c.cx * fw, c.cy * fh)
        if (p.x < 0f || p.x > 1f || p.y < 0f || p.y > 1f) {
            scope.launch { snackbarHostState.showSnackbar("Put the circle on the video picture") }
            return@start
        }
        val rNorm = (c.r * fw) / placement.pixelsPerPictureWidth
        val startSrc = com.aftermotion.studio.engine.CutoutMasks.sourceMs(video, t)
        viewModel.pause()
        viewModel.beginAdjust() // one undo step for the whole track
        trackSetup = null
        trackStop.set(false)
        trackLive = null
        trackProgress = 0f
        trackRunning = true
        trackJob = scope.launch {
            var lastHeavy = 0L
            var done = false
            try {
                val r = com.aftermotion.studio.engine.MotionTrack.run(
                    context, video, startSrc, p.x, p.y, rNorm,
                    onProgress = { pr -> trackProgress = pr },
                    onLive = { lf ->
                        // circle + corners on the canvas, in the video's own (possibly moving) place
                        val tl = com.aftermotion.studio.model.MotionTrackApply.timelineMsAt(video, lf.srcMs)
                        val pl = com.aftermotion.studio.model.VideoPlacement(video.at(tl), lf.aspect, fw, fh)
                        val cp = pl.toCanvas(lf.u, lf.v)
                        val pts = FloatArray(lf.pts.size)
                        var i = 0
                        while (i + 1 < pts.size) {
                            val q = pl.toCanvas(lf.pts[i], lf.pts[i + 1])
                            pts[i] = q.x / fw
                            pts[i + 1] = q.y / fh
                            i += 2
                        }
                        trackLive = com.aftermotion.studio.ui.components.TrackLive(
                            cp.x / fw, cp.y / fh, lf.rNorm * pl.pixelsPerPictureWidth / fw, pts
                        )
                        // the follower layer and the playhead move along (a few times a second)
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastHeavy >= 120L) {
                            lastHeavy = now
                            val part = com.aftermotion.studio.model.TrackResult(video.id, lf.samples.toList(), lf.aspect)
                            val fol = com.aftermotion.studio.model.MotionTrackApply.follow(video, part, fw, fh)
                            scope.launch {
                                if (fol != null) {
                                    viewModel.setTrackFollow(target.id, fol, undoStep = false)
                                    // keep the layer on screen as long as the object is followed
                                    val need = fol.timesMs.last() - target.startMs
                                    if (need > target.durationMs) viewModel.setOverlayDurationMs(target.id, need)
                                }
                                viewModel.seekTo(tl)
                            }
                        }
                    },
                    shouldStop = { trackStop.get() }
                )
                val fol = r?.let { com.aftermotion.studio.model.MotionTrackApply.follow(video, it, fw, fh) }
                if (r == null || fol == null) {
                    scope.launch { snackbarHostState.showSnackbar("Nothing could be tracked") }
                } else {
                    viewModel.setTrackFollow(target.id, fol, undoStep = false)
                    val need = fol.timesMs.last() - target.startMs
                    if (need > target.durationMs) viewModel.setOverlayDurationMs(target.id, need)
                    done = true
                    val lost = r.lostAtSrcMs
                    val msg = if (lost != null) {
                        "Lost the object after " +
                            String.format(java.util.Locale.US, "%.1f", (lost - startSrc) / 1000f) +
                            " s. The layer follows up to there."
                    } else if (trackStop.get()) "Stopped. The layer follows the part tracked so far."
                    else {
                        val lastSrc = r.samples.last().srcMs
                        val endSrc = video.trimStartMs + video.sourceOffsetMs(video.durationMs)
                        val secs = String.format(java.util.Locale.US, "%.1f", (lastSrc - startSrc) / 1000f)
                        val total = String.format(java.util.Locale.US, "%.1f", (endSrc - startSrc) / 1000f)
                        if (endSrc - lastSrc > 700L) "Video reading stopped early: tracked $secs s of $total s."
                        else "Done. Tracked $secs s of $total s."
                    }
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
                viewModel.seekTo(t)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Tracking failed: " + (e.message ?: e.javaClass.simpleName)) }
            } finally {
                if (!done) {
                    viewModel.setTrackFollow(target.id, target.trackFollow, undoStep = false)
                    viewModel.setOverlayDurationMs(target.id, target.durationMs)
                }
                trackRunning = false
                trackProgress = null
                trackLive = null
                trackJob = null
            }
        }
    }

    trackPickFor?.let { videoId ->
        val targets = tracks.flatMap { it.clips }
            .filter { it.id != videoId && it.type != LayerType.AUDIO }
            .map { TrackTarget(it.id, it.type.displayName + ": " + (it.text?.take(24)?.ifBlank { null } ?: it.name)) }
        TrackLayerPickDialog(
            targets = targets,
            onPick = { id ->
                trackPickFor = null
                openTrackSetup(videoId, id)
            },
            onDismiss = { trackPickFor = null }
        )
    }

    // v0.1 only supports main-track editing; everything else shows this message.
    val comingSoon: () -> Unit = {
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch { snackbarHostState.showSnackbar("Coming in a later version") }
    }

    // ---- Real engine: preview player, scrub frame cache, exporter, media picker ----
    val editorPlayer = remember { EditorPlayer(context) }
    val scrubPreview = remember { ScrubPreview(context) }
    val exporter = remember { VideoExporter(context) }
    val audioPreview = remember { AudioPreview(context) }
    val videoPreview = remember { VideoLayerPreview(context) }
    // C++ preview (beta): draws the video / photo layers with the GL renderer; Media3 then only plays the sound.
    val nativePreview = remember {
        com.aftermotion.studio.engine.NativePreview.init(context)
        com.aftermotion.studio.engine.NativePreviewEngine(context)
    }
    val nativeActive = remember(tracks, com.aftermotion.studio.engine.NativePreview.enabled, com.aftermotion.studio.engine.NativePreview.editorsOpen) {
        com.aftermotion.studio.engine.NativePreview.active(tracks)
    }
    val nativeActiveNow by rememberUpdatedState(nativeActive)
    // Alight-style: while the finger/fling is moving, canvas shows cached bitmaps (no Exo seek).
    var isActivelyScrubbing by remember { mutableStateOf(false) }
    // One scrub frame per video layer (picture + the source time it belongs to: the cutout mask and
    // the tracked layers use that time). The canvas reads it while drawing, so it is never recomposed.
    val scrubFrames = remember { androidx.compose.runtime.mutableStateMapOf<String, ScrubShown>() }
    val scrubFrameOf: (String) -> ScrubShown? = remember { { id: String -> scrubFrames[id] } }
    DisposableEffect(Unit) {
        editorPlayer.onEnded = { viewModel.onPlaybackEnded() }
        onDispose {
            exporter.cancel()
            editorPlayer.release()
            scrubPreview.release()
            audioPreview.release()
            videoPreview.release()
            nativePreview.release()
        }
    }

    // Leaving the app stops playback (video and audio layers).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Audio layers follow the playhead. While playing they are synced 10x/s (not per frame);
    // while scrubbing the sync is debounced so swiping stays fluid.
    LaunchedEffect(tracks, isPlaying, isMasterMuted, nativeActive) {
        if (isPlaying) {
            while (isActive) {
                val now = viewModel.currentPlayheadMs.value
                audioPreview.sync(tracks, now, true, isMasterMuted)
                videoPreview.sync(tracks, now, true, isMasterMuted, videoDisabled = nativeActive)
                delay(100L)
            }
        } else {
            viewModel.currentPlayheadMs.collectLatest { ms ->
                delay(100L)
                audioPreview.sync(tracks, ms, false, isMasterMuted)
                videoPreview.sync(tracks, ms, false, isMasterMuted, videoDisabled = nativeActive)
            }
        }
    }
    // C++ preview: a new picture whenever the playhead or the layers change (requests are coalesced).
    LaunchedEffect(nativeActive, tracks) {
        if (!nativeActive) return@LaunchedEffect
        viewModel.currentPlayheadMs.collect { nativePreview.requestFrame() }
    }
    // Decode the waveform of every audio file and of every video's own sound once, in the
    // background (a video without an audio track just gets an empty waveform = nothing drawn).
    LaunchedEffect(tracks) {
        tracks.flatMap { it.clips }
            .filter { it.uri != null && (it.type == LayerType.AUDIO || (it.type == LayerType.VIDEO && !it.isImage)) }
            .forEach {
                val dur = maxOf(it.sourceDurationMs, it.trimStartMs + (it.durationMs * it.speed).toLong())
                viewModel.requestWaveform(context.applicationContext, it.uri!!, dur)
            }
    }

    // Real media clips across all tracks in timeline order.
    // IMPORTANT: only key player/scrub effects on *playback-affecting* fields
    // (uri / trim / duration / image). Transform/volume/slider edits must NOT
    // restart ExoPlayer or the scrub collector — that was the main lag source.
    val mediaClips = remember(tracks) {
        tracks.flatMap { it.clips }
            .filter { it.type == LayerType.VIDEO && it.uri != null }.sortedBy { it.startMs }
    }
    val mediaClipSignature = remember(tracks) {
        mediaClips.map { listOf(it.id, it.uri, it.trimStartMs, it.durationMs, it.isImage, it.speed) }
    }
    LaunchedEffect(mediaClipSignature) {
        editorPlayer.setClips(mediaClips, viewModel.currentPlayheadMs.value)
    }
    // The old single player only feeds the full-screen editors (Transform / Crop / Shape): always silent.
    LaunchedEffect(Unit) { editorPlayer.setMuted(true) }

    // ── Scrub clock ───────────────────────────────────────────────────────────
    // Timeline UI is always instant (graphicsLayer). While the playhead is moving we NEVER touch
    // ExoPlayer: every video layer under the playhead gets a small picture from ScrubPreview (the
    // background-built frame strip first, a single retriever frame as backup). After the
    // finger/fling settles we do one EXACT ExoPlayer seek.
    // Keyed on mediaClipSignature (not full tracks) so transform sliders don't restart this; the
    // current clips are read through liveMediaClips (a layer moved in time keeps the same signature).
    val liveMediaClips by rememberUpdatedState(mediaClips)
    // The background strip builder steps aside while playing / exporting (hardware decoders are few).
    LaunchedEffect(isPlaying, isExporting, nativeActive) { scrubPreview.paused = isPlaying || isExporting || nativeActive }
    // Start building the strips as soon as a video is on the timeline, nearest to the playhead first.
    LaunchedEffect(mediaClipSignature) {
        scrubPreview.warmUp(liveMediaClips, viewModel.currentPlayheadMs.value)
    }
    LaunchedEffect(isPlaying, mediaClipSignature) {
        if (isPlaying || mediaClips.isEmpty()) {
            isActivelyScrubbing = false
            return@LaunchedEffect
        }
        scrubFrames.clear()
        var lastMs = viewModel.currentPlayheadMs.value
        var lastAt = android.os.SystemClock.uptimeMillis()
        viewModel.currentPlayheadMs.collectLatest { ms ->
            val now = android.os.SystemClock.uptimeMillis()
            val moved = kotlin.math.abs(ms - lastMs)
            // Timeline ms per second of finger movement (picks the step of the slow path).
            val speed = moved * 1000L / (now - lastAt).coerceAtLeast(1L)
            lastMs = ms
            lastAt = now
            // Ignore tiny / same-position updates (e.g. settle echo).
            if (moved < 16L) return@collectLatest
            if (nativeActiveNow) {
                // The C++ preview seeks its own decoders: no scrub pictures, just the final exact seek.
                delay(200L)
                editorPlayer.setScrubbing(false)
                editorPlayer.seekTo(ms, force = true)
                return@collectLatest
            }

            isActivelyScrubbing = true
            val found = scrubPreview.frames(liveMediaClips, ms, speed > 2500L) { id, shown ->
                // A slower picture arrived later (main thread).
                scrubFrames[id] = shown
            }
            for ((id, shown) in found) {
                if (scrubFrames[id]?.bitmap !== shown.bitmap) scrubFrames[id] = shown
            }
            delay(200L)
            isActivelyScrubbing = false
            editorPlayer.setScrubbing(false)
            editorPlayer.seekTo(ms, force = true)
        }
    }
    remember { com.aftermotion.studio.engine.FillImages.init(context) }

    // Image fill: the picture picked in the Color editor is stored on the layer being edited.
    var editingColorIdForPicker by remember { mutableStateOf<String?>(null) }
    val fillImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val id = editingColorIdForPicker
        if (uri != null && id != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.updateClip(id) {
                it.copy(fillMode = com.aftermotion.studio.model.FILL_IMAGE, fillImageUri = uri.toString())
            }
        }
    }

    // Replace: pick a new picture/video for the selected layer.
    val replacePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val items = MediaImporter.read(context, listOf(uri))
                if (items.isEmpty()) {
                    snackbarHostState.showSnackbar("Could not read the selected file")
                } else {
                    viewModel.replaceSelectedClipWithMedia(items.first())
                }
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val items = MediaImporter.read(context, uris)
                if (items.isEmpty()) {
                    snackbarHostState.showSnackbar("Could not read the selected files")
                } else {
                    viewModel.importMedia(items)
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { u ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            scope.launch {
                val items = MediaImporter.read(context, uris)
                if (items.isEmpty()) {
                    snackbarHostState.showSnackbar("Could not read the selected audio")
                } else {
                    viewModel.importAudio(items)
                }
            }
        }
    }

    // Dialog & Sheet States
    var showAspectRatioSheet by remember { mutableStateOf(false) }
    var showResolutionSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportDone by remember { mutableStateOf(false) }
    var exportedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var timelinePxPerMs by remember { mutableFloatStateOf(TIMELINE_PX_PER_MS) }
    var showFullscreenPlayer by remember { mutableStateOf(false) }
    var showAudioVolumeSheet by remember { mutableStateOf(false) }
    var showAudioFadeSheet by remember { mutableStateOf(false) }
    var showAudioSpeedSheet by remember { mutableStateOf(false) }
    var showAddTextSheet by remember { mutableStateOf(false) }
    var showAddShapeSheet by remember { mutableStateOf(false) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showStickerSpeedSheet by remember { mutableStateOf(false) }
    var showEditShapeMode by remember { mutableStateOf(false) }
    var showClipEffectsSheet by remember { mutableStateOf(false) }
    var showClipAnimationsSheet by remember { mutableStateOf(false) }
    var showClipReplaceSheet by remember { mutableStateOf(false) }
    var showClipBlendSheet by remember { mutableStateOf(false) }
    var showClipCropSheet by remember { mutableStateOf(false) }
    var showAppliedEffectsMode by remember { mutableStateOf(false) }
    var showTransformMode by remember { mutableStateOf(false) }

    // Play / pause: the view model's wall clock drives the playhead (see VideoEditorViewModel.play).
    // The old single player only plays (silently) while a full-screen editor shows it.
    val oldPlayerVisible = showTransformMode || showEditShapeMode || showClipCropSheet
    // Full-screen editors (Shape / Crop) show the media layers themselves.
    val editorMediaLayers = remember(tracks, playheadState.value) {
        val t = playheadState.value
        tracks.asReversed().flatMap { it.clips }
            .filter { it.type == LayerType.VIDEO && it.isVisible && it.uri != null && it.isActiveAt(t) }
    }
    val editorUseTexture = remember(tracks) {
        tracks.sumOf { tr -> tr.clips.count { it.type == LayerType.VIDEO && it.isVisible && it.uri != null && !it.isImage } } > 1 ||
            tracks.any { tr -> tr.clips.any { it.isVisible && it.hasBlending } }
    }
    LaunchedEffect(isPlaying, mediaClipSignature, oldPlayerVisible) {
        if (isPlaying) isActivelyScrubbing = false
        if (isPlaying && oldPlayerVisible && mediaClips.isNotEmpty()) {
            editorPlayer.setScrubbing(false)
            editorPlayer.seekTo(viewModel.currentPlayheadMs.value, force = true)
            editorPlayer.player.play()
        } else {
            editorPlayer.player.pause()
        }
    }

    // Text edit: id of the text layer that is being edited (null = closed).
    var editingTextId by remember { mutableStateOf<String?>(null) }
    // Color (fill) editor: id of the shape / text layer whose fill is being edited (null = closed).
    var editingColorId by remember { mutableStateOf<String?>(null) }
    // Blending editor (opacity + blend mode): id of the layer being edited (null = closed).
    var editingBlendId by remember { mutableStateOf<String?>(null) }
    // Eyedropper: while set, a tap on the canvas picks a color and passes it to this callback.
    var eyedropperCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    // System back / gesture back closes Transform first instead of leaving the editor.
    BackHandler(enabled = showTransformMode) { showTransformMode = false }
    // Back leaves multi-select first.
    BackHandler(enabled = multiSelectIds.isNotEmpty() && !showTransformMode && editingTextId == null && editingColorId == null && editingBlendId == null) {
        viewModel.exitMultiSelect()
    }
    BackHandler(enabled = editingBlendId != null) { editingBlendId = null }
    BackHandler(enabled = editingTextId != null) {
        if (eyedropperCallback != null) eyedropperCallback = null else editingTextId = null
    }
    BackHandler(enabled = editingColorId != null) {
        if (eyedropperCallback != null) eyedropperCallback = null else editingColorId = null
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.aftermotion.studio.ui.theme.LocalCanvasColor provides androidx.compose.ui.graphics.Color(canvasColor)
    ) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = {
            androidx.compose.foundation.layout.Column {
                cutoutProgress?.let { p ->
                    com.aftermotion.studio.ui.components.CutoutProgressBar(p, cutoutLabel, onCancel = { cutoutJob?.cancel() })
                }
                captionStatus?.let { st ->
                    com.aftermotion.studio.ui.components.CutoutProgressBar(st.progress, st.label, onCancel = { captionJob?.cancel() })
                }
                faceProgress?.let { p ->
                    com.aftermotion.studio.ui.components.CutoutProgressBar(p, faceLabel, onCancel = { faceJob?.cancel() })
                }
                trackProgress?.let { p ->
                    com.aftermotion.studio.ui.components.CutoutProgressBar(
                        p, "Tracking the object…", onCancel = { trackStop.set(true) }, actionLabel = "Stop"
                    )
                }
                SnackbarHost(snackbarHostState)
            }
        },
        containerColor = EditorBackground
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val totalHeight = maxHeight

            // Proportions matching user prompt exactly:
            // Header: 1/18 of screen
            // Canvas: 9/18 of screen
            // Play, full screen section: 1/18 of screen
            // Timescale: 1/30 of screen
            // Layer panel: 5/18 of screen
            // Bottom toolbar: remaining screen height (~2/18)
            val headerH = (totalHeight * (1f / 18f)).coerceAtLeast(44.dp)
            val canvasH = (totalHeight * (9f / 18f)).coerceAtLeast(260.dp)
            val playControlsH = (totalHeight * (1f / 18f)).coerceAtLeast(44.dp)
            val timescaleH = (totalHeight * (1f / 30f)).coerceAtLeast(24.dp)
            val layerPanelH = (totalHeight * (5f / 18f)).coerceAtLeast(160.dp)
            val toolbarH = (totalHeight - (headerH + canvasH + playControlsH + timescaleH + layerPanelH)).coerceAtLeast(54.dp)

            // Alight-style editor panels (Transform / Color / Blending): the play-controls card stays
            // exactly where it is. The panel starts right under it, at the timescale, so the selected
            // layer's strip takes the first layer row and the other layers are hidden behind the panel.
            val panelTop = headerH + canvasH + playControlsH
            val panelH = (totalHeight - panelTop).coerceAtLeast(200.dp)

            // The selected layer, drawn by the real timeline panel (identical look) as the first row.
            val layerRow: @Composable (String) -> Unit = { id ->
                com.aftermotion.studio.ui.components.SelectedLayerRow(
                    clipId = id,
                    tracks = tracks,
                    playheadMs = playheadProvider,
                    totalDurationMs = totalDurationMs,
                    onSeekTo = { viewModel.seekTo(it) },
                    onScrubStart = { viewModel.pause() },
                    isPlaying = isPlaying,
                    onMoveClipInTime = { cid, delta -> viewModel.moveClipInTime(cid, delta) },
                    onBeginClipTimeDrag = { viewModel.beginAdjust() },
                    onTrimClipEdge = { cid, left, d -> viewModel.trimClipEdge(cid, left, d) },
                    onToggleClipVisibility = { cid -> viewModel.toggleClipVisibility(cid) },
                    pxPerMs = timelinePxPerMs,
                    onPxPerMsChange = { timelinePxPerMs = it },
                    waveforms = waveforms
                )
            }
            var transformResetTick by remember { mutableStateOf(0) }

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Header (1/18 of screen)
                EditorHeader(
                    height = headerH,
                    aspectRatio = selectedAspectRatio,
                    onBackClick = {
                    },
                    onAspectRatioClick = { showAspectRatioSheet = true },
                    onExportClick = {
                        // Fresh state every time: a previous export must not leak into this one.
                        exportDone = false
                        exportedUri = null
                        viewModel.setExporting(false)
                        viewModel.setExportProgress(0f)
                        showExportDialog = true
                    }
                )

                // 2. Canvas Area (9/18 of screen)
                val trackOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
                    if (trackSetup != null) {
                        {
                            TrackSetupOverlay(
                                circle = trackCircle,
                                onCircle = { trackCircle = it },
                                onStart = startTracking,
                                onCancel = { trackSetup = null }
                            )
                        }
                    } else if (trackRunning) {
                        { TrackRunOverlay(trackLive) }
                    } else null
                CanvasArea(
                    nativePreview = nativePreview,
                    height = canvasH,
                    aspectRatio = selectedAspectRatio,
                    playheadMs = playheadProvider,
                    isPlaying = isPlaying,
                    tracks = tracks,
                    selectedClipId = selectedClipId,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    player = editorPlayer.player,
                    videoPlayerFor = { id -> videoPreview.playerFor(id) },
                    hasMainMedia = mediaClips.isNotEmpty(),
                    isScrubbing = isActivelyScrubbing && !isPlaying,
                    scrubFrameOf = scrubFrameOf,
                    onUpdateClipTransform = { id, x, y -> viewModel.updateClipTransform(id, x, y) },
                    onClipSelect = { viewModel.selectClip(it) },
                    onTextDoubleTap = { id ->
                        viewModel.selectClip(id)
                        viewModel.beginTextEdit()
                        editingTextId = id
                    },
                    onPreviewFrame = { w, d -> previewFrameWidthPx = w; previewDensity = d },
                    frameOverlay = trackOverlay,
                    onSelectionBegin = { viewModel.beginAdjust() },
                    onSelectionTransform = { id, sc, rot ->
                        viewModel.setScaleRotation(id, sc, rot)
                    },
                    gradientEditId = editingColorId,
                    onGradientPoints = { id, x1, y1, x2, y2 ->
                        viewModel.updateClip(id) {
                            it.copy(gradX1 = x1, gradY1 = y1, gradX2 = x2, gradY2 = y2)
                        }
                    }
                )

                // 3. Play & Full Screen Section (1/18 of screen) — always visible (universal)
                PlayControlsSection(
                    height = playControlsH,
                    isPlaying = isPlaying,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onFullScreenClick = { showFullscreenPlayer = true },
                    onResolutionClick = { showResolutionSheet = true },
                    onPreviousClick = { viewModel.jumpPrevious() },
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onNextClick = { viewModel.jumpNext() },
                    onUndoClick = { viewModel.undo() },
                    onRedoClick = { viewModel.redo() }
                )

                // 4. Timescale Section (1/30 of screen)
                TimescaleSection(
                    height = timescaleH,
                    playheadMs = playheadProvider,
                    totalDurationMs = totalDurationMs,
                    pxPerMs = timelinePxPerMs
                )

                // 5. Layer Panel (5/18 of screen)
                TimelineLayerPanel(
                    height = layerPanelH,
                    tracks = tracks,
                    playheadMs = playheadProvider,
                    totalDurationMs = totalDurationMs,
                    selectedClipId = selectedClipId,
                    isMasterMuted = isMasterMuted,
                    onSeekTo = { viewModel.seekTo(it) },
                    onScrubStart = { viewModel.pause() },
                    isPlaying = isPlaying,
                    // While several layers are checked, taps/drags on layer bodies must not
                    // switch to single selection (that would leave multi-select).
                    onClipSelect = { id -> if (multiSelectIds.isEmpty()) viewModel.selectClip(id) },
                    multiSelectIds = multiSelectIds,
                    onLongPressLane = { id -> viewModel.enterMultiSelect(id) },
                    onToggleMulti = { id -> viewModel.toggleMultiSelect(id) },
                    onMultiAction = { viewModel.applyMultiAction(it) },
                    onSplitClip = {
                        viewModel.splitSelectedClip()
                    },
                    onDeleteClip = {
                        viewModel.deleteSelectedClip()
                    },
                    onDuplicateClip = {
                        viewModel.duplicateSelectedClip()
                    },
                    onToggleMasterAudio = {
                        viewModel.toggleMasterAudio()
                    },
                    onFlipHorizontal = { viewModel.flipHorizontalSelectedClip() },
                    onFlipVertical = { viewModel.flipVerticalSelectedClip() },
                    onReplace = {
                        val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                        if (target != null && target.type == LayerType.VIDEO) {
                            replacePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        } else {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            scope.launch {
                                snackbarHostState.showSnackbar("Replace works on video and photo layers")
                            }
                        }
                    },
                    onSpeed = run {
                        val sel = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                        if (sel != null && (sel.type == LayerType.AUDIO ||
                                (sel.type == LayerType.VIDEO && !sel.isImage && sel.uri != null))
                        ) {
                            {
                                viewModel.beginAdjust()
                                showAudioSpeedSheet = true
                            }
                        } else if (sel != null && sel.type == LayerType.STICKER) {
                            {
                                viewModel.beginAdjust()
                                showStickerSpeedSheet = true
                            }
                        } else null
                    },
                    waveforms = waveforms,
                    onMoveClipInTime = { id, delta -> viewModel.moveClipInTime(id, delta) },
                    onBeginClipTimeDrag = { viewModel.beginAdjust() },
                    onReorderLane = { from, to -> viewModel.reorderLane(from, to) },
                    onTrimClipEdge = { id, left, d -> viewModel.trimClipEdge(id, left, d) },
                    onToggleClipVisibility = { id -> viewModel.toggleClipVisibility(id) },
                    pxPerMs = timelinePxPerMs,
                    onPxPerMsChange = { timelinePxPerMs = it }
                )

                // 6. Footer: Clip Edit Tools Panel if clip is selected, otherwise Main Bottom Toolbar
                AnimatedContent(
                    targetState = selectedClipId,
                    // Fast, springy swap (Alight Motion style): new bar slides up a little and fades in,
                    // the old one fades out quickly. No slow tween, no scale pop.
                    transitionSpec = {
                        (fadeIn(androidx.compose.animation.core.tween(110, delayMillis = 40)) +
                            androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.8f,
                                    stiffness = 900f
                                ),
                                initialOffsetY = { it / 3 }
                            )) togetherWith
                            fadeOut(androidx.compose.animation.core.tween(80))
                    },
                    label = "toolbarTransition"
                ) { targetClipId ->
                    if (targetClipId != null) {
                        val targetIsAudio = tracks.any { t ->
                            t.clips.any { it.id == targetClipId && it.type == LayerType.AUDIO }
                        }
                        if (targetIsAudio) AudioClipToolsPanel(
                            height = toolbarH,
                            onDeselect = { viewModel.selectClip(null) },
                            onVolume = {
                                viewModel.beginAdjust()
                                showAudioVolumeSheet = true
                            },
                            onFade = {
                                viewModel.beginAdjust()
                                showAudioFadeSheet = true
                            },
                            onSpeed = {
                                viewModel.beginAdjust()
                                showAudioSpeedSheet = true
                            },
                            onReverse = comingSoon,
                            onCaptions = { requestCaptions(targetClipId) }
                        ) else ClipEditToolsPanel(
                            height = toolbarH,
                            onDeselect = { viewModel.selectClip(null) },
                            onEffects = comingSoon,
                            onAnimations = comingSoon,
                            onColor = {
                                // Color = the fill of a shape or text layer (none / solid / gradient).
                                val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                                if (target != null && (target.type == LayerType.SHAPE || target.type == LayerType.TEXT)) {
                                    if (!target.isActiveAt(playheadState.value)) {
                                        viewModel.seekTo(target.startMs)
                                    }
                                    viewModel.beginAdjust()
                                    editingColorId = target.id
                                } else {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Color works on shape and text layers")
                                    }
                                }
                            },
                            onTransform = {
                                // Open Transform on the selected layer: if the playhead is outside it,
                                // jump to the layer's start so the layer is visible straight away.
                                val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                                if (target != null && !target.isActiveAt(playheadState.value)) {
                                    viewModel.seekTo(target.startMs)
                                }
                                showTransformMode = true
                            },
                            onCrop = {
                                // Open Crop on the selected layer; jump to its start if the playhead is outside it.
                                val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                                if (target != null && !target.isActiveAt(playheadState.value)) {
                                    viewModel.seekTo(target.startMs)
                                }
                                viewModel.beginAdjust()
                                showClipCropSheet = true
                            },
                            onBlend = {
                                // Blending works on every visual layer: open it on the selected one and
                                // jump to the layer's start if the playhead is outside it.
                                val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                                if (target != null) {
                                    if (!target.isActiveAt(playheadState.value)) {
                                        viewModel.seekTo(target.startMs)
                                    }
                                    viewModel.beginAdjust()
                                    editingBlendId = target.id
                                }
                            },
                            onCutout = {
                                // Cutout (person only, RVM): a photo is replaced by its cutout picture;
                                // a video gets a mask for EVERY frame (preview + export). Tap again to remove.
                                val target = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                                val srcUri = target?.uri
                                snackbarHostState.currentSnackbarData?.dismiss()
                                if (target == null || target.type != LayerType.VIDEO || srcUri == null) {
                                    scope.launch { snackbarHostState.showSnackbar("Cutout works on video and photo layers") }
                                } else if (cutoutProgress != null) {
                                    scope.launch { snackbarHostState.showSnackbar("A cutout is already running") }
                                } else if (!target.isImage && target.cutoutDir != null) {
                                    viewModel.beginAdjust()
                                    viewModel.updateClip(target.id) {
                                        it.copy(cutoutDir = null, cutoutFps = 0f, cutoutStartMs = 0L, cutoutFrames = 0)
                                    }
                                    scope.launch { snackbarHostState.showSnackbar("Cutout removed") }
                                } else if (target.isImage) {
                                    scope.launch { snackbarHostState.showSnackbar("Cutting out the person…") }
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            runCatching {
                                                val src = CutoutEngine.loadPhoto(context, srcUri.toUri())
                                                if (src == null) null
                                                else {
                                                    val (out, engine) = CutoutEngine.cutoutAuto(context, src)
                                                    Pair(Triple(CutoutEngine.savePng(context, out), out.width, out.height), engine)
                                                }
                                            }
                                        }
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val done = result.getOrNull()
                                        if (done == null) {
                                            scope.launch { snackbarHostState.showSnackbar("Cutout failed: " + (result.exceptionOrNull()?.message ?: "could not read the picture")) }
                                        } else {
                                            val (saved, w, h) = done.first
                                            viewModel.beginAdjust()
                                            viewModel.updateClip(target.id) {
                                                it.copy(uri = saved, mimeType = "image/png", name = it.name + " cutout", sourceAspect = w / h.toFloat())
                                            }
                                            scope.launch { snackbarHostState.showSnackbar("Cutout done - " + done.second) }
                                        }
                                    }
                                } else if (!com.aftermotion.studio.engine.RvmEngine.isAvailable(context)) {
                                    askModelDownload = target
                                } else {
                                    runVideoCutout(target, false)
                                }
                            },
                            onTrack = if (tracks.flatMap { it.clips }.any {
                                    it.id == targetClipId && it.type == LayerType.VIDEO && !it.isImage && it.uri != null
                                }
                            ) {
                                { requestTrack(targetClipId) }
                            } else null,
                            onCaptions = if (tracks.flatMap { it.clips }.any {
                                    it.id == targetClipId && it.type == LayerType.VIDEO && !it.isImage && it.uri != null
                                }
                            ) {
                                { requestCaptions(targetClipId) }
                            } else null,
                            onFaceAi = if (tracks.flatMap { it.clips }.any {
                                    it.id == targetClipId && it.type == LayerType.VIDEO && !it.isImage && it.uri != null
                                }
                            ) {
                                { requestFaceAi(targetClipId) }
                            } else null,
                            onEditText = if (tracks.flatMap { it.clips }
                                    .any { it.id == targetClipId && it.type == LayerType.TEXT }
                            ) {
                                {
                                    viewModel.beginTextEdit()
                                    editingTextId = targetClipId
                                }
                            } else null
                        )
                    } else {
                        // Layer-add UI moved to bottom-right radial FAB (AddLayerFab).
                        // Keep a slim spacer so the Column layout still accounts for footer height.
                        androidx.compose.foundation.layout.Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(toolbarH.coerceAtMost(12.dp))
                        )
                    }
                }
            }

            // Bottom-right radial "+" menu for adding layers (replaces old BottomToolbar).
            // Hidden while a clip is selected (clip tools take the footer) or while an
            // editor panel (text / color / transform / blend / shape) is open.
            val hideAddFab = selectedClipId != null ||
                editingTextId != null ||
                editingColorId != null ||
                editingBlendId != null ||
                showTransformMode ||
                showEditShapeMode ||
                showClipCropSheet ||
                showFullscreenPlayer
            if (!hideAddFab) {
                AddLayerFab(
                    onAudioClick = { audioPicker.launch(arrayOf("audio/*")) },
                    onTextClick = {
                        val remaining = totalDurationMs - playheadState.value
                        val id = viewModel.addTextLayer(
                            if (totalDurationMs > 0L && remaining >= 1000L) minOf(4000L, remaining) else 4000L
                        )
                        if (id.isNotEmpty()) editingTextId = id
                    },
                    onShapeClick = { showAddShapeSheet = true },
                    onTransformClick = comingSoon,
                    onMediaClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onStickersClick = { showStickerSheet = true },
                    onEffectsClick = comingSoon,
                    onBrightnessClick = comingSoon,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(40f)
                )
            }

            // ---- Text edit -------------------------------------------------------------------
            // Same idea as Transform: the real canvas in the middle stays visible (it shows every
            // change live), the header becomes "Text edit" and the lower area becomes the editor.
            val editingText = editingTextId?.let { id -> tracks.flatMap { it.clips }.find { it.id == id } }
            LaunchedEffect(editingText == null, editingTextId) {
                if (editingText == null && editingTextId != null) editingTextId = null
            }
            if (editingText != null) {
                val closeText = {
                    eyedropperCallback = null
                    editingTextId = null
                }
                com.aftermotion.studio.ui.components.TransformHeaderBar(
                    height = headerH,
                    title = "Text edit",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(31f)
                        .enterPop()
                )

                // Same layout as Color/Blending. While the keyboard is open only the text box +
                // chips remain, raised above the keyboard.
                val textImeVisible = WindowInsets.isImeVisible
                val textRowH = com.aftermotion.studio.ui.components.laneHeightFor(editingText.type) + 8.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(if (textImeVisible) Modifier else Modifier.height(panelH))
                        .zIndex(30f)
                        .enterPop(fromBottom = true)
                        .background(EditorBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .swipeDownToDismiss(onDismiss = closeText)
                ) {
                    if (!textImeVisible) {
                        com.aftermotion.studio.ui.components.TimescaleSection(
                            height = timescaleH,
                            playheadMs = { playheadState.value },
                            totalDurationMs = totalDurationMs,
                            onBack = closeText
                        )
                        layerRow(editingText.id)
                    }
                    TextEditSheet(
                        clip = editingText,
                        onUpdate = { change -> viewModel.updateClip(editingText.id, change) },
                        onEyedropper = { cb -> eyedropperCallback = cb },
                        modifier = Modifier.fillMaxWidth(),
                        fullHeight = (panelH - timescaleH - textRowH).coerceAtLeast(120.dp)
                    )
                }
                // The layer can disappear while editing (undo / delete): close the editor then.

            }

            // ---- Color (fill) editor -----------------------------------------------------
            // Same idea as Text edit: the canvas stays visible and shows every change live.
            val editingColor = editingColorId?.let { id -> tracks.flatMap { it.clips }.find { it.id == id } }
            LaunchedEffect(editingColor == null, editingColorId) {
                if (editingColor == null && editingColorId != null) editingColorId = null
            }
            if (editingColor != null) {
                val closeColor = {
                    eyedropperCallback = null
                    editingColorId = null
                }
                com.aftermotion.studio.ui.components.TransformHeaderBar(
                    height = headerH,
                    title = "Color",
                    onReset = {

                        // Reset = back to a plain white fill with the default gradient.
                        val d = com.aftermotion.studio.model.TimelineClip(id = "", name = "", type = LayerType.SHAPE, startMs = 0, durationMs = 0, color = 0)
                        viewModel.updateClipNoKeyframes(editingColor.id) {
                            it.copy(
                                keyframes = it.keyframes.filterKeys { p -> !p.isColor },
                                fillMode = com.aftermotion.studio.model.FILL_SOLID, textColor = 0xFFFFFFFF,
                                gradType = d.gradType, gradColor1 = d.gradColor1, gradColor2 = d.gradColor2,
                                gradX1 = d.gradX1, gradY1 = d.gradY1, gradX2 = d.gradX2, gradY2 = d.gradY2,
                                fillImageUri = null, fillImageMode = d.fillImageMode
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(31f)
                        .enterPop()
                )

                // Bottom area: timescale + layer strip + Color editor.
                // Play controls stay visible above (universal).
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = panelTop)
                        .fillMaxWidth()
                        .height(panelH)
                        .zIndex(30f)
                        .enterPop(fromBottom = true)
                        .background(EditorBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .swipeDownToDismiss(onDismiss = closeColor)
                ) {
                    // No TransformPlaybackRow — keep clean look (layer strip + editor only).
                    com.aftermotion.studio.ui.components.TimescaleSection(
                        height = timescaleH,
                        playheadMs = { playheadState.value },
                        totalDurationMs = totalDurationMs,
                        onBack = closeColor
                    )
                    layerRow(editingColor.id)
                    com.aftermotion.studio.ui.components.ColorEditSheet(
                        rawClip = editingColor,
                        currentPlayheadMs = playheadState.value,
                        onToggleKeyframes = { props -> viewModel.toggleKeyframes(editingColor.id, props) },
                        onEasingBegin = { viewModel.beginAdjust() },
                        onEasingChange = { prop, kfTime, easing ->
                            viewModel.setKeyframeEasing(editingColor.id, prop, kfTime, easing)
                        },
                        onUpdate = { change -> viewModel.updateClipColor(editingColor.id, change) },
                        onEyedropper = { cb -> eyedropperCallback = cb },
                        onPickImage = {
                            editingColorIdForPicker = editingColor.id
                            fillImagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onBack = closeColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---- Blending editor ---------------------------------------------------------
            // Same layout as Color: the canvas stays visible and shows opacity / blend live.
            val editingBlend = editingBlendId?.let { id -> tracks.flatMap { it.clips }.find { it.id == id } }
            LaunchedEffect(editingBlend == null, editingBlendId) {
                if (editingBlend == null && editingBlendId != null) editingBlendId = null
            }
            if (editingBlend != null) {
                com.aftermotion.studio.ui.components.TransformHeaderBar(
                    height = headerH,
                    title = "Blending",
                    onReset = {
                        viewModel.updateClip(editingBlend.id) {
                            it.copy(opacity = 100f, blendMode = com.aftermotion.studio.model.BlendModes.NORMAL)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(31f)
                        .enterPop()
                )

                // Play controls stay visible above (universal). Only layer strip + blend editor below.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = panelTop)
                        .fillMaxWidth()
                        .height(panelH)
                        .zIndex(30f)
                        .enterPop(fromBottom = true)
                        .background(EditorBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .swipeDownToDismiss(onDismiss = { editingBlendId = null })
                ) {
                    com.aftermotion.studio.ui.components.TimescaleSection(
                        height = timescaleH,
                        playheadMs = { playheadState.value },
                        totalDurationMs = totalDurationMs,
                        onBack = { editingBlendId = null }
                    )
                    layerRow(editingBlend.id)
                    com.aftermotion.studio.ui.components.BlendEditSheet(
                        clip = editingBlend.at(playheadState.value),
                        rawClip = editingBlend,
                        currentPlayheadMs = playheadState.value,
                        onUpdate = { change -> viewModel.updateClipLive(editingBlend.id, change) },
                        onToggleKeyframes = { props -> viewModel.toggleKeyframes(editingBlend.id, props) },
                        onEasingBegin = { viewModel.beginAdjust() },
                        onEasingChange = { prop, kfTime, easing ->
                            viewModel.setKeyframeEasing(editingBlend.id, prop, kfTime, easing)
                        },
                        onReset = {
                            viewModel.updateClipNoKeyframes(editingBlend.id) {
                                it.copy(
                                    keyframes = it.keyframes.filterKeys { p -> p != com.aftermotion.studio.model.AnimProp.OPACITY },
                                    opacity = 100f,
                                    blendMode = com.aftermotion.studio.model.BlendModes.NORMAL
                                )
                            }
                        },
                        onBack = { editingBlendId = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Eyedropper (Text edit and Color): a tap on the canvas reads the pixel under the finger.
            if (editingText != null || editingColor != null) {
                val picker = eyedropperCallback
                if (picker != null) {
                    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = headerH)
                            .fillMaxWidth()
                            .height(canvasH)
                            .zIndex(32f)
                            .onGloballyPositioned { origin = it.positionInWindow() }
                            .pointerInput(picker) {
                                detectTapGestures { tap ->
                                    pickColorFromWindow(
                                        context,
                                        (origin.x + tap.x).toInt(),
                                        (origin.y + tap.y).toInt()
                                    ) { color ->
                                        if (color != null) picker(color)
                                        else scope.launch {
                                            snackbarHostState.showSnackbar("Could not read that color")
                                        }
                                        eyedropperCallback = null
                                    }
                                }
                            }
                    ) {
                        Text(
                            "Tap the canvas to pick a color",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Transform controls intentionally overlay only the lower timeline/tools area.
            // Keep the canvas viewport and all visible layers completely unchanged.
            if (showTransformMode) {
                val selectedClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                    ?: tracks.flatMap { it.clips }.firstOrNull()

                // Top bar: replaces the editor header while Transform is open (back chevron + title).
                com.aftermotion.studio.ui.components.TransformHeaderBar(
                    height = headerH,
                    onReset = { transformResetTick++ },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(21f)
                        .enterPop()
                )

                // Cover play controls so only clean layer-strip + transform panel is visible
                // Play controls stay visible above (universal). Only layer strip + transform panel below.
                // The canvas in the middle stays untouched.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = panelTop)
                        .fillMaxWidth()
                        .height(panelH)
                        .zIndex(20f)
                        .enterPop(fromBottom = true)
                        .background(EditorBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .swipeDownToDismiss(onDismiss = { showTransformMode = false })
                ) {
                    com.aftermotion.studio.ui.components.TransformView(
                        // The layer as it looks at the playhead (keyframes applied).
                        clip = selectedClip?.at(playheadState.value),
                        aspectRatio = selectedAspectRatio,
                        currentPlayheadMs = playheadState.value,
                        totalDurationMs = totalDurationMs,
                        isPlaying = isPlaying,
                        player = editorPlayer.player,
                        hasMainMedia = mediaClips.isNotEmpty(),
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onDismiss = { showTransformMode = false },
                        panelOnly = true,
                        timescaleHeight = timescaleH,
                        resetTick = transformResetTick,
                        onToggleKeyframe = { prop ->
                            selectedClip?.id?.let { viewModel.toggleKeyframe(it, prop) }
                        },
                        onEasingBegin = { viewModel.beginAdjust() },
                        onEasingChange = { prop, kfTime, easing ->
                            selectedClip?.id?.let { viewModel.setKeyframeEasing(it, prop, kfTime, easing) }
                        },
                        layerRow = selectedClip?.id?.let { id -> @Composable { layerRow(id) } },
                        onApplyTransform = { h, v, sc, rot, px, py, r3x, r3y, warp, z ->
                            val targetId = selectedClip?.id ?: selectedClipId
                            if (targetId != null) {
                                viewModel.updateClipFullTransform(
                                    clipId = targetId,
                                    transformX = h,
                                    transformY = v,
                                    scalePercent = sc,
                                    rotationDeg = rot,
                                    pivotX = px,
                                    pivotY = py,
                                    rot3dX = r3x,
                                    rot3dY = r3y,
                                    warp = warp,
                                    transformZ = z
                                )
                            }
                        }
                    )
                }
            }
            // ---- Audio panels (Volume / Fade / Speed): same layout as the other editors ----------
            val audioClip = tracks.flatMap { it.clips }
                .firstOrNull {
                    it.id == selectedClipId && (it.type == LayerType.AUDIO ||
                        (it.type == LayerType.VIDEO && !it.isImage && it.uri != null))
                }
            val audioPanelTitle = when {
                showAudioVolumeSheet -> "Volume"
                showAudioFadeSheet -> "Fade"
                showAudioSpeedSheet -> "Speed"
                else -> null
            }
            val closeAudioPanel = {
                showAudioVolumeSheet = false
                showAudioFadeSheet = false
                showAudioSpeedSheet = false
            }
            LaunchedEffect(audioClip == null, audioPanelTitle) {
                if (audioClip == null && audioPanelTitle != null) closeAudioPanel()
            }
            BackHandler(enabled = audioPanelTitle != null) { closeAudioPanel() }
            if (audioClip != null && audioPanelTitle != null) {
                com.aftermotion.studio.ui.components.TransformHeaderBar(
                    height = headerH,
                    title = audioPanelTitle,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(31f)
                        .enterPop()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = panelTop)
                        .fillMaxWidth()
                        .height(panelH)
                        .zIndex(30f)
                        .enterPop(fromBottom = true)
                        .background(EditorBackground)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .swipeDownToDismiss(onDismiss = closeAudioPanel)
                ) {
                    com.aftermotion.studio.ui.components.TimescaleSection(
                        height = timescaleH,
                        playheadMs = { playheadState.value },
                        totalDurationMs = totalDurationMs,
                        onBack = closeAudioPanel
                    )
                    layerRow(audioClip.id)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (showAudioVolumeSheet) {
                            AudioVolumeSheet(
                                volume = audioClip.volume,
                                onVolumeChange = { viewModel.setClipVolume(audioClip.id, it) },
                                onDismiss = closeAudioPanel
                            )
                        } else if (showAudioFadeSheet) {
                            AudioFadeSheet(
                                fadeInMs = audioClip.fadeInMs,
                                fadeOutMs = audioClip.fadeOutMs,
                                maxFadeMs = audioClip.durationMs / 2,
                                onChange = { fin, fout -> viewModel.setClipFades(audioClip.id, fin, fout) },
                                onDismiss = closeAudioPanel
                            )
                        } else if (showAudioSpeedSheet) {
                            AudioSpeedSheet(
                                speed = audioClip.at(playheadState.value).speed,
                                onSpeedChange = { viewModel.setClipSpeed(audioClip.id, it) },
                                onDismiss = closeAudioPanel,
                                rawClip = audioClip,
                                playheadMs = playheadState.value,
                                onLiveSpeed = { v ->
                                    viewModel.updateClipLive(audioClip.id) { it.copy(speed = v) }
                                },
                                onToggleKeyframes = { props -> viewModel.toggleKeyframes(audioClip.id, props) },
                                onEasingBegin = { viewModel.beginAdjust() },
                                onEasingChange = { prop, kfTime, easing ->
                                    viewModel.setKeyframeEasing(audioClip.id, prop, kfTime, easing)
                                }
                            )
                        }
                    }
                }
            }

        if (showClipCropSheet) {
            val selectedClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
                ?: tracks.flatMap { it.clips }.firstOrNull()

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showClipCropSheet = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.aftermotion.studio.ui.components.CropView(
                    // The layer as it looks at the playhead (keyframes applied).
                    clip = selectedClip?.at(playheadState.value),
                    onToggleKeyframes = { props ->
                        selectedClip?.id?.let { viewModel.toggleKeyframes(it, props) }
                    },
                    onEasingBegin = { viewModel.beginAdjust() },
                    onEasingChange = { prop, kfTime, easing ->
                        selectedClip?.id?.let { viewModel.setKeyframeEasing(it, prop, kfTime, easing) }
                    },
                    aspectRatio = selectedAspectRatio,
                    currentPlayheadMs = playheadState.value,
                    totalDurationMs = totalDurationMs,
                    isPlaying = isPlaying,
                    player = editorPlayer.player,
                    hasMainMedia = mediaClips.isNotEmpty(),
                    videoPlayerFor = { id -> videoPreview.playerFor(id) },
                    useTexture = editorUseTexture,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onDismiss = { showClipCropSheet = false },
                    onApplyCrop = { w, h, horiz, vert, preset ->
                        val targetId = selectedClip?.id ?: selectedClipId
                        if (targetId != null) {
                            viewModel.updateClipCrop(
                                clipId = targetId,
                                cropWidthPercent = w,
                                cropHeightPercent = h,
                                cropHorizontalPercent = horiz,
                                cropVerticalPercent = vert,
                                cropPreset = preset
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(EditorBackground)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .enterPop(),
                    chrome = com.aftermotion.studio.ui.components.CropChrome(
                        headerH = headerH,
                        canvasH = canvasH,
                        playControlsH = playControlsH,
                        timescaleH = timescaleH,
                        layerRow = { selectedClip?.id?.let { layerRow(it) } },
                        onFullScreen = { showFullscreenPlayer = true },
                        onResolution = { showResolutionSheet = true },
                        onPrevious = { viewModel.jumpPrevious() },
                        onNext = { viewModel.jumpNext() }
                    )
                )
            }
        }


        }
    }
    }


    // Modal Sheets & Dialogs
    if (showAspectRatioSheet) {
        AspectRatioSheet(
            currentRatio = selectedAspectRatio,
            originalAspect = originalAspect,
            onRatioSelected = { viewModel.setAspectRatio(it) },
            canvasColor = canvasColor,
            onCanvasColor = { viewModel.setCanvasColor(it) },
            resolution = exportSettings.resolution,
            onResolution = { viewModel.setExportResolution(it) },
            onDismiss = { showAspectRatioSheet = false }
        )
    }

    if (showResolutionSheet) {
        ResolutionSheet(
            currentResolution = selectedResolution,
            onResolutionSelected = { viewModel.setResolution(it) },
            onDismiss = { showResolutionSheet = false }
        )
    }

    if (showExportDialog) {
        ExportModalDialog(
            isExporting = isExporting,
            isCompleted = exportDone,
            progress = exportProgress,
            resolutionLabel = exportSettings.resolution,
            frameRate = exportSettings.frameRate,
            onResolutionChange = { viewModel.setExportResolution(it) },
            onFrameRateChange = { viewModel.setExportFrameRate(it) },
            quality = exportSettings.quality,
            onQualityChange = { viewModel.setExportQuality(it) },
            bitrateKbps = exportSettings.customBitrateKbps,
            onBitrateChange = { viewModel.setExportBitrate(it) },
            useNativeExport = exportSettings.useNativeExport,
            onUseNativeExportChange = { viewModel.setUseNativeExport(it) },
            onStartExport = {
                exportDone = false
                exportedUri = null
                viewModel.pause()
                viewModel.setExporting(true)
                exporter.start(
                    tracks = tracks,
                    aspectRatio = selectedAspectRatio,
                    settings = exportSettings.copy(canvasColor = canvasColor),
                    previewFrameWidthPx = previewFrameWidthPx,
                    previewDensity = previewDensity,
                    onProgress = { viewModel.setExportProgress(it) },
                    onDone = { uri ->
                        viewModel.finishExport()
                        exportedUri = uri
                        exportDone = true
                        scope.launch { snackbarHostState.showSnackbar("Saved to Movies/After Motion") }
                    },
                    onError = { msg ->
                        viewModel.setExporting(false)
                        viewModel.setExportProgress(0f)
                        showExportDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Export failed: $msg") }
                    }
                )
            },
            onCancel = {
                exporter.cancel()
                viewModel.setExporting(false)
                viewModel.setExportProgress(0f)
                exportDone = false
                showExportDialog = false
            },
            onDismiss = {
                exportDone = false
                exportedUri = null
                viewModel.setExportProgress(0f)
                showExportDialog = false
            },
            onShare = {
                exportedUri?.let { u ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, u)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        )
    }

    if (showAddTextSheet) {
        AddTextSheet(
            onAddText = { text, color ->
                viewModel.addClip(
                    type = LayerType.TEXT,
                    name = "Text: $text",
                    durationMs = 7000L,
                    text = text,
                    textColor = color
                )
            },
            onDismiss = { showAddTextSheet = false }
        )
    }

    if (showStickerSpeedSheet) {
        val stickerClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
        if (stickerClip != null && stickerClip.type == LayerType.STICKER) {
            StickerSpeedSheet(
                clip = stickerClip,
                playheadMs = playheadState.value,
                onSpeedChange = { viewModel.setClipSpeed(stickerClip.id, it) },
                onLiveSpeed = { v -> viewModel.updateClipLive(stickerClip.id) { it.copy(speed = v) } },
                onToggleKeyframes = { props -> viewModel.toggleKeyframes(stickerClip.id, props) },
                onEasingBegin = { viewModel.beginAdjust() },
                onEasingChange = { prop, kfTime, easing ->
                    viewModel.setKeyframeEasing(stickerClip.id, prop, kfTime, easing)
                },
                onDismiss = { showStickerSpeedSheet = false }
            )
        }
    }

    if (showStickerSheet) {
        StickerPickerSheet(
            onPick = { item ->
                // Sticker at the playhead, up to 4 s (or what is left of the timeline).
                val remaining = totalDurationMs - playheadState.value
                val dur = if (totalDurationMs > 0L && remaining >= 1000L) minOf(4000L, remaining) else 4000L
                viewModel.addSticker(item.assetPath, item.name, dur)
            },
            onDismiss = { showStickerSheet = false }
        )
    }

    if (showAddShapeSheet) {
        AddShapeSheet(
            onAddShape = { name, pattern ->
                val clipDuration = if (totalDurationMs > 0L && playheadState.value < totalDurationMs) {
                    minOf(4000L, (totalDurationMs - playheadState.value).coerceAtLeast(1000L))
                } else {
                    4000L
                }
                viewModel.addShape(
                    name = name,
                    pattern = pattern,
                    durationMs = clipDuration
                )
            },
            onDismiss = { showAddShapeSheet = false }
        )
    }

    if (showFullscreenPlayer) {
        FullscreenPlayerModal(
            aspectRatio = selectedAspectRatio,
            currentPlayheadMs = playheadState.value,
            totalDurationMs = totalDurationMs,
            isPlaying = isPlaying,
            onTogglePlay = { viewModel.togglePlayPause() },
            onDismiss = { showFullscreenPlayer = false }
        )
    }

    if (showEditShapeMode) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEditShapeMode = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.aftermotion.studio.ui.components.EditShapeView(
                aspectRatio = selectedAspectRatio,
                currentPlayheadMs = playheadState.value,
                totalDurationMs = totalDurationMs,
                isPlaying = isPlaying,
                player = editorPlayer.player,
                hasMainMedia = mediaClips.isNotEmpty(),
                mediaLayers = editorMediaLayers,
                videoPlayerFor = { id -> videoPreview.playerFor(id) },
                useTexture = editorUseTexture,
                onTogglePlay = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it) },
                onDismiss = { showEditShapeMode = false },
                onSaveShape = { shapeType, w, h, color, feather, offX, offY, strokeW, strokeCol ->
                    val clipDuration = if (totalDurationMs > 0L && playheadState.value < totalDurationMs) {
                        minOf(4000L, (totalDurationMs - playheadState.value).coerceAtLeast(1000L))
                    } else {
                        4000L
                    }
                    val a = (color.alpha * 255).toInt().coerceIn(0, 255)
                    val r = (color.red * 255).toInt().coerceIn(0, 255)
                    val g = (color.green * 255).toInt().coerceIn(0, 255)
                    val b = (color.blue * 255).toInt().coerceIn(0, 255)
                    val argb = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
                    val colorLong = argb.toLong() and 0xFFFFFFFFL

                    val strA = (strokeCol.alpha * 255).toInt().coerceIn(0, 255)
                    val strR = (strokeCol.red * 255).toInt().coerceIn(0, 255)
                    val strG = (strokeCol.green * 255).toInt().coerceIn(0, 255)
                    val strB = (strokeCol.blue * 255).toInt().coerceIn(0, 255)
                    val strokeArgb = ((strA and 0xFF) shl 24) or ((strR and 0xFF) shl 16) or ((strG and 0xFF) shl 8) or (strB and 0xFF)
                    val strokeColorLong = strokeArgb.toLong() and 0xFFFFFFFFL

                    viewModel.addClip(
                        type = LayerType.SHAPE,
                        name = "${shapeType.label} Shape",
                        durationMs = clipDuration,
                        shapePattern = shapeType.name,
                        textColor = colorLong,
                        shapeOffsetX = offX,
                        shapeOffsetY = offY,
                        shapeScaleX = w / 100f,
                        shapeScaleY = h / 100f,
                        strokeWidth = strokeW,
                        strokeColor = strokeColorLong
                    )
                },
                modifier = Modifier.fillMaxSize().enterPop()
            )
        }
    }

    if (showClipEffectsSheet) {
        val selectedClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
        ClipEffectsSheet(
            currentEffect = selectedClip?.effectName,
            onEffectSelect = { effect ->
                viewModel.setClipEffect(effect)
            },
            onDismiss = { showClipEffectsSheet = false }
        )
    }

    if (showClipAnimationsSheet) {
        val selectedClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
        ClipAnimationsSheet(
            currentAnim = selectedClip?.animationName,
            onAnimSelect = { anim ->
                viewModel.setClipAnimation(anim)
            },
            onDismiss = { showClipAnimationsSheet = false }
        )
    }

    if (showClipReplaceSheet) {
        ClipReplaceSheet(
            onMediaSelected = { res, name ->
                viewModel.replaceSelectedClipMedia(res, name)
            },
            onDismiss = { showClipReplaceSheet = false }
        )
    }

    if (showClipBlendSheet) {
        val selectedClip = tracks.flatMap { it.clips }.find { it.id == selectedClipId }
        ClipBlendSheet(
            currentBlend = selectedClip?.blendMode ?: "Normal",
            onBlendSelected = { mode ->
                viewModel.setClipBlendMode(mode)
            },
            onDismiss = { showClipBlendSheet = false }
        )
    }

    if (showAppliedEffectsMode) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAppliedEffectsMode = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppliedEffectsView(
                aspectRatio = selectedAspectRatio,
                onDismiss = { showAppliedEffectsMode = false },
                onEffectApplied = { effectName ->
                    viewModel.setClipEffect(effectName)
                },
                modifier = Modifier.fillMaxSize().enterPop()
            )
        }
    }


}
