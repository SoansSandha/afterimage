package io.github.soanssandha.afterimage.core.burnin

/** A shift in whole pixels: positive [dx] moves right, positive [dy] moves down. */
data class Offset(val dx: Int, val dy: Int) {
    companion object {
        val ZERO = Offset(0, 0)
    }
}
