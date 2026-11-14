package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CannyEdgesTest {

    private val canny = EdgeDetector.Canny()

    @Test
    fun `a flat image has no lines`() {
        assertEquals(0, canny.detect(TestImages.flat(30, 30, 0.5f)).count())
    }

    @Test
    fun `faint noise stays below the minimum magnitude`() {
        val noise = TestImages.noisy(TestImages.flat(40, 40, 0.2f), amplitude = 0.01f, seed = 7)

        assertEquals(0, canny.detect(noise).count())
        // Without the floor, the percentile threshold would turn the same noise into lines.
        assertTrue(EdgeDetector.Canny(minMagnitude = 0f).detect(noise).count() > 0)
    }

    @Test
    fun `a rectangle's border becomes a one-pixel line around it`() {
        val mask = canny.detect(TestImages.rectangle(40, 40, 10, 10, 30, 30))

        for (y in 0 until 40) {
            for (x in 0 until 40) {
                if (!mask[x, y]) continue
                val nearSide = minOf(abs(x - 9.5f), abs(x - 29.5f), abs(y - 9.5f), abs(y - 29.5f))
                assertTrue(nearSide <= 2f, "line pixel ($x, $y) is far from the border")
            }
        }
        // Each side is mostly covered, and a row through the middle crosses each vertical side exactly once.
        assertTrue((12 until 28).count { y -> mask[9, y] || mask[10, y] } >= 14)
        assertTrue((12 until 28).count { x -> mask[x, 29] || mask[x, 30] } >= 14)
        assertEquals(1, (5 until 15).count { x -> mask[x, 20] })
        assertEquals(1, (25 until 35).count { x -> mask[x, 20] })
    }

    @Test
    fun `diagonal edges in both directions are found and thinned`() {
        // Falling: x = y, brighter above it. Rising: x + y = 49, brighter below it. Each takes the other diagonal branch.
        assertThinDiagonal(canny.detect(TestImages.of(50, 50) { x, y -> if (x > y) 0.9f else 0.1f })) { x, y -> x - y }
        assertThinDiagonal(canny.detect(TestImages.of(50, 50) { x, y -> if (x + y > 49) 0.9f else 0.1f })) { x, y ->
            x + y - 49
        }
    }

    private fun assertThinDiagonal(mask: EdgeMask, offLine: (Int, Int) -> Int) {
        assertTrue(mask.count() >= 30, "too few line pixels: ${mask.count()}")
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (mask[x, y]) assertTrue(abs(offLine(x, y)) <= 2, "line pixel ($x, $y) is off the diagonal")
            }
        }
    }

    @Test
    fun `rejects settings out of range`() {
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Canny(sigma = -1f) }
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Canny(highPercentile = 1.5f) }
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Canny(lowRatio = -0.1f) }
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Canny(minMagnitude = -1f) }
    }
}
