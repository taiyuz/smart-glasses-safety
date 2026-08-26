# Tracking design

The live tracker is a small SORT-style filter, not a MOT research stack.

## What the code does

On each frame:

1. Predict every existing track with a constant-velocity Kalman step.
2. Compute IoU between each predicted box and each detection.
3. Greedy match (highest IoU first) above a threshold (default `0.3`). A track and a detection are each used at most once.
4. Unmatched tracks increment a miss counter and are dropped after `maxMisses` (default 5).
5. Unmatched detections start a new track.
6. Matched detections update the Kalman filter and the short history used by `RiskScorer` (`areaGrowth`, `centerDriftToMiddle`, `confidencePersistence`).

The box filter is four independent 1-D constant-velocity Kalman filters on `(cx, cy, w, h)`, not a single coupled 7-D SORT state. Process and measurement noise are untuned defaults. There is no appearance embedding, no ReID, and no second association pass on low-score detections.

## Citation (matches this code)

Alex Bewley, Zongyuan Ge, Lionel Ott, Fabio Ramos, and Ben Upcroft. Simple Online and Realtime Tracking. *IEEE International Conference on Image Processing (ICIP)*, 2016. https://arxiv.org/abs/1602.00763

We take from SORT: detect → Kalman predict → IoU associate → update, plus track expiry. We do **not** implement SORT's Hungarian assignment (greedy IoU is the substitute) and we do **not** implement ByteTrack's low-score second association, so Zhang et al. 2022 is not cited.

## What this is not

Not MOTS, not ByteTrack, not a benchmarked MOTA/HOTA number. It exists so vehicle candidates keep a stable id for a few frames so the risk scorer can see box-area growth.
