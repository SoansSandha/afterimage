package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeMaskTest {

    @Test
    fun `reads and counts line pixels`() {
        val mask = TestImages.mask(3, 2, 0 to 0, 2 to 1)

        assertTrue(mask[0, 0])
        assertTrue(mask[2, 1])
        assertFalse(mask[1, 0])
        assertEquals(2, mask.count())
    }

    @Test
    fun `rejects empty sizes and mismatched pixel counts`() {
        assertFailsWith<IllegalArgumentException> { EdgeMask(0, 1) }
        assertFailsWith<IllegalArgumentException> { EdgeMask(1, 0) }
        assertFailsWith<IllegalArgumentException> { EdgeMask(2, 2, BooleanArray(5)) }
    }
}
