package com.smartglasses.safety

import com.smartglasses.safety.pipeline.RectBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RectBoxTest {
    @Test
    fun iouOfIdenticalBoxesIsOne() {
        val box = RectBox(0f, 0f, 10f, 10f)
        assertEquals(1f, box.iou(box), 0.0001f)
    }

    @Test
    fun iouOfDisjointBoxesIsZero() {
        val a = RectBox(0f, 0f, 10f, 10f)
        val b = RectBox(20f, 20f, 30f, 30f)
        assertEquals(0f, a.iou(b), 0.0001f)
        assertEquals(0f, b.iou(a), 0.0001f)
    }

    @Test
    fun iouIsSymmetricForPartialOverlap() {
        val a = RectBox(0f, 0f, 10f, 10f)
        val b = RectBox(5f, 0f, 15f, 10f)
        // intersection 5x10=50; union 100+100-50=150
        assertEquals(50f / 150f, a.iou(b), 0.0001f)
        assertEquals(a.iou(b), b.iou(a), 0.0001f)
    }

    @Test
    fun containedBoxIouIsInnerOverOuter() {
        val outer = RectBox(0f, 0f, 10f, 10f)
        val inner = RectBox(2f, 2f, 8f, 8f)
        assertEquals(inner.area / outer.area, outer.iou(inner), 0.0001f)
    }

    @Test
    fun touchingEdgesHaveZeroIou() {
        val a = RectBox(0f, 0f, 10f, 10f)
        val b = RectBox(10f, 0f, 20f, 10f)
        assertEquals(0f, a.iou(b), 0.0001f)
    }

    @Test
    fun zeroAreaBoxDoesNotNaN() {
        val empty = RectBox(5f, 5f, 5f, 5f)
        val other = RectBox(0f, 0f, 10f, 10f)
        assertEquals(0f, empty.iou(other), 0.0001f)
        assertTrue(empty.iou(other).isFinite())
        assertEquals(0f, empty.iou(empty), 0.0001f)
    }

    @Test
    fun centerAndAreaMatchGeometry() {
        val box = RectBox(10f, 20f, 30f, 60f)
        assertEquals(20f, box.width, 0.0001f)
        assertEquals(40f, box.height, 0.0001f)
        assertEquals(20f, box.centerX, 0.0001f)
        assertEquals(40f, box.centerY, 0.0001f)
        assertEquals(800f, box.area, 0.0001f)
    }
}
