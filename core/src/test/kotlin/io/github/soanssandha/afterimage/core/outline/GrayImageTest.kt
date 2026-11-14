package io.github.soanssandha.afterimage.core.outline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrayImageTest {

    @Test
    fun `luma follows Rec 709 weights and ignores alpha`() {
        val argb = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0x0000FF00, 0xFFFF0000.toInt())
        val image = GrayImage.fromArgb(2, 2, argb)

        assertEquals(1f, image[0, 0], 1e-5f)
        assertEquals(0f, image[1, 0], 1e-5f)
        assertEquals(0.7152f, image[0, 1], 1e-5f)
        assertEquals(0.2126f, image[1, 1], 1e-5f)
    }

    @Test
    fun `clamped reads past the border return the nearest edge pixel`() {
        val image = GrayImage(2, 2, floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))

        assertEquals(0.1f, image.clamped(-5, -5))
        assertEquals(0.2f, image.clamped(9, 0))
        assertEquals(0.3f, image.clamped(0, 9))
        assertEquals(0.4f, image.clamped(9, 9))
    }

    @Test
    fun `rejects empty sizes and mismatched pixel counts`() {
        assertFailsWith<IllegalArgumentException> { GrayImage(0, 4) }
        assertFailsWith<IllegalArgumentException> { GrayImage(4, 0) }
        assertFailsWith<IllegalArgumentException> { GrayImage(2, 2, FloatArray(3)) }
        assertFailsWith<IllegalArgumentException> { GrayImage.fromArgb(2, 2, IntArray(5)) }
    }
}
