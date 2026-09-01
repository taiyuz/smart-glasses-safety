package com.smartglasses.safety

import com.smartglasses.safety.pipeline.AlertLevel
import com.smartglasses.safety.pipeline.AnalysisPipeline
import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.VehicleDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPipelineTest {
    private fun detection(box: RectBox, confidence: Float = 0.9f) =
        VehicleDetection(label = "car", confidence = confidence, box = box)

    private fun nearBox() = RectBox(10f, 10f, 50f, 50f)

    private fun farBox() = RectBox(200f, 200f, 240f, 240f)

    @Test
    fun firstFrameResetsTracker() {
        val pipeline = AnalysisPipeline()
        val result = pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        assertTrue(result.trackerReset)
        assertEquals(1, result.tracked.size)
        assertEquals(1L, result.tracked[0].id)
    }

    @Test
    fun consecutiveFramesKeepId() {
        val pipeline = AnalysisPipeline(maxPauseMs = 2_000L)
        val first = pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        val second = pipeline.process(
            200f,
            listOf(detection(RectBox(12f, 12f, 52f, 52f))),
            nowMs = 50L
        )
        assertFalse(second.trackerReset)
        assertEquals(first.tracked[0].id, second.tracked[0].id)
    }

    @Test
    fun gapLongerThanMaxPauseStartsIdsAtOne() {
        val pipeline = AnalysisPipeline(maxPauseMs = 2_000L)
        pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        val after = pipeline.process(200f, listOf(detection(farBox())), nowMs = 2_001L)
        assertTrue(after.trackerReset)
        assertEquals(1, after.tracked.size)
        // Without a reset the far box would be a new track (id 2). A new session starts at 1.
        assertEquals(1L, after.tracked[0].id)
    }

    @Test
    fun pauseExactlyAtThresholdDoesNotReset() {
        val pipeline = AnalysisPipeline(maxPauseMs = 2_000L)
        val first = pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        val second = pipeline.process(
            200f,
            listOf(detection(RectBox(12f, 12f, 52f, 52f))),
            nowMs = 2_000L
        )
        assertFalse(second.trackerReset)
        assertEquals(first.tracked[0].id, second.tracked[0].id)
    }

    @Test
    fun explicitResetStartsIdsOver() {
        val pipeline = AnalysisPipeline()
        pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        pipeline.process(200f, listOf(detection(farBox())), nowMs = 50L)
        pipeline.reset()
        val after = pipeline.process(200f, listOf(detection(nearBox())), nowMs = 100L)
        assertTrue(after.trackerReset)
        assertEquals(1L, after.tracked[0].id)
    }

    @Test
    fun frameWidthChangeResetsTracker() {
        val pipeline = AnalysisPipeline()
        pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        val second = pipeline.process(640f, listOf(detection(nearBox())), nowMs = 50L)
        assertTrue(second.trackerReset)
        assertEquals(1L, second.tracked[0].id)
    }

    @Test
    fun emptyDetectionsStillAdvanceTheClockWithoutReset() {
        val pipeline = AnalysisPipeline()
        pipeline.process(200f, listOf(detection(nearBox())), nowMs = 0L)
        val empty = pipeline.process(200f, emptyList(), nowMs = 40L)
        assertFalse(empty.trackerReset)
        assertTrue(empty.tracked.isEmpty())
        assertEquals(AlertLevel.IDLE, empty.risk.level)
        assertEquals(0f, empty.risk.score, 0.0001f)
    }

    @Test
    fun scoresIdleWhenNoDetectionsOnFirstFrame() {
        val pipeline = AnalysisPipeline()
        val result = pipeline.process(200f, emptyList(), nowMs = 0L)
        assertTrue(result.trackerReset)
        assertEquals(AlertLevel.IDLE, result.risk.level)
        assertEquals(0f, result.risk.score, 0.0001f)
    }
}
