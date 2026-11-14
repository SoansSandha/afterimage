package io.github.soanssandha.afterimage.core.outline

import kotlin.math.ceil
import kotlin.math.exp

/** A separable Gaussian blur. Pixels past the border repeat the edge pixel, so borders don't darken. */
object GaussianBlur {

    /** [image] blurred with standard deviation [sigma] pixels. A [sigma] of 0 returns a copy. */
    fun blur(image: GrayImage, sigma: Float): GrayImage {
        require(sigma >= 0f) { "sigma must be 0 or more, was $sigma" }
        if (sigma == 0f) return GrayImage(image.width, image.height, image.pixels.copyOf())
        val kernel = kernel(sigma)
        val radius = kernel.size / 2
        val w = image.width
        val h = image.height
        val src = image.pixels
        val horizontal = FloatArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * src[row + (x + k - radius).coerceIn(0, w - 1)]
                }
                horizontal[row + x] = sum
            }
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * horizontal[(y + k - radius).coerceIn(0, h - 1) * w + x]
                }
                out[y * w + x] = sum
            }
        }
        return GrayImage(w, h, out)
    }

    /** Normalised weights out to three standard deviations. */
    internal fun kernel(sigma: Float): FloatArray {
        val radius = ceil(3 * sigma).toInt().coerceAtLeast(1)
        val weights = FloatArray(2 * radius + 1) { i ->
            val d = (i - radius).toFloat()
            exp(-(d * d) / (2 * sigma * sigma))
        }
        val total = weights.sum()
        for (i in weights.indices) weights[i] /= total
        return weights
    }
}
