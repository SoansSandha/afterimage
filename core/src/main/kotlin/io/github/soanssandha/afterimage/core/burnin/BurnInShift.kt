package io.github.soanssandha.afterimage.core.burnin

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the outline sits on each AOD entry. SystemUI moves its own AOD content around to spread wear on the OLED
 * panel, but not the wallpaper, so the engine moves the outline itself: a few pixels per AOD entry, stepping around a
 * circle of [radiusPx] in [positions] steps (ADR 0003, docs/plan.md §5.2). A [radiusPx] of 0 turns the shift off.
 */
class BurnInShift(val radiusPx: Int, val positions: Int) {

    init {
        require(radiusPx >= 0) { "radiusPx must be 0 or more, was $radiusPx" }
        require(positions >= 1) { "positions must be 1 or more, was $positions" }
    }

    /** The offset for the [entry]th AOD entry. Any count works, negative or past the end: positions repeat. */
    fun offsetFor(entry: Long): Offset {
        if (radiusPx == 0) return Offset.ZERO
        val step = Math.floorMod(entry, positions.toLong())
        val angle = 2 * PI * step / positions
        return Offset(dx = (radiusPx * cos(angle)).roundToInt(), dy = (radiusPx * sin(angle)).roundToInt())
    }

    companion object {
        /** A starting point, matching what DepthWall uses (8 positions, about 6 px); tuned on the phone in Phase 1. */
        val DEFAULT = BurnInShift(radiusPx = 6, positions = 8)
    }
}
