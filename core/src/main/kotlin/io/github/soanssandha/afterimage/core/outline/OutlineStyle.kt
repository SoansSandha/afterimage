package io.github.soanssandha.afterimage.core.outline

/**
 * How a line is drawn from its distance field: a solid core [halfWidth] px either side of the edge, with a soft
 * 1 px rim, plus a glow that fades over [glowRadius] px starting at [glowStrength]. These are the "thickness" and
 * glow settings; changing them needs no recomputation.
 */
data class OutlineStyle(val halfWidth: Float = 1.0f, val glowRadius: Float = 4.0f, val glowStrength: Float = 0.35f) {
    init {
        require(halfWidth >= 0f) { "halfWidth must be 0 or more, was $halfWidth" }
        require(glowRadius >= 0f) { "glowRadius must be 0 or more, was $glowRadius" }
        require(glowStrength in 0f..1f) { "glowStrength must be within 0..1, was $glowStrength" }
    }
}
