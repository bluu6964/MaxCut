# After Motion — UI-only

This package is a **UI-only** slice of the After Motion video editor.

## What was removed
- Entire native C++ engine (`app/src/main/cpp/`) — decoder pool, OpenGL renderer, export encoder, whisper JNI, layer anim, etc.
- Real engine implementations (`com.aftermotion.studio.engine.*` production code)
- Heavy ML dependencies: ONNX Runtime, MediaPipe Tasks Vision, ML Kit segmentation
- Whisper / RVM / motion-track / face-blur / cutout / native-preview production paths
- Engine-focused unit tests

## What remains
- All Compose UI (`ui/`, `ui/components/`, `ui/theme/`, `ui/text/`, `ui/util/`)
- Data models (`model/`)
- ViewModel shell (`viewmodel/VideoEditorViewModel.kt`)
- Minimal **stubs** in `engine/EngineStubs.kt` so UI code still type-checks
- Stickers assets, resources, Gradle project structure

## Purpose
Start UI updates without the weight of the native/ML stack. Engine can be re-introduced later by restoring the real `engine/` + `cpp/` packages and the removed dependencies.

## Build
Standard Android Gradle project. Native CMake is disabled. Version suffix: `0.1.100-ui`.
