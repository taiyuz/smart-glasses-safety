package com.smartglasses.safety

import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.VehicleDetection
import com.smartglasses.safety.pipeline.VehicleTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTrackerTest {
    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        RectBox(left, top, right, bottom)

    private fun detection(box: RectBox, confidence: Float = 0.9f) =
        VehicleDetection(label = "car", confidence = confidence, box = box)

    @Test
    fun persistsIdWhenBoxesOverlap() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val first = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        val second = tracker.track(listOf(detection(box(12f, 12f, 52f, 52f))))
        assertEquals(1, first.size)
        assertEquals(first[0].id, second[0].id)
    }

    @Test
    fun assignsNewIdWhenBoxesAreFar() {
        val tracker = VehicleTracker(frameWidth = 400f)
        val first = tracker.track(listOf(detection(box(0f, 0f, 20f, 20f))))
        val second = tracker.track(listOf(detection(box(200f, 200f, 240f, 240f))))
        assertNotEquals(first[0].id, second[0].id)
    }

    @Test
    fun emptyDetectionsAreSafe() {
        val tracker = VehicleTracker(frameWidth = 100f)
        assertTrue(tracker.track(emptyList()).isEmpty())
    }

    @Test
    fun dropsTrackAfterMissesThenReassigns() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 2)
        val first = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        tracker.track(emptyList())
        tracker.track(emptyList())
        val again = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        assertNotEquals(first[0].id, again[0].id)
    }

    @Test
    fun keepsIdWhenMissesStayBelowMax() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 3)
        val first = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        tracker.track(emptyList())
        tracker.track(emptyList())
        val again = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        assertEquals(first[0].id, again[0].id)
    }

    @Test
    fun doesNotMatchWhenIouIsBelowThreshold() {
        val tracker = VehicleTracker(frameWidth = 400f, iouMatchThreshold = 0.3f)
        val first = tracker.track(listOf(detection(box(0f, 0f, 40f, 40f))))
        // overlap is 5x40; IoU ≈ 0.067, under the 0.3 gate
        val second = tracker.track(listOf(detection(box(35f, 0f, 75f, 40f))))
        assertEquals(1, second.size)
        assertNotEquals(first[0].id, second[0].id)
    }

    @Test
    fun tracksTwoNonOverlappingBoxesIndependently() {
        val tracker = VehicleTracker(frameWidth = 400f)
        val both = tracker.track(
            listOf(
                detection(box(0f, 0f, 40f, 40f)),
                detection(box(200f, 0f, 240f, 40f))
            )
        )
        assertEquals(2, both.size)
        assertNotEquals(both[0].id, both[1].id)
    }

    @Test
    fun greedyMatchDoesNotAssignOneTrackToTwoDetections() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val first = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        val second = tracker.track(
            listOf(
                detection(box(12f, 12f, 52f, 52f)),
                detection(box(14f, 14f, 54f, 54f))
            )
        )
        assertEquals(2, second.size)
        assertEquals(1, second.count { it.id == first[0].id })
    }

    @Test
    fun areaGrowthIsPositiveWhenBoxGetsLarger() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val first = tracker.track(listOf(detection(box(10f, 10f, 50f, 50f))))
        // IoU stays ~0.76 so this is the same track, not a new id with empty history.
        val grown = tracker.track(listOf(detection(box(8f, 8f, 54f, 54f))))
        assertEquals(1, grown.size)
        assertEquals(first[0].id, grown[0].id)
        assertTrue(grown[0].areaGrowth > 0f)
    }

    @Test
    fun confidencePersistenceDropsWhenALowScoreUpdateArrives() {
        val tracker = VehicleTracker(frameWidth = 200f)
        tracker.track(listOf(detection(box(10f, 10f, 50f, 50f), confidence = 0.9f)))
        val low = tracker.track(listOf(detection(box(12f, 12f, 52f, 52f), confidence = 0.2f)))
        assertEquals(0.5f, low[0].confidencePersistence, 0.0001f)
    }

    @Test
    fun zeroAreaBoxDoesNotNaNAreaGrowth() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val degenerate = tracker.track(listOf(detection(box(10f, 10f, 10f, 10f))))
        assertEquals(1, degenerate.size)
        assertEquals(0f, degenerate[0].areaGrowth, 0.0001f)
        assertTrue(degenerate[0].areaGrowth.isFinite())
    }

    @Test
    fun centerDriftIsZeroWhenBoxIsCentered() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val tracked = tracker.track(listOf(detection(box(80f, 10f, 120f, 50f))))
        assertEquals(0f, tracked[0].centerDriftToMiddle, 0.0001f)
    }

    @Test
    fun centerDriftIsOneWhenBoxCenterIsAtTheLeftEdge() {
        val tracker = VehicleTracker(frameWidth = 200f)
        val tracked = tracker.track(listOf(detection(box(-20f, 10f, 20f, 50f))))
        assertEquals(1f, tracked[0].centerDriftToMiddle, 0.0001f)
    }

    @Test
    fun slowHorizontalSlideKeepsTheSameId() {
        val tracker = VehicleTracker(frameWidth = 400f)
        var lastId: Long? = null
        for (shift in 0..8) {
            val dx = shift * 4f
            val tracked = tracker.track(
                listOf(detection(box(10f + dx, 10f, 50f + dx, 50f)))
            )
            assertEquals(1, tracked.size)
            if (lastId == null) {
                lastId = tracked[0].id
            } else {
                assertEquals(lastId, tracked[0].id)
            }
        }
    }

    @Test
    fun survivingTrackKeepsIdWhenTheOtherExpires() {
        val tracker = VehicleTracker(frameWidth = 400f, maxMisses = 2)
        val both = tracker.track(
            listOf(
                detection(box(0f, 0f, 40f, 40f)),
                detection(box(200f, 0f, 240f, 40f))
            )
        )
        val rightId = both.first { it.detection.box.centerX > 100f }.id
        tracker.track(listOf(detection(box(202f, 0f, 242f, 40f))))
        val afterExpiry = tracker.track(listOf(detection(box(204f, 0f, 244f, 40f))))
        assertEquals(1, afterExpiry.size)
        assertEquals(rightId, afterExpiry[0].id)
    }

    @Test
    fun historyWindowDropsOldHighConfidence() {
        val tracker = VehicleTracker(frameWidth = 200f, historyWindow = 3)
        tracker.track(listOf(detection(box(10f, 10f, 50f, 50f), confidence = 0.9f)))
        tracker.track(listOf(detection(box(12f, 12f, 52f, 52f), confidence = 0.9f)))
        tracker.track(listOf(detection(box(14f, 14f, 54f, 54f), confidence = 0.9f)))
        val low = tracker.track(listOf(detection(box(16f, 16f, 56f, 56f), confidence = 0.2f)))
        // Window is the last three: 0.9, 0.9, 0.2 → 2/3
        assertEquals(2f / 3f, low[0].confidencePersistence, 0.0001f)
    }
}
