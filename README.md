# Smart Glasses Safety MVP

Android-based MVP that monitors forward camera video and issues on-device visual and voice warnings when vehicles approach.

## MVP capabilities
- Camera-to-inference pipeline with `CameraX`.
- On-device detector interface (`VehicleDetector`) with a mock detector for fast iteration.
- Temporal tracking and risk scoring with three urgency levels.
- Visual + spoken warnings with alert debouncing to reduce fatigue.
- Runtime latency/FPS telemetry and local risk event logging.

## Tech stack
- Android + Kotlin
- CameraX
- TensorFlow Lite runtime (integration path via `TFLiteVehicleDetector`)

## Project layout
- `app/src/main/java/com/smartglasses/safety/MainActivity.kt` - camera pipeline orchestration
- `app/src/main/java/com/smartglasses/safety/pipeline/` - detector, tracker, risk, alerts, perf, logger
- `docs/optimization-checklist.md` - on-device optimization playbook
- `docs/field-validation.md` - controlled field test protocol and tuning loop

## Run
1. Open the folder in Android Studio.
2. Let Gradle sync and install missing SDK packages.
3. Deploy to an Android device / smart glasses with camera access.
4. Grant camera permission and observe live alerts.

## Next step for production
Replace `MockVehicleDetector` in `MainActivity` with a concrete `TFLiteVehicleDetector` model adapter and map model outputs into `VehicleDetection`.
