package com.smartglasses.safety

import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.VehicleDetection
import com.smartglasses.safety.pipeline.VehicleTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTrackerTest {
    @Test
    fun emptyDetectionsDoNotCrash() {
        val tracker = VehicleTracker(frameWidth = 100f)
        val result = tracker.track(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun highIouPersistsTrackId() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 5)
        val first = tracker.track(listOf(car(RectBox(20f, 20f, 80f, 80f))))
        val second = tracker.track(listOf(car(RectBox(22f, 21f, 83f, 82f))))
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(first[0].id, second[0].id)
    }

    @Test
    fun distantBoxGetsNewId() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 5)
        val left = tracker.track(listOf(car(RectBox(10f, 10f, 40f, 40f))))
        val both = tracker.track(
            listOf(
                car(RectBox(10f, 10f, 40f, 40f)),
                car(RectBox(150f, 120f, 190f, 180f))
            )
        )
        assertEquals(1, left.size)
        assertEquals(2, both.size)
        val ids = both.map { it.id }.toSet()
        assertTrue(left[0].id in ids)
        assertEquals(2, ids.size)
    }

    @Test
    fun farReplacementIsNotTheSameTrack() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 5)
        val first = tracker.track(listOf(car(RectBox(10f, 10f, 40f, 40f))))
        val far = tracker.track(listOf(car(RectBox(150f, 120f, 190f, 180f))))
        val farId = far.first { it.detection.box.centerX > 100f }.id
        assertNotEquals(first[0].id, farId)
    }

    @Test
    fun expiresAfterMisses() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 3)
        tracker.track(listOf(car(RectBox(20f, 20f, 80f, 80f))))
        tracker.track(emptyList())
        tracker.track(emptyList())
        val stillThere = tracker.track(emptyList())
        // three empty frames with maxMisses=3: misses 1,2, then 3 removes
        assertTrue(stillThere.isEmpty())
    }

    @Test
    fun coastsUntilExpired() {
        val tracker = VehicleTracker(frameWidth = 200f, maxMisses = 3)
        val first = tracker.track(listOf(car(RectBox(20f, 20f, 80f, 80f))))
        val coast = tracker.track(emptyList())
        assertEquals(1, coast.size)
        assertEquals(first[0].id, coast[0].id)
    }

    private fun car(box: RectBox): VehicleDetection {
        return VehicleDetection(label = "car", confidence = 0.9f, box = box)
    }
}
