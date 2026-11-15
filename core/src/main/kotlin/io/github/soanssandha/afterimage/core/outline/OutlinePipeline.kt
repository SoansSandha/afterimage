package io.github.soanssandha.afterimage.core.outline

/**
 * Photo to outline (docs/plan.md §5.3): find the lines, drop specks, then measure every pixel's distance to them.
 * Runs once per photo and settings change, off the main thread; the input is already scaled to working size. To try
 * several detail levels on one photo, [OutlineSource] keeps the slow half between runs.
 */
object OutlinePipeline {

    fun run(image: GrayImage, params: OutlineParams = OutlineParams()): OutlineField =
        finish(params.detector.detect(image), params.minComponentPixels)

    /** Drops specks from [edges] and measures the distances: the steps after detection. */
    internal fun finish(edges: EdgeMask, minComponentPixels: Int): OutlineField {
        val lines = ComponentFilter.removeSmall(edges, minComponentPixels)
        return OutlineField(edges.width, edges.height, lines, DistanceTransform.of(lines))
    }
}
