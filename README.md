# Smart glasses vehicle-alert MVP

Android/Kotlin on-device pipeline: camera frames → detections → short-horizon tracks → risk score → overlay + spoken alerts.

Wearable-safety prototype (crossing / approach warnings). **Not production ADAS.** Useful as a systems interview artifact: camera backpressure, on-device detection, and alert UX under a thermal/power budget.

## What is real vs not

**Real**

- CameraX preview + `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` (drop stale frames instead of queuing them).
- Default detector: bundled ML Kit Object Detection (`MlKitVehicleDetector`, `STREAM_MODE`, multiple objects). The model is in the APK (`com.google.mlkit:object-detection:17.0.2`); no Play download at runtime.
- Tracker: greedy IoU association plus independent constant-velocity Kalman filters on `(cx, cy, w, h)`. Tracks expire after missed frames. Same `TrackedVehicle` fields the scorer already uses (`areaGrowth`, `centerDriftToMiddle`, `confidencePersistence`).
- Risk scorer (`CONSERVATIVE` / `BALANCED` / `SENSITIVE`), TTS debounce, latency/FPS overlay, local event log.

**Mock / optional**

- `MockVehicleDetector` (centered fake "car" box) only if `BuildConfig.DEBUG && USE_MOCK_DETECTOR`. That flag defaults to `false`. Release always constructs `MlKitVehicleDetector`.
- `TFLiteVehicleDetector` is an optional adapter for a future EfficientDet-Lite asset. **No trained weights are in this repo.** If init fails, it logs and returns no boxes — it does not silently mock. GPU/NNAPI delegates are not wired yet (device-specific init; a failed delegate must fall back to CPU, not invent a speedup).

**Honest limits**

- Bundled ML Kit finds generic objects. Its optional coarse classifier is fashion/food/home/place/plant, **not vehicle classes**, so classification is left off. Every box is a vehicle *candidate*. Expect false positives on people, bags, signs.
- Kalman process/measure noise are untuned defaults, not a MOT benchmark and not ByteTrack.
- Overlay latency/FPS are whatever that phone reports at runtime. This README does not claim a measured mAP or millisecond budget.

## Architecture

```mermaid
flowchart LR
  cam[CameraX ImageAnalysis] --> det[VehicleDetector]
  det --> tr[VehicleTracker]
  tr --> risk[RiskScorer]
  risk --> ui[overlay plus TTS]
  risk --> log[LocalEventLogger]
```

`MainActivity` binds the back camera and a single-thread analyzer. Each frame: YUV → bitmap → `detect` → `track` → `score` → overlay + `AlertManager`. `ImageProxy` is closed on every path so the camera does not stall.

Risk levels: idle / advisory / warning / critical.

## Why `KEEP_ONLY_LATEST`

Glasses and phones cannot afford a backlog of frames. `STRATEGY_KEEP_ONLY_LATEST` is the backpressure valve: if inference is slower than the camera, the analyzer drops the old frame and keeps the newest view of the world. Processing a queued, stale frame would alert on a car that has already moved. Frame time is measured on the analyzer thread; TTS is coalesced so speech does not block the next frame.

GPU/NNAPI on the LiteRT path is the same idea: only enable a delegate if it actually binds, then fall back. Shipping a comment that says "GPU later" with a fake 2× is worse than CPU.

## Stack

- Kotlin, minSdk 28, compileSdk 34, Java 17
- CameraX 1.4.2, AppCompat / Material, view binding, coroutines
- ML Kit Object Detection 17.0.2 (bundled)
- TensorFlow Lite 2.16 (optional adapter only)
- Gradle version catalog (`gradle/libs.versions.toml`)

## Layout

- `app/src/main/java/com/smartglasses/safety/MainActivity.kt` — camera bind, permission, detector choice
- `.../pipeline/MlKitVehicleDetector.kt` — default on-device detector
- `.../pipeline/VehicleTracker.kt` — IoU + Kalman
- `.../pipeline/RiskScorer.kt` — weighted approach score
- `.../pipeline/MockVehicleDetector` in `VehicleDetector.kt` — debug-only
- `.../pipeline/TFLiteVehicleDetector.kt` — optional, no weights
- `docs/` — field-validation notes (algorithm write-up still to land)

## Build

1. Open the repo root in Android Studio (Hedgehog+).
2. Let Gradle sync; install SDK 34 if prompted.
3. Deploy to a device with a back camera (or glasses hardware).
4. Grant camera permission.

```bash
./gradlew :app:assembleDebug   # if the Gradle wrapper is present locally
```

This snapshot does not include `gradlew`. Android Studio generates the wrapper on first sync. `USE_MOCK_DETECTOR` stays `false` unless you flip the `buildConfigField` in `app/build.gradle.kts`.

## On-device constraints

Wearable cameras are low-power: target a modest analysis resolution, close every `ImageProxy`, and do not keep a frame queue. The bundled ML Kit model avoids a runtime download, at the cost of **not** being a COCO vehicle detector. A real vehicle-class model (EfficientDet-Lite0 or similar) belongs on the LiteRT path once weights are vendored or downloaded with a documented checksum — they are not here yet.

MIT license (see `LICENSE`). Copyright (c) 2026 Taiyu Zhu.
