package io.github.soanssandha.afterimage.core.outline

/**
 * A photo ready to be outlined at any [OutlineDetail] level. Making it does the slow half of the pipeline once (blur,
 * gradients, thinning); [outline] then does the rest for one level (thresholds, speck removal, distances), so the
 * detail slider can redraw without starting over. The input is already scaled to working size.
 */
class OutlineSource private constructor(private val gradients: CannyGradients) {

    val width: Int get() = gradients.width
    val height: Int get() = gradients.height

    /** The same outline [OutlinePipeline.run] makes with `OutlineDetail.params(detail)`. */
    fun outline(detail: Float): OutlineField {
        val edges = CannyEdges.threshold(gradients, OutlineDetail.canny(detail))
        return OutlinePipeline.finish(edges, OutlineDetail.minComponentPixels(detail))
    }

    companion object {
        /** Every [OutlineDetail] level shares the default blur, so it's applied here once. */
        fun of(image: GrayImage): OutlineSource = OutlineSource(CannyEdges.gradients(image, EdgeDetector.Canny().sigma))
    }
}
