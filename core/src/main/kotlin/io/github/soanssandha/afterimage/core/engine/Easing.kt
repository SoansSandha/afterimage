package io.github.soanssandha.afterimage.core.engine

/** Easing curves on 0..1. Inputs outside 0..1 are clamped. */
object Easing {

    /** Slow start, fast middle, slow end. */
    fun inOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
    }
}
