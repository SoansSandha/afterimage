package io.github.soanssandha.afterimage.core.outline

/**
 * Photo to outline (docs/plan.md §5.3): find the lines, drop specks, then measure every pixel's distance to them.
 * Runs once per photo and settings change, off the main thread; the input is already scaled to working size.
 */
object OutlinePipeline {

    fun run(image: GrayImage, params: OutlineParams = OutlineParams()): OutlineField {
        val lines = ComponentFilter.removeSmall(params.detector.detect(image), params.minComponentPixels)
        return OutlineField(image.width, image.height, lines, DistanceTransform.of(lines))
    }
}
