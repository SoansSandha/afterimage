package io.github.soanssandha.afterimage.core.outline

/** The thresholded difference of Gaussians behind [EdgeDetector.Xdog]. */
internal object XdogEdges {

    fun detect(image: GrayImage, params: EdgeDetector.Xdog): EdgeMask {
        val narrow = GaussianBlur.blur(image, params.sigma).pixels
        val wide = GaussianBlur.blur(image, params.k * params.sigma).pixels
        val edges = BooleanArray(narrow.size) { i -> narrow[i] - params.tau * wide[i] < params.epsilon }
        return EdgeMask(image.width, image.height, edges)
    }
}
