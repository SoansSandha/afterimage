package io.github.soanssandha.afterimage.core.outline

/** Settings that change the outline's geometry, so changing them means running the pipeline again. */
data class OutlineParams(
    val detector: EdgeDetector = EdgeDetector.Canny(),
    /** Groups of touching line pixels smaller than this are dropped as specks. */
    val minComponentPixels: Int = 24,
) {
    init {
        require(minComponentPixels >= 0) { "minComponentPixels must be 0 or more, was $minComponentPixels" }
    }
}
