package com.smartglasses.safety

import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.VehicleDetection
import com.smartglasses.safety.pipeline.VehicleTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTrackerTest {
    private fun car(box: RectBox, confidence: Float = 0.9f): VehicleDetection {
        return VehicleDetection(label = "car", confidence = confidence, box = box)
    }

    @Test
    fun emptyDetectionsAreSafe() {
        val tracker = VehicleTracker(frameWidth = 1000f)
        val first = tracker.track(emptyList())
        val second = tracker.track(emptyList())
        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
    }

    @Test
    fun highIouPersistsTrackId() {
        val tracker = VehicleTracker(frameWidth = 1000f)
        val first = tracker.track(listOf(car(RectBox(100f, 100f, 200f, 200f))))
        val second = tracker.track(listOf(car(RectBox(110f, 105f, 210f, 205f))))

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(first[0].id, second[0].id)
    }

    @Test
    fun farBoxGetsNewId() {
        val tracker = VehicleTracker(frameWidth = 1000f)
        val first = tracker.track(listOf(car(RectBox(100f, 100f, 200f, 200f))))
        val second = tracker.track(listOf(car(RectBox(700f, 100f, 800f, 200f))))

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertNotEquals(first[0].id, second[0].id)
    }

    @Test
    fun expiresAfterMissesThenAssignsNewId() {
        val tracker = VehicleTracker(frameWidth = 1000f, maxMisses = 2)
        val box = RectBox(100f, 100f, 200f, 200f)
        val original = tracker.track(listOf(car(box)))
        assertEquals(1, original.size)

        assertTrue(tracker.track(emptyList()).isEmpty())
        assertTrue(tracker.track(emptyList()).isEmpty())

        val revived = tracker.track(listOf(car(box)))
        assertEquals(1, revived.size)
        assertNotEquals(original[0].id, revived[0].id)
    }

    @Test
    fun recoversSameIdWhenMissesStayBelowExpiry() {
        val tracker = VehicleTracker(frameWidth = 1000f, maxMisses = 3)
        val box = RectBox(100f, 100f, 200f, 200f)
        val original = tracker.track(listOf(car(box)))
        tracker.track(emptyList())
        val recovered = tracker.track(listOf(car(box)))

        assertEquals(original[0].id, recovered[0].id)
    }

    @Test
    fun twoBoxesInOneFrameGetDistinctIds() {
        val tracker = VehicleTracker(frameWidth = 1000f)
        val tracked = tracker.track(
            listOf(
                car(RectBox(50f, 50f, 120f, 120f)),
                car(RectBox(400f, 80f, 500f, 180f))
            )
        )
        assertEquals(2, tracked.size)
        assertNotEquals(tracked[0].id, tracked[1].id)
    }
}
