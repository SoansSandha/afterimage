package io.github.soanssandha.afterimage.core.outline

/** Removes specks: groups of touching line pixels (8-connected) that are too small to read as a line. */
object ComponentFilter {

    /** [mask] without the connected groups of fewer than [minPixels] pixels. A [minPixels] of 0 or 1 keeps all. */
    fun removeSmall(mask: EdgeMask, minPixels: Int): EdgeMask {
        require(minPixels >= 0) { "minPixels must be 0 or more, was $minPixels" }
        val w = mask.width
        val h = mask.height
        val src = mask.edges
        if (minPixels <= 1) return EdgeMask(w, h, src.copyOf())
        val out = BooleanArray(src.size)
        val seen = BooleanArray(src.size)
        val component = IntArray(src.size)
        for (start in src.indices) {
            if (!src[start] || seen[start]) continue
            // Flood fill; `component` doubles as the stack, since every pixel is pushed exactly once.
            var size = 0
            var cursor = 0
            component[size++] = start
            seen[start] = true
            while (cursor < size) {
                val p = component[cursor++]
                val px = p % w
                val py = p / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = px + dx
                        val ny = py + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val n = ny * w + nx
                        if (src[n] && !seen[n]) {
                            seen[n] = true
                            component[size++] = n
                        }
                    }
                }
            }
            if (size >= minPixels) {
                for (i in 0 until size) out[component[i]] = true
            }
        }
        return EdgeMask(w, h, out)
    }
}
