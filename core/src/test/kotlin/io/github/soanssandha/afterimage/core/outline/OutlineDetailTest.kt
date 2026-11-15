package io.github.soanssandha.afterimage.core.outline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OutlineDetailTest {

    @Test
    fun `the default level gives the tuned defaults exactly`() {
        assertEquals(OutlineParams(), OutlineDetail.params(OutlineDetail.DEFAULT))
    }

    @Test
    fun `the ends scale the floor by four, the speck size by two and move the percentile by a tenth`() {
        val fewest = OutlineDetail.canny(OutlineDetail.MIN)
        val most = OutlineDetail.canny(OutlineDetail.MAX)

        assertEquals(1.0f, fewest.minMagnitude, 1e-6f)
        assertEquals(0.0625f, most.minMagnitude, 1e-6f)
        assertEquals(0.95f, fewest.highPercentile, 1e-6f)
        assertEquals(0.75f, most.highPercentile, 1e-6f)
        assertEquals(48, OutlineDetail.minComponentPixels(OutlineDetail.MIN))
        assertEquals(12, OutlineDetail.minComponentPixels(OutlineDetail.MAX))
        // Only the thresholds move; the blur and weak-edge ratio stay at their defaults.
        assertEquals(EdgeDetector.Canny().sigma, most.sigma)
        assertEquals(EdgeDetector.Canny().lowRatio, most.lowRatio)
    }

    @Test
    fun `more detail never raises a threshold`() {
        val levels = (0..20).map { it / 20f }
        for ((lower, higher) in levels.zipWithNext()) {
            val a = OutlineDetail.params(lower)
            val b = OutlineDetail.params(higher)
            val ca = a.detector as EdgeDetector.Canny
            val cb = b.detector as EdgeDetector.Canny
            assertTrue(cb.minMagnitude < ca.minMagnitude, "floor at $higher")
            assertTrue(cb.highPercentile < ca.highPercentile, "percentile at $higher")
            assertTrue(b.minComponentPixels <= a.minComponentPixels, "speck size at $higher")
        }
    }

    @Test
    fun `coerce clamps stored values and maps NaN to the default`() {
        assertEquals(0.3f, OutlineDetail.coerce(0.3f))
        assertEquals(OutlineDetail.MIN, OutlineDetail.coerce(-2f))
        assertEquals(OutlineDetail.MAX, OutlineDetail.coerce(7f))
        assertEquals(OutlineDetail.MAX, OutlineDetail.coerce(Float.POSITIVE_INFINITY))
        assertEquals(OutlineDetail.DEFAULT, OutlineDetail.coerce(Float.NaN))
    }

    @Test
    fun `levels outside the range are rejected`() {
        assertFailsWith<IllegalArgumentException> { OutlineDetail.canny(-0.01f) }
        assertFailsWith<IllegalArgumentException> { OutlineDetail.minComponentPixels(1.01f) }
        assertFailsWith<IllegalArgumentException> { OutlineDetail.params(Float.NaN) }
    }
}
