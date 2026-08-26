# Tracker design

The on-device tracker is a small SORT pipeline (Bewley et al., 2016):

1. **Predict.** Each live track steps a constant-velocity Kalman filter on `(cx, cy, w, h)` (independent 2-state filters per coordinate, `dt = 1` frame).
2. **Associate.** Predicted boxes are matched to detections by greedy highest-IoU above 0.3. SORT uses the Hungarian algorithm on the same IoU cost; greedy is equivalent for the disjoint boxes this MVP sees.
3. **Update.** Matched filters are corrected with the detection box. Unmatched detections start a new track. Unmatched tracks increment a miss counter.
4. **Expire.** Tracks with `misses >= maxMisses` (default 5) are dropped.

Output is mapped to existing `TrackedVehicle` fields so `RiskScorer` is unchanged:

- `areaGrowth` — relative box-area change over an 8-frame history
- `centerDriftToMiddle` — `|cx - frameCenter| / (frameWidth / 2)`
- `confidencePersistence` — fraction of history with confidence ≥ 0.5

This is not ByteTrack (no low-score second association) and not a MOTS stack. No MOT17/BDD numbers are claimed.

## Reference

Alex Bewley, Zongyuan Ge, Lionel Ott, Fabio Ramos, and Ben Upcroft. "Simple Online and Realtime Tracking." In *IEEE International Conference on Image Processing (ICIP)*, 2016. https://arxiv.org/abs/1602.00763
