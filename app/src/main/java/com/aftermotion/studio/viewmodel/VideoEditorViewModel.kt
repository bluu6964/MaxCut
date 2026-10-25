package com.aftermotion.studio.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aftermotion.studio.R
import com.aftermotion.studio.engine.AudioWaveform
import com.aftermotion.studio.model.sourceOffsetMs
import com.aftermotion.studio.model.MIN_SPEED
import com.aftermotion.studio.model.MAX_SPEED
import com.aftermotion.studio.model.AspectRatio
import com.aftermotion.studio.model.ExportSettings
import com.aftermotion.studio.model.ImportedMedia
import com.aftermotion.studio.model.LaneLayout
import com.aftermotion.studio.model.LayerType
import com.aftermotion.studio.model.MultiAction
import com.aftermotion.studio.model.MultiSelectOps
import com.aftermotion.studio.model.PreviewResolution
import com.aftermotion.studio.model.AnimProp
import com.aftermotion.studio.model.Easing
import com.aftermotion.studio.model.TimelineClip
import com.aftermotion.studio.model.at
import com.aftermotion.studio.model.trackOffsetAt
import com.aftermotion.studio.model.indexOnTime
import com.aftermotion.studio.model.propValues
import com.aftermotion.studio.model.withEasing
import com.aftermotion.studio.model.withKeyframe
import com.aftermotion.studio.model.withProp
import com.aftermotion.studio.model.withScaledKeyframes
import com.aftermotion.studio.model.withShiftedKeyframes
import com.aftermotion.studio.model.withoutKeyframe
import com.aftermotion.studio.model.TimelineTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class VideoEditorViewModel : ViewModel() {

    private val _currentPlayheadMs = MutableStateFlow(0L)
    val currentPlayheadMs: StateFlow<Long> = _currentPlayheadMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(AspectRatio.RATIO_16_9)
    val selectedAspectRatio: StateFlow<AspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedResolution = MutableStateFlow(PreviewResolution.FULL)
    val selectedResolution: StateFlow<PreviewResolution> = _selectedResolution.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _multiSelectIds = MutableStateFlow<Set<String>>(emptySet())
    /** Layers checked in multi-select mode (empty = mode is off). */
    val multiSelectIds: StateFlow<Set<String>> = _multiSelectIds.asStateFlow()

    private val _isMasterMuted = MutableStateFlow(false)
    val isMasterMuted: StateFlow<Boolean> = _isMasterMuted.asStateFlow()

    private val _tracks = MutableStateFlow<List<TimelineTrack>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack>> = _tracks.asStateFlow()

    init {
        recomputeTotal()
    }

    // Undo / Redo history stacks
    private val undoStack = mutableListOf<List<TimelineTrack>>()
    private val redoStack = mutableListOf<List<TimelineTrack>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _exportSettings = MutableStateFlow(ExportSettings())
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private var playbackJob: Job? = null

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val total = _totalDurationMs.value
        if (total <= 0L) {
            _isPlaying.value = false
            return
        }
        val hasMedia = hasMainMedia()
        // Finished project: start again from the beginning.
        if (hasMedia && _currentPlayheadMs.value >= total - 50L) _currentPlayheadMs.value = 0L
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = null
        // The wall clock is the only clock: every layer (several videos can overlap) has its own
        // player that just follows the playhead.
        playbackJob = viewModelScope.launch {
            val startWallTime = System.currentTimeMillis()
            val startPlayhead = _currentPlayheadMs.value

            while (isActive && _isPlaying.value) {
                delay(16L) // ~60 fps smooth synchronization
                val elapsed = System.currentTimeMillis() - startWallTime
                val target = startPlayhead + elapsed
                if (target >= total) {
                    if (hasMedia) {
                        _currentPlayheadMs.value = total
                        _isPlaying.value = false
                    } else {
                        _currentPlayheadMs.value = 0L
                        play() // Loop from start
                    }
                    break
                } else {
                    _currentPlayheadMs.value = target
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun seekTo(timeMs: Long) {
        val clamped = timeMs.coerceIn(0L, _totalDurationMs.value)
        _currentPlayheadMs.value = clamped
    }

    fun jumpPrevious() {
        val current = _currentPlayheadMs.value
        // Find nearest clip boundary before current
        val allBoundaries = _tracks.value.flatMap { track ->
            track.clips.flatMap { listOf(it.startMs, it.endMs) }
        }.distinct().sorted()

        val prev = allBoundaries.lastOrNull { it < current - 300L } ?: 0L
        seekTo(prev)
    }

    fun jumpNext() {
        val current = _currentPlayheadMs.value
        val allBoundaries = _tracks.value.flatMap { track ->
            track.clips.flatMap { listOf(it.startMs, it.endMs) }
        }.distinct().sorted()

        val next = allBoundaries.firstOrNull { it > current + 300L } ?: _totalDurationMs.value
        seekTo(next)
    }

    fun selectClip(clipId: String?) {
        setSelected(clipId)
    }

    /** Every selection change goes through here: choosing a normal layer leaves multi-select. */
    private fun setSelected(id: String?) {
        _selectedClipId.value = id
        if (id != null && _multiSelectIds.value.isNotEmpty()) _multiSelectIds.value = emptySet()
    }

    // ---- Multi-select (long-press a layer's left control) -------------------

    fun enterMultiSelect(clipId: String) {
        _selectedClipId.value = null
        _multiSelectIds.value = setOf(clipId)
    }

    fun toggleMultiSelect(clipId: String) {
        val cur = _multiSelectIds.value
        _multiSelectIds.value = if (clipId in cur) cur - clipId else cur + clipId
    }

    fun exitMultiSelect() {
        _multiSelectIds.value = emptySet()
    }

    /** Runs one button of the multi-select bar on all checked layers (one undo step). */
    fun applyMultiAction(action: MultiAction) {
        val ids = _multiSelectIds.value
        if (ids.isEmpty()) return
        val next = MultiSelectOps.apply(_tracks.value, ids, action, _currentPlayheadMs.value) ?: return
        pushHistory()
        _tracks.value = next
        recomputeTotal()
    }

    private val _canvasColor = MutableStateFlow(0xFF000000L)
    val canvasColor: StateFlow<Long> = _canvasColor.asStateFlow()

    fun setCanvasColor(argb: Long) {
        _canvasColor.value = argb or 0xFF000000L // the canvas is always opaque
    }

    /** Export resolution label, e.g. "1080p FHD" (the number is the short side in pixels). */
    fun setExportResolution(label: String) {
        _exportSettings.value = _exportSettings.value.copy(resolution = label)
    }

    /** Custom bitrate in kbps (500..50000); 0 = automatic. */
    fun setExportBitrate(kbps: Int) {
        _exportSettings.value = _exportSettings.value.copy(
            customBitrateKbps = if (kbps <= 0) 0 else kbps.coerceIn(500, 50_000)
        )
    }

    fun setExportQuality(quality: String) {
        _exportSettings.value = _exportSettings.value.copy(quality = quality)
    }

    fun setUseNativeExport(on: Boolean) {
        _exportSettings.value = _exportSettings.value.copy(useNativeExport = on)
    }

    /** Export frame rate, limited to 24..60 fps. */
    fun setExportFrameRate(fps: Int) {
        _exportSettings.value = _exportSettings.value.copy(frameRate = fps.coerceIn(24, 60))
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _selectedAspectRatio.value = ratio
    }

    fun setResolution(resolution: PreviewResolution) {
        _selectedResolution.value = resolution
    }

    fun toggleMasterAudio() {
        _isMasterMuted.value = !_isMasterMuted.value
    }

    fun toggleTrackMute(trackIndex: Int) {
        pushHistory()
        val currentList = _tracks.value.toMutableList()
        if (trackIndex in currentList.indices) {
            val track = currentList[trackIndex]
            currentList[trackIndex] = track.copy(isMuted = !track.isMuted)
            _tracks.value = currentList
        }
    }

    /** Eye control: hide/show a layer on the canvas and in export. */
    fun toggleClipVisibility(clipId: String) {
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { c ->
                if (c.id == clipId) c.copy(isVisible = !c.isVisible) else c
            })
        }
    }

    private fun pushHistory() {
        undoStack.add(_tracks.value)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_tracks.value)
            val previous = undoStack.removeAt(undoStack.lastIndex)
            _tracks.value = previous
            recomputeTotal()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_tracks.value)
            val next = redoStack.removeAt(redoStack.lastIndex)
            _tracks.value = next
            recomputeTotal()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    fun splitSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        val playhead = _currentPlayheadMs.value

        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        // Need room on both sides so each half is usable.
        if (playhead <= clip.startMs + 500 || playhead >= clip.endMs - 500) return

        pushHistory()
        val duration1 = playhead - clip.startMs
        val duration2 = clip.endMs - playhead
        val part1 = clip.copy(durationMs = duration1, fadeOutMs = 0L)
        val part2 = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (Split)",
            startMs = playhead,
            durationMs = duration2,
            // Source time moves `speed` times faster than timeline time.
            trimStartMs = clip.trimStartMs + clip.sourceOffsetMs(duration1),
            fadeInMs = 0L
        ).withShiftedKeyframes(-duration1)

        // Alight Motion style: 1 layer = 1 line.
        // Keep part1 in the original lane; put part2 on its own new lane.
        val withPart1 = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { if (it.id == clipId) part1 else it })
        }
        _tracks.value = LaneLayout.place(LaneLayout.normalize(withPart1), part2)
        setSelected(part2.id)
        recomputeTotal()
    }

    fun deleteSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        if (_tracks.value.flatMap { it.clips }.none { it.id == clipId }) return
        pushHistory()
        // Media is a normal layer (like text/shape): no special gap-closing.
        val withoutClip = _tracks.value.map { track ->
            track.copy(clips = track.clips.filter { it.id != clipId })
        }
        _tracks.value = LaneLayout.normalize(withoutClip)
        setSelected(null)
        recomputeTotal()
    }

    fun duplicateSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        pushHistory()
        // Same rule for every layer type (media is not special).
        val duplicated = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (Copy)",
            startMs = clip.endMs + 200
        )
        _tracks.value = LaneLayout.place(LaneLayout.normalize(_tracks.value), duplicated)
        setSelected(duplicated.id)
        recomputeTotal()
    }

    fun flipHorizontalSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isFlippedH = !clip.isFlippedH) else clip
            })
        }
    }

    fun flipVerticalSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isFlippedV = !clip.isFlippedV) else clip
            })
        }
    }

    /**
     * Replace: swaps the picture/video of the selected video or photo layer for a new file.
     * Position, lane, transform, flip and effects stay; the layer never gets longer than the
     * new video, and the crop is reset because the new file has another shape.
     */
    fun replaceSelectedClipWithMedia(m: ImportedMedia) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId && clip.type == LayerType.VIDEO) {
                    val newDuration = if (m.isImage) clip.durationMs
                    else minOf(clip.durationMs, m.durationMs).coerceAtLeast(250L)
                    clip.copy(
                        name = m.name,
                        uri = m.uri,
                        mimeType = m.mimeType,
                        isImage = m.isImage,
                        sourceDurationMs = m.durationMs,
                        sourceAspect = m.aspect,
                        trimStartMs = 0L,
                        durationMs = newDuration,
                        drawableResId = null,
                        cropWidthPercent = 100f,
                        cropHeightPercent = 100f,
                        cropHorizontalPercent = 50f,
                        cropVerticalPercent = 50f,
                        cropPreset = "Free"
                    )
                } else clip
            })
        }
        recomputeTotal()
    }

    fun replaceSelectedClipMedia(drawableResId: Int, newName: String? = null) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(
                        drawableResId = drawableResId,
                        name = newName ?: clip.name
                    )
                } else clip
            })
        }
    }

    fun setClipBlendMode(mode: String) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(blendMode = mode) else clip
            })
        }
    }

    fun setClipEffect(effect: String) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(effectName = effect) else clip
            })
        }
    }

    fun setClipAnimation(anim: String) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(animationName = anim) else clip
            })
        }
    }

    fun setClipColor(color: Long) {
        val clipId = _selectedClipId.value ?: return
        pushHistory()
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) clip.copy(color = color) else clip
            })
        }
    }

    /**
     * Adds a real shape layer at the current playhead. This is the single entry point
     * used by the Shape picker so timeline insertion, selection, undo/redo and duration
     * calculation stay consistent.
     */
    fun addShape(
        name: String,
        pattern: String,
        durationMs: Long = 4000L,
        fillColor: Long = 0xFFFFFFFF,
        strokeWidth: Float = 0f,
        strokeColor: Long = 0xFFFFFFFF,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        val normalizedPattern = pattern.uppercase()
        val safeDuration = durationMs.coerceIn(250L, 60_000L)
        addClip(
            type = LayerType.SHAPE,
            name = name,
            durationMs = safeDuration,
            textColor = fillColor,
            shapePattern = normalizedPattern,
            shapeOffsetX = offsetX,
            shapeOffsetY = offsetY,
            shapeScaleX = scaleX.coerceIn(0.1f, 10f),
            shapeScaleY = scaleY.coerceIn(0.1f, 10f),
            strokeWidth = strokeWidth.coerceIn(0f, 40f),
            strokeColor = strokeColor
        )
    }

    /**
     * Adds a sticker layer at the playhead. [assetPath] is the sticker's path inside the assets
     * (see StickerLibrary): a PNG file or an animated frame folder.
     */
    fun addSticker(assetPath: String, name: String, durationMs: Long = 4000L) {
        addClip(
            type = LayerType.STICKER,
            name = name,
            durationMs = durationMs.coerceIn(250L, 60_000L),
            uri = com.aftermotion.studio.engine.StickerLibrary.URI_PREFIX + assetPath,
            shapeScaleX = 1f,
            shapeScaleY = 1f
        )
    }

    /**
     * Adds an empty text layer at the playhead and selects it. Returns the new layer's id so the
     * screen can open Text edit on it. The layer exists (and can be undone) right away.
     */
    fun addTextLayer(durationMs: Long = 4000L): String {
        addClip(
            type = LayerType.TEXT,
            name = "Text",
            durationMs = durationMs.coerceIn(250L, 60_000L),
            text = ""
        )
        return _selectedClipId.value.orEmpty()
    }

    /**
     * Adds one text layer per caption (Auto Captions), all in ONE undo step and ONE update of the
     * timeline. [parentStartMs] is the timeline start of the layer the captions were made from; each
     * caption's times are relative to it. [offsetYPx] moves the captions down from the centre of the
     * canvas (the lower third looks right). Every layer gets its own lane (one layer per line, like
     * every other layer); the first caption is the top lane. Returns how many were added.
     */
    fun addCaptionLayers(
        parentStartMs: Long,
        captions: List<com.aftermotion.studio.engine.CaptionBuilder.Caption>,
        offsetYPx: Float
    ): Int {
        if (captions.isEmpty()) return 0
        pushHistory()
        val clips = captions.map { c ->
            TimelineClip(
                id = UUID.randomUUID().toString(),
                name = "Caption",
                type = LayerType.TEXT,
                startMs = (parentStartMs + c.relStartMs).coerceAtLeast(0L),
                durationMs = (c.relEndMs - c.relStartMs).coerceAtLeast(250L),
                color = 0xFF3992CE,
                text = c.text,
                textColor = 0xFFFFFFFF,
                fontWeight = 700,
                textStrokeThickness = 14f,
                textStrokeColor = 0xFF000000,
                scalePercent = 70f,
                transformY = offsetYPx
            )
        }
        _tracks.value = LaneLayout.placeAll(_tracks.value, clips)
        setSelected(null)
        recomputeTotal()
        return clips.size
    }

    /** Remembers the current state for undo before a Text edit session on an existing layer. */
    fun beginTextEdit() = pushHistory()

    /**
     * Live edit of one layer (used by Text edit). It does not push undo steps itself:
     * one step is pushed when the editor opens, so a whole editing session undoes at once.
     */
    fun updateClip(clipId: String, change: (TimelineClip) -> TimelineClip) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map {
                if (it.id == clipId) commitKeyframed(it, change(it), vsResolved = false) else it
            })
        }
    }

    /**
     * Live edit from the Color panel. [change] is applied to the layer AS IT LOOKS at the playhead,
     * so a colour that has keyframes is compared with its animated value: an edit becomes a keyframe
     * at the playhead (the Transform rule), the stored colour stays as the fallback.
     */
    fun updateClipLive(clipId: String, change: (TimelineClip) -> TimelineClip) = updateClipColor(clipId, change)

    fun updateClipColor(clipId: String, change: (TimelineClip) -> TimelineClip) {
        val t = _currentPlayheadMs.value
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map {
                if (it.id == clipId) commitKeyframed(it, change(it.at(t)), vsResolved = true) else it
            })
        }
    }

    /** Edit without any keyframe handling (Color reset also drops the colour keyframes). */
    fun updateClipNoKeyframes(clipId: String, change: (TimelineClip) -> TimelineClip) =
        updateClipRaw(clipId, change)

    /**
     * Diamond of a panel that edits several properties at once (gradient colours): if the playhead
     * sits on a keyframe of any of them those keyframes are removed, otherwise every property gets
     * one at the playhead.
     */
    fun toggleKeyframes(clipId: String, props: List<AnimProp>) {
        if (props.isEmpty()) return
        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val t = _currentPlayheadMs.value
        if (t < clip.startMs || t > clip.endMs) return
        val rel = t - clip.startMs
        val on = props.filter { clip.keyframes[it].orEmpty().indexOnTime(rel) >= 0 }
        pushHistory()
        if (on.isNotEmpty()) {
            updateClipRaw(clipId) { c ->
                var out = c
                for (p in on) {
                    val idx = out.keyframes[p].orEmpty().indexOnTime(rel)
                    if (idx >= 0) out = out.withoutKeyframe(p, idx)
                }
                out
            }
        } else {
            val resolved = clip.at(t)
            updateClipRaw(clipId) { c ->
                var out = c
                for (p in props) {
                    val (a, b) = resolved.propValues(p)
                    out = out.withKeyframe(p, rel, a, b)
                }
                out
            }
        }
    }

    /** Same as [updateClip] but without the keyframe handling (keyframe edits themselves). */
    private fun updateClipRaw(clipId: String, change: (TimelineClip) -> TimelineClip) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { if (it.id == clipId) change(it) else it })
        }
    }

    /**
     * Keyframe rule for every transform edit. A property WITHOUT keyframes is edited as before.
     * A property WITH keyframes keeps its plain field untouched; an edit becomes a keyframe at the
     * playhead (the one it sits on is changed, otherwise a new one is added) - Alight Motion style.
     *
     * [vsResolved]: the caller sends the complete state it showed (the Transform panel), so a value
     * counts as edited when it differs from what the layer looks like at the playhead. Otherwise it
     * counts as edited when it differs from the stored field.
     */
    private fun commitKeyframed(old: TimelineClip, new: TimelineClip, vsResolved: Boolean): TimelineClip {
        if (old.keyframes.isEmpty()) return new
        val t = _currentPlayheadMs.value
        val rel = (t - old.startMs).coerceIn(0L, old.durationMs)
        val resolved = old.at(t)
        var out = new
        for (p in old.keyframes.keys) {
            val (na, nb) = new.propValues(p)
            val (ra, rb) = (if (vsResolved) resolved else old).propValues(p)
            val (oa, ob) = old.propValues(p)
            val changed = kotlin.math.abs(na - ra) > 0.0005f || kotlin.math.abs(nb - rb) > 0.0005f
            out = out.withProp(p, oa, ob)
            if (changed) out = out.withKeyframe(p, rel, na, nb)
        }
        return out
    }

    /**
     * Motion tracking: makes the layer follow a tracked object, frame by frame (no keyframes are
     * written). [follow] = null removes the tracking. [undoStep] = false for the live updates while a
     * track runs (the caller pushed one undo step when the track began).
     */
    fun setTrackFollow(clipId: String, follow: com.aftermotion.studio.model.TrackFollow?, undoStep: Boolean = true) {
        if (undoStep) pushHistory()
        updateClipRaw(clipId) { it.copy(trackFollow = follow) }
    }

    /**
     * Diamond button: adds a keyframe of [prop] at the playhead (with the value the layer has there),
     * or removes the one the playhead sits on. Only works while the playhead is inside the layer.
     */
    fun toggleKeyframe(clipId: String, prop: AnimProp) {
        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val t = _currentPlayheadMs.value
        if (t < clip.startMs || t > clip.endMs) return
        val rel = t - clip.startMs
        val idx = clip.keyframes[prop].orEmpty().indexOnTime(rel)
        pushHistory()
        if (idx >= 0) {
            updateClipRaw(clipId) { it.withoutKeyframe(prop, idx) }
        } else {
            val (a, b) = clip.at(t).propValues(prop)
            updateClipRaw(clipId) { it.withKeyframe(prop, rel, a, b) }
        }
    }

    /**
     * Sets the easing of the segment that starts at the keyframe [keyframeTimeMs] (relative time).
     * Live while a handle is dragged; the caller invokes [beginAdjust] once when the drag starts.
     */
    fun setKeyframeEasing(clipId: String, prop: AnimProp, keyframeTimeMs: Long, easing: Easing) {
        updateClipRaw(clipId) { c ->
            val idx = c.keyframes[prop].orEmpty().indexOfFirst { it.timeMs == keyframeTimeMs }
            if (idx < 0) c else c.withEasing(prop, idx, easing)
        }
    }

    /** Scale + rotation from the selection frame handles (values are absolute, as the user sees them). */
    fun setScaleRotation(clipId: String, scalePercent: Float, rotationDeg: Float) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map {
                if (it.id == clipId) {
                    commitKeyframed(it, it.copy(scalePercent = scalePercent, rotationDeg = rotationDeg), vsResolved = true)
                } else it
            })
        }
    }

    /**
     * Moves a layer earlier/later on the timeline (horizontal drag).
     * [deltaMs] is added to startMs; start is clamped to ≥ 0.
     * Call [beginAdjust] once before a drag session so undo is a single step.
     */
    fun moveClipInTime(clipId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        _tracks.value = LaneLayout.shiftClipTime(_tracks.value, clipId, deltaMs)
        recomputeTotal()
    }

    /**
     * Trims a layer from one edge (the two arrow handles of a selected layer).
     * Left edge: start moves, and for media the in-point (trimStartMs) moves with it.
     * Right edge: only the length changes. Media can't be stretched past its source length.
     * Call [beginAdjust] once before a drag session so undo is a single step.
     */
    fun trimClipEdge(clipId: String, isLeft: Boolean, deltaMs: Long) {
        if (deltaMs == 0L) return
        val minLen = 100L
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { c ->
                if (c.id != clipId) return@map c
                val hasSource = c.uri != null && !c.isImage
                val spd = c.speed.coerceAtLeast(0.01f)
                if (isLeft) {
                    var d = deltaMs.coerceAtLeast(-c.startMs)
                    if (hasSource) d = d.coerceAtLeast(-(c.trimStartMs / spd).toLong())
                    d = d.coerceAtMost(c.durationMs - minLen)
                    c.copy(
                        startMs = c.startMs + d,
                        durationMs = c.durationMs - d,
                        trimStartMs = if (hasSource) (c.trimStartMs + d * spd).toLong().coerceAtLeast(0L) else c.trimStartMs
                    ).withShiftedKeyframes(-d)
                } else {
                    var d = deltaMs.coerceAtLeast(-(c.durationMs - minLen))
                    if (hasSource && c.sourceDurationMs > 0L) {
                        val maxLen = ((c.sourceDurationMs - c.trimStartMs) / spd).toLong()
                        d = d.coerceAtMost((maxLen - c.durationMs).coerceAtLeast(0L))
                    }
                    c.copy(durationMs = c.durationMs + d)
                }
            })
        }
        recomputeTotal()
    }

    /**
     * Sets the length of a layer that has no media of its own (text, shape, sticker). Used by motion
     * tracking: a new layer is only 4 s long, but it has to stay on screen as long as the object is
     * followed. One layer per lane, so a longer layer can never run into another one.
     */
    fun setOverlayDurationMs(clipId: String, durationMs: Long) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { c ->
                if (c.id == clipId && (c.uri == null || c.isImage)) c.copy(durationMs = durationMs.coerceAtLeast(100L)) else c
            })
        }
        recomputeTotal()
    }

    /** Sets absolute start time of a layer on the timeline. */
    fun setClipStartMs(clipId: String, startMs: Long) {
        _tracks.value = LaneLayout.setClipStart(_tracks.value, clipId, startMs)
        recomputeTotal()
    }

    /**
     * Reorders lanes (vertical drag). [fromIndex]/[toIndex] are positions in [tracks]
     * (0 = top layer, drawn above everything).
     */
    fun reorderLane(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        pushHistory()
        _tracks.value = LaneLayout.reorder(_tracks.value, fromIndex, toIndex)
    }

    /**
     * Moves the lane that holds [clipId] by [deltaLanes] steps
     * (negative = toward top / higher in the stack, positive = toward bottom).
     */
    fun moveLaneByClip(clipId: String, deltaLanes: Int) {
        if (deltaLanes == 0) return
        val tracks = _tracks.value
        val from = tracks.indexOfFirst { lane -> lane.clips.any { it.id == clipId } }
        if (from < 0) return
        val to = (from + deltaLanes).coerceIn(0, tracks.lastIndex)
        if (from == to) return
        pushHistory()
        _tracks.value = LaneLayout.reorder(tracks, from, to)
    }

    fun addClip(
        type: LayerType,
        name: String,
        durationMs: Long = 5000L,
        text: String? = null,
        textColor: Long = 0xFFFFFFFF,
        shapePattern: String? = null,
        uri: String? = null,
        shapeOffsetX: Float = 0f,
        shapeOffsetY: Float = 0f,
        shapeScaleX: Float = 1.0f,
        shapeScaleY: Float = 1.0f,
        strokeWidth: Float = 0f,
        strokeColor: Long = 0xFFFFFFFF
    ) {
        pushHistory()
        val playhead = _currentPlayheadMs.value

        val color = when (type) {
            LayerType.VIDEO -> 0xFF2A4365
            LayerType.AUDIO -> 0xFF1B4332
            LayerType.SHAPE -> 0xFFFF3377
            LayerType.OVERLAY -> 0xFF4A154B
            LayerType.TEXT -> 0xFF3992CE
            LayerType.STICKER -> 0xFF4A2511
        }

        val newClip = TimelineClip(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            startMs = playhead,
            durationMs = durationMs,
            color = color,
            text = text,
            textColor = textColor,
            shapePattern = shapePattern,
            shapeOffsetX = shapeOffsetX,
            shapeOffsetY = shapeOffsetY,
            shapeScaleX = shapeScaleX,
            shapeScaleY = shapeScaleY,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            uri = uri,
            transformX = shapeOffsetX,
            transformY = shapeOffsetY,
            scalePercent = (shapeScaleX * 100f).coerceAtLeast(1f)
        )

        // Alight Motion style: every layer gets its own exclusive lane (1 layer = 1 line).
        _tracks.value = LaneLayout.place(_tracks.value, newClip)
        setSelected(newClip.id)
        recomputeTotal()
    }

    fun updateClipTransform(
        clipId: String,
        offsetX: Float,
        offsetY: Float,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    // The canvas draws a tracked layer at (own position + tracking offset); take the
                    // tracking offset out again so only the user's own move is stored.
                    val (fx, fy) = clip.trackOffsetAt(_currentPlayheadMs.value)
                    val ox = offsetX - fx
                    val oy = offsetY - fy
                    // Built on the layer as it looks at the playhead, so a moved layer with position
                    // keyframes gets a keyframe at the playhead instead of silently changing the stored value.
                    commitKeyframed(clip, clip.at(_currentPlayheadMs.value).copy(
                        shapeOffsetX = ox,
                        shapeOffsetY = oy,
                        shapeScaleX = scaleX,
                        shapeScaleY = scaleY,
                        transformX = ox,
                        transformY = oy,
                        scalePercent = if (scaleX != 1f) (scaleX * 100f).coerceAtLeast(1f) else clip.scalePercent
                    ), vsResolved = true)
                } else clip
            })
        }
    }

    fun updateClipFullTransform(
        clipId: String,
        transformX: Float,
        transformY: Float,
        scalePercent: Float,
        rotationDeg: Float,
        pivotX: Float = 0.5f,
        pivotY: Float = 0.5f,
        rot3dX: Float = 0f,
        rot3dY: Float = 0f,
        warp: Float = 0f,
        transformZ: Float = 0f
    ) {
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    // The Transform panel sends everything it shows (= the animated values at the playhead).
                    commitKeyframed(clip, clip.copy(
                        transformX = transformX,
                        transformY = transformY,
                        scalePercent = scalePercent,
                        rotationDeg = rotationDeg,
                        pivotX = pivotX,
                        pivotY = pivotY,
                        rotation3dX = rot3dX,
                        rotation3dY = rot3dY,
                        warpDistort = warp,
                        transformZ = transformZ,
                        shapeOffsetX = transformX,
                        shapeOffsetY = transformY,
                        shapeScaleX = scalePercent / 100f,
                        shapeScaleY = scalePercent / 100f
                    ), vsResolved = true)
                } else clip
            })
        }
    }

    fun updateClipCrop(
        clipId: String,
        cropWidthPercent: Float,
        cropHeightPercent: Float,
        cropHorizontalPercent: Float = 50f,
        cropVerticalPercent: Float = 50f,
        cropPreset: String = "Free"
    ) {
        // Live update while the Crop screen is open; the caller invokes beginAdjust() once on open.
        // Built on the layer as it looks at the playhead, so a cropped property that has keyframes
        // turns the edit into a keyframe (the Transform rule) instead of changing the stored value.
        val t = _currentPlayheadMs.value
        _tracks.value = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    commitKeyframed(clip, clip.at(t).copy(
                        cropWidthPercent = cropWidthPercent,
                        cropHeightPercent = cropHeightPercent,
                        cropHorizontalPercent = cropHorizontalPercent,
                        cropVerticalPercent = cropVerticalPercent,
                        cropPreset = cropPreset
                    ), vsResolved = true)
                } else clip
            })
        }
    }

    fun resetClipTransform(clipId: String) {
        pushHistory()
        updateClipFullTransform(
            clipId = clipId,
            transformX = 0f,
            transformY = 0f,
            scalePercent = 100f,
            rotationDeg = 0f,
            pivotX = 0.5f,
            pivotY = 0.5f,
            rot3dX = 0f,
            rot3dY = 0f,
            warp = 0f
        )
    }


    // ---- Audio layers -----------------------------------------------------

    private val _waveforms = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    /** Waveform peaks per audio file uri (empty array = could not be decoded). */
    val waveforms: StateFlow<Map<String, FloatArray>> = _waveforms.asStateFlow()
    private val waveformRequested = mutableSetOf<String>()

    /** Decodes the waveform of an audio file once, in the background. */
    fun requestWaveform(context: Context, uri: String, durationMs: Long) {
        if (!waveformRequested.add(uri)) return
        viewModelScope.launch {
            val peaks = AudioWaveform.compute(context, uri, durationMs) ?: FloatArray(0)
            _waveforms.value = _waveforms.value + (uri to peaks)
        }
    }

    /** End of the last picture/video clip: audio is kept inside the video length. */
    private fun videoEndMs(): Long =
        _tracks.value.flatMap { it.clips }
            .filter { it.type == LayerType.VIDEO && it.uri != null }
            .maxOfOrNull { it.endMs } ?: 0L

    /**
     * Adds picked audio files as audio layers, one after the other, starting at the playhead.
     * When there is video, a layer never runs past the end of the video.
     */
    fun importAudio(items: List<ImportedMedia>) {
        if (items.isEmpty()) return
        pushHistory()
        val videoEnd = videoEndMs()
        var cursor = _currentPlayheadMs.value
        if (videoEnd > 0L && cursor > videoEnd - 1000L) cursor = 0L
        var updated = _tracks.value
        var lastId: String? = null
        for (m in items) {
            var duration = m.durationMs
            if (videoEnd > 0L) duration = minOf(duration, videoEnd - cursor)
            if (duration < 250L) continue
            val clip = TimelineClip(
                id = UUID.randomUUID().toString(),
                name = m.name,
                type = LayerType.AUDIO,
                startMs = cursor,
                durationMs = duration,
                color = 0xFF1B4332,
                uri = m.uri,
                mimeType = m.mimeType,
                sourceDurationMs = m.durationMs
            )
            updated = LaneLayout.place(updated, clip)
            lastId = clip.id
            cursor += duration
        }
        if (lastId == null) {
            undoStack.removeAt(undoStack.lastIndex)
            _canUndo.value = undoStack.isNotEmpty()
            return
        }
        _tracks.value = updated
        setSelected(lastId)
        recomputeTotal()
    }

    /** One undo step for a whole slider session (Volume / Fade / Speed sheets). */
    fun beginAdjust() = pushHistory()

    fun setClipVolume(clipId: String, volume: Float) =
        updateClip(clipId) { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setClipFades(clipId: String, fadeInMs: Long, fadeOutMs: Long) =
        updateClip(clipId) {
            val maxFade = it.durationMs
            it.copy(
                fadeInMs = fadeInMs.coerceIn(0L, maxFade),
                fadeOutMs = fadeOutMs.coerceIn(0L, maxFade)
            )
        }

    fun setAudioSpeed(clipId: String, speed: Float) = setClipSpeed(clipId, speed)

    /** Changes the speed of an audio or video layer; the layer gets shorter/longer on the timeline. */
    fun setClipSpeed(clipId: String, speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val mapped = _tracks.value.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId && clip.type == LayerType.STICKER) {
                    // Stickers: speed = how fast the animation plays; the layer keeps its length.
                    clip.copy(speed = s)
                } else if (clip.id == clipId) {
                    val sourceSpan = clip.sourceOffsetMs(clip.durationMs).toDouble()
                    val newDuration = (sourceSpan / s).toLong().coerceAtLeast(250L)
                    clip.copy(
                        speed = s,
                        durationMs = newDuration,
                        fadeInMs = clip.fadeInMs.coerceAtMost(newDuration),
                        fadeOutMs = clip.fadeOutMs.coerceAtMost(newDuration)
                    ).withScaledKeyframes(newDuration.toFloat() / clip.durationMs.coerceAtLeast(1L))
                } else clip
            })
        }
        _tracks.value = LaneLayout.normalize(mapped)
        recomputeTotal()
    }

    // ---- Real media -------------------------------------------------------

    fun hasMainMedia(): Boolean =
        _tracks.value.any { track ->
            track.clips.any { it.type == LayerType.VIDEO && it.uri != null }
        }

    /**
     * Imports videos/photos as normal layers (like text/shape).
     * Each file becomes its own lane at the playhead — no special "main track" behaviour.
     */
    fun importMedia(items: List<ImportedMedia>) {
        if (items.isEmpty()) return
        pushHistory()
        val playhead = _currentPlayheadMs.value
        var updated = _tracks.value
        var lastId: String? = null
        for (m in items) {
            val clip = TimelineClip(
                id = UUID.randomUUID().toString(),
                name = m.name,
                type = LayerType.VIDEO,
                startMs = playhead,
                durationMs = m.durationMs,
                color = 0xFF2A4365,
                uri = m.uri,
                mimeType = m.mimeType,
                isImage = m.isImage,
                sourceDurationMs = m.durationMs,
                sourceAspect = m.aspect
            )
            updated = LaneLayout.place(updated, clip)
            lastId = clip.id
        }
        _tracks.value = updated
        setSelected(lastId)
        recomputeTotal()
    }

    /** Called by the screen while ExoPlayer plays; keeps the timeline playhead in sync. */
    fun updatePlayhead(ms: Long) {
        _currentPlayheadMs.value = ms.coerceIn(0L, _totalDurationMs.value)
    }

    fun onPlaybackEnded() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun setExporting(exporting: Boolean) {
        _isExporting.value = exporting
        if (exporting) _exportProgress.value = 0f
    }

    fun setExportProgress(progress: Float) {
        _exportProgress.value = progress.coerceIn(0f, 1f)
    }

    fun finishExport() {
        _exportProgress.value = 1f
        _isExporting.value = false
    }

    private fun recomputeTotal() {
        val maxEnd = _tracks.value.flatMap { it.clips }.maxOfOrNull { it.endMs }
        _totalDurationMs.value = maxEnd ?: 0L
        _currentPlayheadMs.value = _currentPlayheadMs.value.coerceAtMost(_totalDurationMs.value)
        val multi = _multiSelectIds.value
        if (multi.isNotEmpty()) {
            val alive = _tracks.value.flatMap { it.clips }.map { it.id }.toSet()
            val kept = multi.filter { it in alive }.toSet()
            if (kept != multi) _multiSelectIds.value = kept
        }
    }
}
