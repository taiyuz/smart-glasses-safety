package com.smartglasses.safety.pipeline

/**
 * Tracker + scorer for one camera session.
 *
 * CameraX pauses [androidx.camera.core.ImageAnalysis] when the activity stops.
 * Miss counters do not increment while no frames arrive, so a Kalman filter would
 * still hold velocity from the previous scene. This class drops that tracker on
 * [reset], on a frame-width change, or if two processed frames are more than
 * [maxPauseMs] apart. The same session boundary clears [RiskScorer] hysteresis so
 * a held WARNING from street A cannot suppress IDLE/ADVISORY on street B.
 *
 * Session hygiene only — not ByteTrack, not ReID, not a second association pass.
 */
class AnalysisPipeline(
    profile: RiskProfile = RiskProfile.BALANCED,
    private val maxPauseMs: Long = 2_000L,
    private val historyWindow: Int = 8,
    private val iouMatchThreshold: Float = 0.3f,
    private val maxMisses: Int = 5,
    private val scorer: RiskScorer = RiskScorer(profile)
) {
    data class FrameResult(
        val tracked: List<TrackedVehicle>,
        val risk: RiskResult,
        val trackerReset: Boolean
    )

    private var tracker: VehicleTracker? = null
    private var lastFrameWidth: Float? = null
    private var lastFrameAtMs: Long? = null

    fun process(
        frameWidth: Float,
        detections: List<VehicleDetection>,
        nowMs: Long
    ): FrameResult {
        val widthChanged = lastFrameWidth != null && lastFrameWidth != frameWidth
        val pausedTooLong = lastFrameAtMs != null && (nowMs - lastFrameAtMs!!) > maxPauseMs
        val reset = tracker == null || widthChanged || pausedTooLong
        if (reset) {
            tracker = VehicleTracker(
                frameWidth = frameWidth,
                historyWindow = historyWindow,
                iouMatchThreshold = iouMatchThreshold,
                maxMisses = maxMisses
            )
            scorer.reset()
        }
        lastFrameWidth = frameWidth
        lastFrameAtMs = nowMs
        val tracked = requireNotNull(tracker).track(detections)
        return FrameResult(
            tracked = tracked,
            risk = scorer.score(tracked),
            trackerReset = reset
        )
    }

    fun reset() {
        tracker = null
        lastFrameWidth = null
        lastFrameAtMs = null
        scorer.reset()
    }
}
