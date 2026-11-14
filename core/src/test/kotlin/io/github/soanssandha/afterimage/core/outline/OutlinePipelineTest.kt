package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlinePipelineTest {

    @Test
    fun `a subject on a plain background becomes a closed outline within the lit budget`() {
        // Phone-like proportions: a long outline on a small image would overstate the lit share.
        val photo = TestImages.rectangle(300, 500, 75, 125, 225, 375)

        val field = OutlinePipeline.run(photo)

        assertTrue(field.lines.count() > 700)
        for (i in field.distances.indices) {
            if (field.lines.edges[i]) assertEquals(0f, field.distances[i])
        }
        // The centre's nearest line is the left or right side, about 75 px away.
        assertTrue(field.distances[250 * 300 + 150] in 70f..80f)
        val lit = field.litFraction(OutlineStyle())
        assertTrue(lit in 0.01f..0.15f, "lit fraction $lit")
        val mean = field.meanIntensity(OutlineStyle())
        assertTrue(mean > 0f && mean < lit, "mean intensity $mean")
    }

    @Test
    fun `specks are dropped before the distance field`() {
        // A one-pixel dark dot, which XDoG strokes but which is far smaller than the speck limit.
        val photo = TestImages.of(60, 60) { x, y -> if (x == 40 && y == 40) 0.2f else 0.8f }
        val params = OutlineParams(detector = EdgeDetector.Xdog(), minComponentPixels = 24)

        assertTrue(params.detector.detect(photo).count() > 0)
        val field = OutlinePipeline.run(photo, params)
        assertEquals(0, field.lines.count())
        assertTrue(field.distances.all { it == Float.POSITIVE_INFINITY })
        assertEquals(0f, field.litFraction(OutlineStyle()))
    }

    @Test
    fun `a blank photo gives an empty outline`() {
        val field = OutlinePipeline.run(TestImages.flat(32, 48, 0.05f))

        assertFalse(field.lines.edges.any { it })
        assertEquals(0f, field.litFraction(OutlineStyle()))
        assertEquals(0f, field.meanIntensity(OutlineStyle()))
    }

    @Test
    fun `rejects inconsistent fields and parameters`() {
        assertFailsWith<IllegalArgumentException> { OutlineParams(minComponentPixels = -1) }
        assertFailsWith<IllegalArgumentException> { OutlineField(4, 4, EdgeMask(4, 3), FloatArray(16)) }
        assertFailsWith<IllegalArgumentException> { OutlineField(4, 4, EdgeMask(5, 4), FloatArray(16)) }
        assertFailsWith<IllegalArgumentException> { OutlineField(4, 4, EdgeMask(4, 4), FloatArray(15)) }
    }
}
