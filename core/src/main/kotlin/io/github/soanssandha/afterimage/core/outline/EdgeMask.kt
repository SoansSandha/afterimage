package io.github.soanssandha.afterimage.core.outline

/** Which pixels are on a line, row-major. [edges] is owned by this mask: stages return new masks. */
class EdgeMask(val width: Int, val height: Int, val edges: BooleanArray = BooleanArray(width * height)) {

    init {
        require(width > 0 && height > 0) { "Mask must be at least 1x1, was ${width}x$height" }
        require(edges.size == width * height) { "Expected ${width * height} pixels, got ${edges.size}" }
    }

    operator fun get(x: Int, y: Int): Boolean = edges[y * width + x]

    /** How many pixels are on a line. */
    fun count(): Int = edges.count { it }
}
