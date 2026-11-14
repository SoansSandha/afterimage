package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdogEdgesTest {

    private val xdog = EdgeDetector.Xdog()

    @Test
    fun `flat images have no lines, including near-black ones`() {
        assertEquals(0, xdog.detect(TestImages.flat(20, 20, 0.7f)).count())
        assertEquals(0, xdog.detect(TestImages.flat(20, 20, 0.0f)).count())
    }

    @Test
    fun `a step gets a stroke on its dark side only`() {
        val mask = xdog.detect(TestImages.of(40, 20) { x, _ -> if (x < 20) 0.15f else 0.85f })

        assertTrue((0 until 20).all { y -> (16 until 20).any { x -> mask[x, y] } }, "no stroke beside the step")
        assertTrue((0 until 20).none { y -> (20 until 40).any { x -> mask[x, y] } }, "stroke on the light side")
        assertTrue((0 until 20).none { y -> (0 until 12).any { x -> mask[x, y] } }, "stroke far from the step")
    }

    @Test
    fun `rejects settings out of range`() {
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Xdog(sigma = 0f) }
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Xdog(k = 1f) }
        assertFailsWith<IllegalArgumentException> { EdgeDetector.Xdog(tau = 1.5f) }
    }
}
