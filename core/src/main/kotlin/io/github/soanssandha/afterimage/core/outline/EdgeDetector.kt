package io.github.soanssandha.afterimage.core.outline

/** How lines are found in a photo. Each variant holds its own settings; the outline lab compares them. */
sealed interface EdgeDetector {

    fun detect(image: GrayImage): EdgeMask

    /**
     * Canny: thin lines centred on edges. The image is blurred by [sigma] px; the strong-edge threshold is the
     * gradient at [highPercentile] of the candidate pixels, but never below [minMagnitude] (so a near-flat photo
     * doesn't turn its noise into lines); weak edges down to [lowRatio] of it are kept where they touch strong ones.
     *
     * Gradients depend on scale, so these defaults assume the 1536 px working size. [minMagnitude] = 0.25 came from
     * the outline lab on a dark, shallow-focus photo: it keeps the sharp subject and drops soft, out-of-focus edges,
     * lighting about 10 % of the screen (0.08 lit 24 %). The app's Detail slider moves it, through [OutlineDetail].
     */
    data class Canny(
        val sigma: Float = 1.4f,
        val highPercentile: Float = 0.85f,
        val lowRatio: Float = 0.4f,
        val minMagnitude: Float = 0.25f,
    ) : EdgeDetector {
        init {
            require(sigma >= 0f) { "sigma must be 0 or more, was $sigma" }
            require(highPercentile in 0f..1f) { "highPercentile must be within 0..1, was $highPercentile" }
            require(lowRatio in 0f..1f) { "lowRatio must be within 0..1, was $lowRatio" }
            require(minMagnitude >= 0f) { "minMagnitude must be 0 or more, was $minMagnitude" }
        }

        override fun detect(image: GrayImage): EdgeMask = CannyEdges.detect(image, this)
    }

    /**
     * XDoG (extended difference of Gaussians): stylised strokes on the dark side of edges. A pixel is on a line where
     * `blur(sigma) - tau * blur(k * sigma)` falls below [epsilon]; a slightly negative [epsilon] keeps large dark
     * areas, which are common in AOD-friendly photos, free of speckle.
     */
    data class Xdog(
        val sigma: Float = 1.0f,
        val k: Float = 1.6f,
        val tau: Float = 0.98f,
        val epsilon: Float = -0.01f,
    ) : EdgeDetector {
        init {
            require(sigma > 0f) { "sigma must be above 0, was $sigma" }
            require(k > 1f) { "k must be above 1, was $k" }
            require(tau in 0f..1f) { "tau must be within 0..1, was $tau" }
        }

        override fun detect(image: GrayImage): EdgeMask = XdogEdges.detect(image, this)
    }
}
