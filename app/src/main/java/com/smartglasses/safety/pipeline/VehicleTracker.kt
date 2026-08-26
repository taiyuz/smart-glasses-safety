package com.smartglasses.safety.pipeline

import kotlin.math.abs

class VehicleTracker(
    private val frameWidth: Float,
    private val historyWindow: Int = 8,
    private val iouMatchThreshold: Float = 0.3f,
    private val maxMisses: Int = 5
) {
    private data class History(val area: Float, val centerX: Float, val confidence: Float)

    private data class Track(
        val id: Long,
        val kalman: BoxKalman,
        val history: ArrayDeque<History>,
        var misses: Int,
        var lastBox: RectBox
    )

    private val tracks = mutableMapOf<Long, Track>()
    private var nextId = 1L

    fun track(detections: List<VehicleDetection>): List<TrackedVehicle> {
        tracks.values.forEach { it.kalman.predict() }

        val unmatchedTrackIds = tracks.keys.toMutableSet()
        val unmatchedDet = detections.indices.toMutableSet()
        val assignments = LinkedHashMap<Long, Int>()

        val pairs = ArrayList<Match>(tracks.size * detections.size)
        for (track in tracks.values) {
            val predicted = track.kalman.toRectBox()
            detections.forEachIndexed { index, detection ->
                val iou = predicted.iou(detection.box)
                if (iou >= iouMatchThreshold) {
                    pairs.add(Match(track.id, index, iou))
                }
            }
        }
        pairs.sortByDescending { it.iou }
        for (pair in pairs) {
            if (pair.trackId in unmatchedTrackIds && pair.detIndex in unmatchedDet) {
                assignments[pair.trackId] = pair.detIndex
                unmatchedTrackIds.remove(pair.trackId)
                unmatchedDet.remove(pair.detIndex)
            }
        }

        for (trackId in unmatchedTrackIds.toList()) {
            val track = tracks[trackId] ?: continue
            track.misses += 1
            if (track.misses >= maxMisses) {
                tracks.remove(trackId)
            }
        }

        val output = ArrayList<TrackedVehicle>(detections.size)

        for ((trackId, detIndex) in assignments) {
            val track = tracks[trackId] ?: continue
            val detection = detections[detIndex]
            track.kalman.update(detection.box)
            track.misses = 0
            track.lastBox = detection.box
            output.add(toTracked(track, detection))
        }

        for (detIndex in unmatchedDet) {
            val detection = detections[detIndex]
            val id = nextId++
            val track = Track(
                id = id,
                kalman = BoxKalman.from(detection.box),
                history = ArrayDeque(),
                misses = 0,
                lastBox = detection.box
            )
            tracks[id] = track
            output.add(toTracked(track, detection))
        }

        return output
    }

    private fun toTracked(track: Track, detection: VehicleDetection): TrackedVehicle {
        val history = track.history
        history.addLast(History(detection.box.area, detection.box.centerX, detection.confidence))
        while (history.size > historyWindow) history.removeFirst()

        val first = history.first()
        val last = history.last()
        val areaGrowth = if (first.area <= 0f) 0f else (last.area - first.area) / first.area
        val halfWidth = (frameWidth / 2f).coerceAtLeast(1f)
        val centerDriftToMiddle = abs(halfWidth - last.centerX) / halfWidth
        val confidencePersistence = history.count { it.confidence >= 0.5f }.toFloat() / history.size

        return TrackedVehicle(
            id = track.id,
            detection = detection,
            areaGrowth = areaGrowth.coerceIn(-1f, 3f),
            centerDriftToMiddle = centerDriftToMiddle.coerceIn(0f, 1f),
            confidencePersistence = confidencePersistence
        )
    }

    private data class Match(val trackId: Long, val detIndex: Int, val iou: Float)
}

/**
 * Constant-velocity Kalman on (cx, cy, w, h), one 2-state filter per coordinate.
 * Process/measure noise are untuned defaults — not a claimed MOT benchmark.
 */
internal class BoxKalman(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float
) {
    private val cxFilter = ScalarCvKalman(cx)
    private val cyFilter = ScalarCvKalman(cy)
    private val wFilter = ScalarCvKalman(w)
    private val hFilter = ScalarCvKalman(h)

    fun predict() {
        cxFilter.predict()
        cyFilter.predict()
        wFilter.predict()
        hFilter.predict()
    }

    fun update(box: RectBox) {
        cxFilter.update(box.centerX)
        cyFilter.update(box.centerY)
        wFilter.update(box.width.coerceAtLeast(1f))
        hFilter.update(box.height.coerceAtLeast(1f))
    }

    fun toRectBox(): RectBox {
        val w = wFilter.x.coerceAtLeast(1f)
        val h = hFilter.x.coerceAtLeast(1f)
        val cx = cxFilter.x
        val cy = cyFilter.x
        return RectBox(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    companion object {
        fun from(box: RectBox): BoxKalman {
            return BoxKalman(
                cx = box.centerX,
                cy = box.centerY,
                w = box.width.coerceAtLeast(1f),
                h = box.height.coerceAtLeast(1f)
            )
        }
    }
}

internal class ScalarCvKalman(
    initialPosition: Float,
    private val processNoisePos: Float = 1f,
    private val processNoiseVel: Float = 1f,
    private val measureNoise: Float = 10f
) {
    var x = initialPosition
        private set
    var v = 0f
        private set

    private var p00 = 10f
    private var p01 = 0f
    private var p10 = 0f
    private var p11 = 10f

    fun predict(dt: Float = 1f) {
        x += v * dt
        val n00 = p00 + dt * (p10 + p01) + dt * dt * p11 + processNoisePos
        val n01 = p01 + dt * p11
        val n10 = p10 + dt * p11
        val n11 = p11 + processNoiseVel
        p00 = n00
        p01 = n01
        p10 = n10
        p11 = n11
    }

    fun update(measurement: Float) {
        val y = measurement - x
        val s = p00 + measureNoise
        if (s <= 0f) return
        val k0 = p00 / s
        val k1 = p10 / s
        x += k0 * y
        v += k1 * y
        val np00 = (1f - k0) * p00
        val np01 = (1f - k0) * p01
        val np10 = p10 - k1 * p00
        val np11 = p11 - k1 * p01
        p00 = np00
        p01 = np01
        p10 = np10
        p11 = np11
    }
}
