package io.github.soanssandha.afterimage.core.outline

import kotlin.math.abs
import kotlin.math.sqrt

/** The Canny steps behind [EdgeDetector.Canny]: blur, Sobel gradients, non-maximum suppression, hysteresis. */
internal object CannyEdges {

    // tan(22.5°) and tan(67.5°): the borders between horizontal, diagonal and vertical gradient directions.
    private const val TAN_22_5 = 0.41421357f
    private const val TAN_67_5 = 2.4142137f

    fun detect(image: GrayImage, params: EdgeDetector.Canny): EdgeMask {
        val blurred = GaussianBlur.blur(image, params.sigma)
        val w = image.width
        val h = image.height
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val magnitude = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val tl = blurred.clamped(x - 1, y - 1)
                val tc = blurred.clamped(x, y - 1)
                val tr = blurred.clamped(x + 1, y - 1)
                val ml = blurred.clamped(x - 1, y)
                val mr = blurred.clamped(x + 1, y)
                val bl = blurred.clamped(x - 1, y + 1)
                val bc = blurred.clamped(x, y + 1)
                val br = blurred.clamped(x + 1, y + 1)
                val i = y * w + x
                gx[i] = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                gy[i] = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                magnitude[i] = sqrt(gx[i] * gx[i] + gy[i] * gy[i])
            }
        }

        val thinned = suppressNonMaxima(w, h, gx, gy, magnitude)
        // Copied into a primitive array: a List<Float> would box a few hundred thousand values per photo.
        val candidates = FloatArray(thinned.count { it > 0f })
        if (candidates.isEmpty()) return EdgeMask(w, h)
        var next = 0
        for (m in thinned) if (m > 0f) candidates[next++] = m
        candidates.sort()
        val percentile = candidates[(params.highPercentile * (candidates.size - 1)).toInt()]
        val high = maxOf(percentile, params.minMagnitude)
        return hysteresis(w, h, thinned, high = high, low = high * params.lowRatio)
    }

    /** Keeps each gradient only where it peaks across the edge, so lines come out one pixel wide. */
    private fun suppressNonMaxima(w: Int, h: Int, gx: FloatArray, gy: FloatArray, magnitude: FloatArray): FloatArray {
        val out = FloatArray(w * h)
        fun at(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) magnitude[y * w + x] else 0f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val m = magnitude[i]
                if (m == 0f) continue
                val ax = abs(gx[i])
                val ay = abs(gy[i])
                // The two neighbours along the gradient. y grows downwards, so equal signs point down-right.
                val (n1, n2) = when {
                    ay <= ax * TAN_22_5 -> at(x - 1, y) to at(x + 1, y)
                    ay >= ax * TAN_67_5 -> at(x, y - 1) to at(x, y + 1)
                    (gx[i] > 0f) == (gy[i] > 0f) -> at(x - 1, y - 1) to at(x + 1, y + 1)
                    else -> at(x + 1, y - 1) to at(x - 1, y + 1)
                }
                // Strict on one side, so a two-pixel plateau keeps exactly one pixel.
                if (m > n1 && m >= n2) out[i] = m
            }
        }
        return out
    }

    /** Strong pixels, plus weak ones connected to them through other weak or strong pixels (8-connected). */
    private fun hysteresis(w: Int, h: Int, thinned: FloatArray, high: Float, low: Float): EdgeMask {
        val edges = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        for (i in thinned.indices) {
            if (thinned[i] >= high && !edges[i]) {
                edges[i] = true
                stack[top++] = i
                while (top > 0) {
                    val p = stack[--top]
                    val px = p % w
                    val py = p / w
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = px + dx
                            val ny = py + dy
                            if (nx !in 0 until w || ny !in 0 until h) continue
                            val n = ny * w + nx
                            if (!edges[n] && thinned[n] >= low && thinned[n] > 0f) {
                                edges[n] = true
                                stack[top++] = n
                            }
                        }
                    }
                }
            }
        }
        return EdgeMask(w, h, edges)
    }
}
