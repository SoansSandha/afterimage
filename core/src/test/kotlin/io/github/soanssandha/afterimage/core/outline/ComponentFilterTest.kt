package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComponentFilterTest {

    @Test
    fun `drops specks and keeps lines`() {
        val line = (2..9).map { x -> x to 2 }.toTypedArray()
        val mask = TestImages.mask(12, 8, *line, 5 to 6, 10 to 6)

        val out = ComponentFilter.removeSmall(mask, minPixels = 3)

        assertEquals(8, out.count())
        assertTrue(out[2, 2] && out[9, 2])
        assertFalse(out[5, 6] || out[10, 6])
    }

    @Test
    fun `diagonal neighbours count as one group`() {
        val staircase = TestImages.mask(6, 6, 0 to 0, 1 to 1, 2 to 2, 3 to 3)

        assertEquals(4, ComponentFilter.removeSmall(staircase, minPixels = 4).count())
        assertEquals(0, ComponentFilter.removeSmall(staircase, minPixels = 5).count())
    }

    @Test
    fun `a minimum of 0 or 1 keeps everything, as a copy`() {
        val mask = TestImages.randomMask(10, 10, density = 0.3f, seed = 3)

        for (min in 0..1) {
            val out = ComponentFilter.removeSmall(mask, min)
            assertContentEquals(mask.edges, out.edges)
        }
    }

    @Test
    fun `rejects a negative minimum`() {
        assertFailsWith<IllegalArgumentException> { ComponentFilter.removeSmall(EdgeMask(2, 2), -1) }
    }
}
