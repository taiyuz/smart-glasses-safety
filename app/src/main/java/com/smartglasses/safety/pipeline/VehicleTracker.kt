package com.smartglasses.safety.pipeline

class VehicleTracker(
    private val frameWidth: Float,
    private val historyWindow: Int = 8,
    private val maxMisses: Int = 8,
    private val highIouThreshold: Float = 0.3f,
    private val lowIouThreshold: Float = 0.1f
) {
    private data class History(val area: Float, val centerX: Float, val confidence: Float)

    private class Track(
        val id: Long,
        val filter: ConstantVelocityBoxFilter,
        val history: ArrayDeque<History>,
        var misses: Int = 0,
        var lastLabel: String,
        var lastConfidence: Float
    )

    private val tracks = mutableListOf<Track>()
    private var nextId = 1L

    fun track(detections: List<VehicleDetection>): List<TrackedVehicle> {
        tracks.forEach { it.filter.predict() }

        val unmatchedTracks = tracks.toMutableSet()
        val unmatchedDets = detections.indices.toMutableSet()
        val assignments = mutableListOf<Pair<Track, Int>>()

        fun associate(minIou: Float) {
            val pairs = ArrayList<Triple<Float, Track, Int>>()
            for (track in unmatchedTracks) {
                val predicted = track.filter.toBox()
                for (di in unmatchedDets) {
                    val iou = predicted.iou(detections[di].box)
                    if (iou >= minIou) pairs += Triple(iou, track, di)
                }
            }
            pairs.sortByDescending { it.first }
            for ((_, track, di) in pairs) {
                if (track in unmatchedTracks && di in unmatchedDets) {
                    unmatchedTracks.remove(track)
                    unmatchedDets.remove(di)
                    assignments += track to di
                }
            }
        }

        // BYTE-style two-stage: high-IoU first, then a lower-IoU salvage pass.
        associate(highIouThreshold)
        associate(lowIouThreshold)

        for ((track, di) in assignments) {
            val det = detections[di]
            track.filter.update(det.box)
            track.misses = 0
            track.lastLabel = det.label
            track.lastConfidence = det.confidence
            track.history.addLast(History(det.box.area, det.box.centerX, det.confidence))
            while (track.history.size > historyWindow) track.history.removeFirst()
        }

        for (di in unmatchedDets) {
            val det = detections[di]
            val filter = ConstantVelocityBoxFilter().also { it.initFrom(det.box) }
            val history = ArrayDeque<History>()
            history.addLast(History(det.box.area, det.box.centerX, det.confidence))
            tracks += Track(
                id = nextId++,
                filter = filter,
                history = history,
                lastLabel = det.label,
                lastConfidence = det.confidence
            )
        }

        unmatchedTracks.forEach { it.misses += 1 }
        tracks.removeAll { it.misses >= maxMisses }

        return tracks.map { it.toTrackedVehicle() }
    }

    private fun Track.toTrackedVehicle(): TrackedVehicle {
        val box = filter.toBox()
        val detection = VehicleDetection(lastLabel, lastConfidence, box)
        val first = history.first()
        val last = history.last()
        val areaGrowth = if (first.area <= 0f) 0f else (last.area - first.area) / first.area
        val half = (frameWidth / 2f).coerceAtLeast(1f)
        val centerDriftToMiddle = kotlin.math.abs(half - last.centerX) / half
        val confidencePersistence =
            history.count { it.confidence >= 0.5f }.toFloat() / history.size.coerceAtLeast(1)
        return TrackedVehicle(
            id = id,
            detection = detection,
            areaGrowth = areaGrowth.coerceIn(-1f, 3f),
            centerDriftToMiddle = centerDriftToMiddle.coerceIn(0f, 1f),
            confidencePersistence = confidencePersistence
        )
    }
}

/**
 * Diagonal-covariance constant-velocity Kalman on (cx, cy, w, h) with vx, vy.
 * No matrix library; process/measurement noise are scalars on the diagonal.
 */
internal class ConstantVelocityBoxFilter {
    private val x = FloatArray(6)
    private val pDiag = FloatArray(6) { 10f }

    fun initFrom(box: RectBox) {
        x[0] = box.centerX
        x[1] = box.centerY
        x[2] = maxOf(box.width, 1f)
        x[3] = maxOf(box.height, 1f)
        x[4] = 0f
        x[5] = 0f
        pDiag[0] = 10f
        pDiag[1] = 10f
        pDiag[2] = 10f
        pDiag[3] = 10f
        pDiag[4] = 50f
        pDiag[5] = 50f
    }

    fun predict() {
        x[0] += x[4]
        x[1] += x[5]
        pDiag[0] += 1f + pDiag[4] * 0.05f
        pDiag[1] += 1f + pDiag[5] * 0.05f
        pDiag[2] += 1f
        pDiag[3] += 1f
        pDiag[4] += 2f
        pDiag[5] += 2f
    }

    fun update(box: RectBox) {
        val meas = floatArrayOf(
            box.centerX,
            box.centerY,
            maxOf(box.width, 1f),
            maxOf(box.height, 1f)
        )
        val r = 4f
        val prevCx = x[0]
        val prevCy = x[1]
        for (i in 0..3) {
            val k = pDiag[i] / (pDiag[i] + r)
            x[i] = x[i] + k * (meas[i] - x[i])
            pDiag[i] = (1f - k) * pDiag[i]
        }
        val dvx = x[0] - prevCx
        val dvy = x[1] - prevCy
        val kv = pDiag[4] / (pDiag[4] + 8f)
        x[4] = x[4] + kv * (dvx - x[4])
        x[5] = x[5] + kv * (dvy - x[5])
        pDiag[4] = (1f - kv) * pDiag[4]
        pDiag[5] = (1f - kv) * pDiag[5]
    }

    fun toBox(): RectBox {
        val w = maxOf(x[2], 1f)
        val h = maxOf(x[3], 1f)
        val cx = x[0]
        val cy = x[1]
        return RectBox(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}
