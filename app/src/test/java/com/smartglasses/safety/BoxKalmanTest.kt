package com.smartglasses.safety

import com.smartglasses.safety.pipeline.BoxKalman
import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.ScalarCvKalman
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxKalmanTest {
    @Test
    fun predictFromRestLeavesPositionUnchanged() {
        val kf = BoxKalman.from(RectBox(0f, 0f, 10f, 10f))
        val before = kf.toRectBox()
        kf.predict()
        val after = kf.toRectBox()
        assertEquals(before.centerX, after.centerX, 0.0001f)
        assertEquals(before.centerY, after.centerY, 0.0001f)
        assertEquals(before.width, after.width, 0.0001f)
        assertEquals(before.height, after.height, 0.0001f)
    }

    @Test
    fun predictCoastsInTheDirectionOfRecentUpdates() {
        val kf = BoxKalman.from(RectBox(0f, 0f, 10f, 10f))
        kf.predict()
        kf.update(RectBox(10f, 0f, 20f, 10f))
        kf.predict()
        kf.update(RectBox(20f, 0f, 30f, 10f))
        val before = kf.toRectBox()
        kf.predict()
        val after = kf.toRectBox()
        assertTrue(after.centerX > before.centerX)
        assertEquals(before.centerY, after.centerY, 0.05f)
    }

    @Test
    fun toRectBoxNeverHasNonPositiveSize() {
        val kf = BoxKalman.from(RectBox(5f, 5f, 5f, 5f))
        val box = kf.toRectBox()
        assertTrue(box.width >= 1f)
        assertTrue(box.height >= 1f)
    }

    @Test
    fun scalarFilterPicksUpPositiveVelocity() {
        val filter = ScalarCvKalman(0f)
        filter.predict()
        filter.update(10f)
        filter.predict()
        filter.update(20f)
        val xBefore = filter.x
        filter.predict()
        assertTrue(filter.v > 0f)
        assertTrue(filter.x > xBefore)
    }
}
