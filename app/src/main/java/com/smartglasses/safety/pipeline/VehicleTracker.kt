package com.smartglasses.safety.pipeline

class VehicleTracker(
    private val frameWidth: Float,
    private val historyWindow: Int = 8
) {
    private data class History(val area: Float, val centerX: Float, val confidence: Float)

    private val trackHistory = mutableMapOf<Long, ArrayDeque<History>>()
    private var nextId = 1L

    fun track(detections: List<VehicleDetection>): List<TrackedVehicle> {
        return detections.map { detection ->
            val id = bestTrackId(detection) ?: nextId++
            val history = trackHistory.getOrPut(id) { ArrayDeque() }
            history.addLast(History(detection.box.area, detection.box.centerX, detection.confidence))
            while (history.size > historyWindow) history.removeFirst()

            val first = history.first()
            val last = history.last()
            val areaGrowth = if (first.area <= 0f) 0f else (last.area - first.area) / first.area
            val centerDriftToMiddle = kotlin.math.abs((frameWidth / 2f) - last.centerX) / (frameWidth / 2f)
            val confidencePersistence = history.count { it.confidence >= 0.5f }.toFloat() / history.size

            TrackedVehicle(
                id = id,
                detection = detection,
                areaGrowth = areaGrowth.coerceIn(-1f, 3f),
                centerDriftToMiddle = centerDriftToMiddle.coerceIn(0f, 1f),
                confidencePersistence = confidencePersistence
            )
        }
    }

    private fun bestTrackId(detection: VehicleDetection): Long? {
        if (trackHistory.isEmpty()) return null
        return trackHistory.minByOrNull { (_, history) ->
            kotlin.math.abs(history.last().centerX - detection.box.centerX)
        }?.key
    }
}
