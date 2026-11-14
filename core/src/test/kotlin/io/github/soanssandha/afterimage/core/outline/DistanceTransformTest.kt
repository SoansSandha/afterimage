package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceTransformTest {

    @Test
    fun `no lines means every distance is infinite`() {
        assertTrue(DistanceTransform.of(EdgeMask(7, 4)).all { it == Float.POSITIVE_INFINITY })
    }

    @Test
    fun `a single line pixel gives exact Euclidean distances`() {
        val d = DistanceTransform.of(TestImages.mask(9, 7, 3 to 2))

        assertEquals(0f, d[2 * 9 + 3])
        assertEquals(5f, d[6 * 9 + 6], 1e-5f) // (3, 4) away
        assertEquals(sqrt(2f), d[3 * 9 + 4], 1e-5f)
        assertEquals(sqrt(9f + 4f), d[0], 1e-5f)
    }

    @Test
    fun `matches brute force on random masks, including rows and columns with no lines`() {
        for (seed in 1..20) {
            val mask = TestImages.randomMask(23, 17, density = if (seed % 4 == 0) 0.005f else 0.04f, seed = seed)
            if (mask.count() == 0) continue
            val fast = DistanceTransform.of(mask)
            val lines = (0 until 23 * 17).filter { mask.edges[it] }
            for (y in 0 until 17) {
                for (x in 0 until 23) {
                    val expected = lines.minOf { p ->
                        val dx = (x - p % 23).toFloat()
                        val dy = (y - p / 23).toFloat()
                        sqrt(dx * dx + dy * dy)
                    }
                    assertEquals(expected, fast[y * 23 + x], 1e-4f, "seed $seed at ($x, $y)")
                }
            }
        }
    }
}
