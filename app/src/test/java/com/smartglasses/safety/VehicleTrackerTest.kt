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
}
