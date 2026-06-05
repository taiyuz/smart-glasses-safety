# Field Validation Protocol (Controlled)

This MVP is assistive only. Users must still verify traffic conditions before crossing.

## Test setup
- Device mounted in realistic glasses position.
- Tester walks sidewalk and curb-adjacent paths only.
- Second observer records ground-truth events.
- Start with low-traffic areas before busier roads.

## Scenarios
1. Vehicle approaching straight ahead at low speed.
2. Vehicle approaching from left/right peripheral angle.
3. Parked/static vehicles (false positive check).
4. Occlusion and partial visibility (e.g., behind a bus).
5. Bright daylight / shadow transitions.

## Metrics to capture
- True positive alerts.
- False positives per 10 minutes.
- Missed high-risk approaches.
- Alert comprehension (did user understand and react).
- Average latency and FPS from app logs.

## Tuning loop
1. Start with `BALANCED` profile thresholds.
2. If false positives are high, move toward `CONSERVATIVE`.
3. If misses are high, move toward `SENSITIVE`.
4. Re-run same scenarios and compare trend lines.

## Release gate for pilot
- Missed critical approaches minimized in controlled scenarios.
- False positives low enough to avoid alert fatigue.
- Senior testers report clear and understandable warnings.
