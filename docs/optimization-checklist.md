# On-Device Optimization Checklist

Use this checklist to meet latency and stability goals on target smart-glasses hardware.

## Performance targets
- End-to-end alert latency: under 300 ms.
- Inference throughput: at least 10 FPS effective.
- Session stability: 30+ minute continuous operation.

## Measurement loop
1. Record `avgLatencyMs` and `fps` from runtime logs.
2. Run 10-minute outdoor sessions in consistent lighting.
3. Compare metrics across profile/build changes.

## Optimization levers
- Lower camera analysis resolution (e.g., 1280x720 to 960x540).
- Reduce inference rate (analyze every Nth frame).
- Quantize model to int8/fp16.
- Ensure one analyzer thread and avoid UI thread blocking.
- Keep post-processing linear-time per frame.

## Thermal and battery checks
- Log battery drop for 30-minute runs.
- Pause non-critical overlays if thermal throttling begins.
- Prefer short speech prompts and avoid repeated TTS bursts.

## Acceptance checklist
- [ ] Latency under 300 ms in daylight scenes.
- [ ] FPS consistently at or above 10.
- [ ] No ANR/crash during 30-minute test.
- [ ] Battery drop acceptable for your deployment target.
