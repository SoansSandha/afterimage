package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class GaussianBlurTest {

    @Test
    fun `a zero sigma returns an equal copy`() {
        val image = TestImages.rectangle(8, 8, 2, 2, 6, 6)

        val out = GaussianBlur.blur(image, 0f)

        assertContentEquals(image.pixels, out.pixels)
        assertNotSame(image.pixels, out.pixels)
    }

    @Test
    fun `a flat image stays flat, borders included`() {
        val out = GaussianBlur.blur(TestImages.flat(9, 5, 0.3f), 2f)

        assertTrue(out.pixels.all { it in 0.2999f..0.3001f })
    }

    @Test
    fun `an impulse spreads symmetrically and keeps its total`() {
        val impulse = TestImages.of(21, 21) { x, y -> if (x == 10 && y == 10) 1f else 0f }

        val out = GaussianBlur.blur(impulse, 1.5f)

        assertEquals(1f, out.pixels.sum(), 1e-4f)
        assertEquals(out[8, 10], out[12, 10], 1e-6f)
        assertEquals(out[10, 7], out[10, 13], 1e-6f)
        assertTrue(out[10, 10] > out[11, 10] && out[11, 10] > out[12, 10])
    }

    @Test
    fun `the kernel is normalised and reaches three sigma`() {
        val kernel = GaussianBlur.kernel(2f)

        assertEquals(13, kernel.size)
        assertEquals(1f, kernel.sum(), 1e-5f)
        assertEquals(3, GaussianBlur.kernel(0.1f).size)
    }

    @Test
    fun `rejects a negative sigma`() {
        assertFailsWith<IllegalArgumentException> { GaussianBlur.blur(TestImages.flat(2, 2, 0f), -1f) }
    }
}
