package io.github.soanssandha.afterimage.core.burnin

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BurnInShiftTest {

    @Test
    fun `the default steps through eight distinct spots around a circle`() {
        val offsets = (0L until 8L).map(BurnInShift.DEFAULT::offsetFor)

        assertEquals(8, offsets.toSet().size)
        assertEquals(Offset(6, 0), offsets[0])
        assertEquals(Offset(0, 6), offsets[2])
        assertEquals(Offset(-6, 0), offsets[4])
        assertEquals(Offset(0, -6), offsets[6])
    }

    @Test
    fun `positions repeat after a full circle`() {
        val shift = BurnInShift(radiusPx = 5, positions = 7)

        for (entry in 0L until 21L) {
            assertEquals(shift.offsetFor(entry), shift.offsetFor(entry + 7))
        }
    }

    @Test
    fun `negative and very large entry counts still land on a position`() {
        val shift = BurnInShift(radiusPx = 4, positions = 4)

        assertEquals(shift.offsetFor(3), shift.offsetFor(-1))
        assertEquals(shift.offsetFor(Long.MAX_VALUE % 4), shift.offsetFor(Long.MAX_VALUE))
    }

    @Test
    fun `no offset strays more than a pixel past the radius`() {
        val shift = BurnInShift(radiusPx = 9, positions = 13)

        for (entry in 0L until 13L) {
            val (dx, dy) = shift.offsetFor(entry)
            assertTrue(sqrt((dx * dx + dy * dy).toDouble()) <= 10.0, "entry $entry: ($dx, $dy)")
        }
    }

    @Test
    fun `a zero radius turns the shift off`() {
        val shift = BurnInShift(radiusPx = 0, positions = 8)

        assertTrue((0L until 8L).all { shift.offsetFor(it) == Offset.ZERO })
    }

    @Test
    fun `rejects a negative radius or no positions`() {
        assertFailsWith<IllegalArgumentException> { BurnInShift(radiusPx = -1, positions = 8) }
        assertFailsWith<IllegalArgumentException> { BurnInShift(radiusPx = 6, positions = 0) }
    }
}
