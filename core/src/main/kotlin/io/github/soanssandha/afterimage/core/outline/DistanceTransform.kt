package io.github.soanssandha.afterimage.core.outline

import kotlin.math.sqrt

/**
 * The exact Euclidean distance from every pixel to the nearest line pixel, in linear time (Felzenszwalb and
 * Huttenlocher, "Distance Transforms of Sampled Functions", 2012). The renderer turns these distances into line width
 * and glow, so both can change without recomputing the outline (docs/plan.md §5.3).
 */
object DistanceTransform {

    /** Distances in pixels, row-major. Every value is [Float.POSITIVE_INFINITY] when [mask] has no line pixels. */
    fun of(mask: EdgeMask): FloatArray {
        val w = mask.width
        val h = mask.height
        // Pass 1, per column: the distance to the nearest line pixel in the same column (or infinity).
        val vertical = FloatArray(w * h)
        for (x in 0 until w) {
            var last = Float.NEGATIVE_INFINITY
            for (y in 0 until h) {
                if (mask.edges[y * w + x]) last = y.toFloat()
                vertical[y * w + x] = y - last
            }
            last = Float.POSITIVE_INFINITY
            for (y in h - 1 downTo 0) {
                if (mask.edges[y * w + x]) last = y.toFloat()
                val below = last - y
                if (below < vertical[y * w + x]) vertical[y * w + x] = below
            }
        }
        // Pass 2, per row: the lower envelope of parabolas (x - q)² + vertical(q)², skipping infinite columns.
        val out = FloatArray(w * h)
        val f = FloatArray(w)
        val v = IntArray(w)
        val z = FloatArray(w + 1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val d = vertical[y * w + x]
                f[x] = if (d.isInfinite()) Float.POSITIVE_INFINITY else d * d
            }
            lowerEnvelope(f, v, z, out, y * w)
        }
        return out
    }

    /** One row of pass 2: writes sqrt(min over q of (x - q)² + f[q]) for each x into [out] from [offset]. */
    private fun lowerEnvelope(f: FloatArray, v: IntArray, z: FloatArray, out: FloatArray, offset: Int) {
        val n = f.size
        var k = -1
        for (q in 0 until n) {
            if (f[q].isInfinite()) continue
            if (k < 0) {
                k = 0
                v[0] = q
                z[0] = Float.NEGATIVE_INFINITY
                z[1] = Float.POSITIVE_INFINITY
                continue
            }
            var s = intersection(f, q, v[k])
            while (s <= z[k]) {
                k--
                s = intersection(f, q, v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.POSITIVE_INFINITY
        }
        if (k < 0) {
            out.fill(Float.POSITIVE_INFINITY, offset, offset + n)
            return
        }
        var j = 0
        for (x in 0 until n) {
            while (z[j + 1] < x) j++
            val dx = (x - v[j]).toFloat()
            out[offset + x] = sqrt(dx * dx + f[v[j]])
        }
    }

    /** Where the parabolas rooted at [q] and [p] (p < q) cross. */
    private fun intersection(f: FloatArray, q: Int, p: Int): Float =
        ((f[q] + q.toFloat() * q) - (f[p] + p.toFloat() * p)) / (2f * (q - p))
}
