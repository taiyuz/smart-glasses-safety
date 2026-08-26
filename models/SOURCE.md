# Model source

## Default detector (no weights file)

Release and first-run Studio builds use **ML Kit Object Detection**
(`com.google.mlkit:object-detection:17.0.2`), STREAM_MODE, bundled model.

- License / ToS: [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms)
- Labels: coarse categories (fashion / food / home / place / plant), **not** COCO vehicles
- Why it is the default: it is a real on-device detector that does not need a `.tflite` in the repo

This is **not** a vehicle-class model. The rest of the pipeline (tracker, scorer, TTS) still runs on the returned boxes.

## Optional TFLite path: EfficientDet-Lite0

When Gradle successfully downloads the weights, `OnDeviceVehicleDetector` prefers TFLite.

| Field | Value |
| --- | --- |
| Architecture | EfficientDet-Lite0 (COCO) |
| File | `efficientdet-lite0.tflite` (not committed; downloaded into `app/build/downloaded-assets/`) |
| URL | https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite |
| SHA-256 | `2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b` (computed locally from that URL on 2026-08-26; 4,563,519 bytes) |
| Hub page | https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1 |
| Upstream download script | https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/android/app/download_models.gradle |
| License | Apache 2.0 (TensorFlow model); trained on [COCO](https://cocodataset.org/) |
| Input | typically `1 x 320 x 320 x 3` UINT8 RGB (read from the interpreter at load time) |
| Outputs | TFLite Detection PostProcess: boxes `[1,N,4]` as ymin/xmin/ymax/xmax in 0–1, classes, scores, count |
| Class filter | COCO 90-slot metadata map; keep `bicycle`, `car`, `motorcycle`, `bus`, `truck` |

SHA-256 is verified by the `downloadEfficientDetLite0` Gradle task. A mismatch deletes the file and leaves ML Kit as the detector. The task never fails the build.

GPU / NNAPI delegates are **not** enabled. They need per-device accuracy checks, warmup, and a CPU fallback before they are honest to claim as a speedup.

## Mock detector

`MockVehicleDetector` is compiled in but only constructed when `BuildConfig.DEBUG && BuildConfig.USE_MOCK_DETECTOR`. The flag defaults to `false`. It emits a synthetic centered "car" box so the tracker/scorer/TTS can be exercised without camera content. Release builds never use it.
