package io.github.soanssandha.afterimage.core.outline

import kotlin.math.exp

/**
 * Line brightness from distance: the reference for the AGSL shader, which repeats this formula per pixel. Tests and
 * the outline lab use this copy.
 */
object OutlineShading {

    /** Brightness 0..1 of a pixel [distance] px from the nearest line. Infinite distance gives 0. */
    fun intensity(distance: Float, style: OutlineStyle): Float {
        val core = 1f - smoothstep(style.halfWidth - 0.5f, style.halfWidth + 0.5f, distance)
        val glow = if (style.glowRadius > 0f) style.glowStrength * exp(-distance / style.glowRadius) else 0f
        return maxOf(core, glow).coerceIn(0f, 1f)
    }

    /** GLSL's smoothstep: 0 below [edge0], 1 above [edge1], a smooth S-curve between. */
    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
