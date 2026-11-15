package io.github.soanssandha.afterimage.core.outline

/**
 * The first half of Canny ([CannyEdges.gradients]): each pixel's gradient magnitude where it peaks across an edge, 0
 * elsewhere, and the non-zero magnitudes sorted for the percentile threshold. Read-only once made.
 */
internal class CannyGradients(
    val width: Int,
    val height: Int,
    val thinned: FloatArray,
    val sortedCandidates: FloatArray,
)
