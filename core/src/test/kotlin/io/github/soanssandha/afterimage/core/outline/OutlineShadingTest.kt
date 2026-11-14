package io.github.soanssandha.afterimage.core.outline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OutlineShadingTest {

    @Test
    fun `the core is solid, then fades over one pixel`() {
        val style = OutlineStyle(halfWidth = 1f, glowRadius = 0f, glowStrength = 0f)

        assertEquals(1f, OutlineShading.intensity(0f, style))
        assertEquals(1f, OutlineShading.intensity(0.5f, style))
        assertEquals(0.5f, OutlineShading.intensity(1f, style), 1e-6f)
        assertEquals(0f, OutlineShading.intensity(1.5f, style))
        assertEquals(0f, OutlineShading.intensity(Float.POSITIVE_INFINITY, style))
    }

    @Test
    fun `the glow fades with distance and never exceeds its strength outside the core`() {
        val style = OutlineStyle(halfWidth = 0.5f, glowRadius = 4f, glowStrength = 0.4f)

        val far = (2..40).map { OutlineShading.intensity(it.toFloat(), style) }

        assertTrue(far.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(far.all { it <= 0.4f })
        assertEquals(0.4f * kotlin.math.exp(-0.5f), OutlineShading.intensity(2f, style), 1e-6f)
        assertEquals(0f, OutlineShading.intensity(Float.POSITIVE_INFINITY, style))
    }

    @Test
    fun `smoothstep matches GLSL at its ends and middle`() {
        assertEquals(0f, OutlineShading.smoothstep(0f, 1f, -1f))
        assertEquals(0.5f, OutlineShading.smoothstep(0f, 1f, 0.5f))
        assertEquals(1f, OutlineShading.smoothstep(0f, 1f, 2f))
    }

    @Test
    fun `the style rejects values out of range`() {
        assertFailsWith<IllegalArgumentException> { OutlineStyle(halfWidth = -1f) }
        assertFailsWith<IllegalArgumentException> { OutlineStyle(glowRadius = -1f) }
        assertFailsWith<IllegalArgumentException> { OutlineStyle(glowStrength = 1.5f) }
    }
}
