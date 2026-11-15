package io.github.soanssandha.afterimage.core.outline

/**
 * A distance field packed one byte per pixel: byte value v means a distance of `v / 255 * maxDistance` working
 * pixels; 255 means "at least [maxDistance]". The renderer uploads [bytes] as an alpha-only texture.
 */
class PackedField(val width: Int, val height: Int, val maxDistance: Float, val bytes: ByteArray) {

    init {
        require(width > 0 && height > 0) { "Field must be at least 1x1, was ${width}x$height" }
        require(maxDistance > 0f) { "maxDistance must be above 0, was $maxDistance" }
        require(bytes.size == width * height) { "Expected ${width * height} bytes, got ${bytes.size}" }
    }

    /** The distance stored for pixel ([x], [y]), in working pixels. */
    fun distanceAt(x: Int, y: Int): Float = (bytes[y * width + x].toInt() and 0xff) / 255f * maxDistance
}
