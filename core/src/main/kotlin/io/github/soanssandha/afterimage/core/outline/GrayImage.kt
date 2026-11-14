package io.github.soanssandha.afterimage.core.outline

/**
 * A single-channel image, row-major, with values normally between 0 (black) and 1 (white). The pipeline works on
 * these rather than on platform bitmaps, so it runs the same in unit tests, the outline lab and the app.
 *
 * [pixels] is exposed for speed and is owned by this image: stages return new images instead of changing their input.
 */
class GrayImage(val width: Int, val height: Int, val pixels: FloatArray = FloatArray(width * height)) {

    init {
        require(width > 0 && height > 0) { "Image must be at least 1x1, was ${width}x$height" }
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Float = pixels[y * width + x]

    /** The pixel at ([x], [y]), with coordinates outside the image moved to the nearest edge pixel. */
    fun clamped(x: Int, y: Int): Float = pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

    companion object {
        /**
         * The luma of packed ARGB pixels (as `Bitmap.getPixels` and `BufferedImage.getRGB` return them), using
         * Rec. 709 weights on the stored sRGB values. Alpha is ignored: wallpapers are opaque.
         */
        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage {
            require(argb.size == width * height) { "Expected ${width * height} pixels, got ${argb.size}" }
            val out = FloatArray(argb.size)
            for (i in argb.indices) {
                val c = argb[i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                out[i] = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            }
            return GrayImage(width, height, out)
        }
    }
}
