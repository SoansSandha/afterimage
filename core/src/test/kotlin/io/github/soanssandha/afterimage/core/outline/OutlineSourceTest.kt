package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineSourceTest {

    // Three full-height bars on a dark, faintly noisy background: strong, medium and faint contrast. The noise gives
    // Canny the many weak candidates a real photo has, so the percentile sits low and the floor decides.
    private val strong = 40 until 100
    private val medium = 150 until 210
    private val faint = 260 until 320
    private val bars = TestImages.noisy(
        TestImages.of(360, 240) { x, _ ->
            when (x) {
                in strong -> 0.85f
                in medium -> 0.35f
                in faint -> 0.2f
                else -> 0.15f
            }
        },
        amplitude = 0.01f,
        seed = 3,
    )

    @Test
    fun `each level matches the one-shot pipeline with the same settings`() {
        val source = OutlineSource.of(bars)

        for (detail in listOf(OutlineDetail.MIN, 0.3f, OutlineDetail.DEFAULT, OutlineDetail.MAX)) {
            val reused = source.outline(detail)
            val fresh = OutlinePipeline.run(bars, OutlineDetail.params(detail))
            assertEquals(fresh.width, reused.width)
            assertEquals(fresh.height, reused.height)
            assertContentEquals(fresh.lines.edges, reused.lines.edges, "lines at $detail")
            assertContentEquals(fresh.distances, reused.distances, "distances at $detail")
        }
        assertEquals(360, source.width)
        assertEquals(240, source.height)
    }

    @Test
    fun `fewer lines keeps only strong edges and more lines adds faint ones`() {
        val source = OutlineSource.of(bars)
        val fewest = source.outline(OutlineDetail.MIN).lines
        val default = source.outline(OutlineDetail.DEFAULT).lines
        val most = source.outline(OutlineDetail.MAX).lines

        // Two edges per bar, about 240 px each.
        assertTrue(linesAround(fewest, strong) > 400)
        assertEquals(0, linesAround(fewest, medium))
        assertEquals(0, linesAround(fewest, faint))

        assertTrue(linesAround(default, strong) > 400)
        assertTrue(linesAround(default, medium) > 400)
        assertEquals(0, linesAround(default, faint))

        assertTrue(linesAround(most, faint) > 400)
    }

    @Test
    fun `more detail only ever adds lines`() {
        val source = OutlineSource.of(bars)
        val levels = (0..10).map { it / 10f }
        for ((lower, higher) in levels.zipWithNext()) {
            val a = source.outline(lower).lines
            val b = source.outline(higher).lines
            for (i in a.edges.indices) {
                if (a.edges[i]) assertTrue(b.edges[i], "pixel $i lost going from $lower to $higher")
            }
        }
    }

    @Test
    fun `a blank photo has no lines at any level`() {
        val source = OutlineSource.of(TestImages.flat(30, 40, 0.4f))

        assertEquals(0, source.outline(OutlineDetail.MAX).lines.count())
        assertTrue(source.outline(OutlineDetail.MIN).distances.all { it == Float.POSITIVE_INFINITY })
    }

    /** Line pixels in the columns of [bar], with a small margin for where Canny centres an edge. */
    private fun linesAround(mask: EdgeMask, bar: IntRange): Int {
        var count = 0
        for (y in 0 until mask.height) {
            for (x in bar.first - 3..bar.last + 3) if (mask[x, y]) count++
        }
        return count
    }
}
