package io.github.soanssandha.afterimage.core.outline

/**
 * The pipeline's result: the cleaned-up line pixels and every pixel's distance to them. The app caches this per photo
 * and the renderer draws from [distances].
 */
class OutlineField(val width: Int, val height: Int, val lines: EdgeMask, val distances: FloatArray) {

    init {
        require(lines.width == width && lines.height == height) { "Mask size doesn't match ${width}x$height" }
        require(distances.size == width * height) { "Expected ${width * height} distances, got ${distances.size}" }
    }

    /**
     * The share of pixels that [style] lights above [threshold]: a proxy for what the outline costs on an OLED panel
     * and how much of it can burn in (docs/plan.md §5.8 caps it at 15 %).
     */
    fun litFraction(style: OutlineStyle, threshold: Float = 0.05f): Float {
        var lit = 0
        for (d in distances) if (OutlineShading.intensity(d, style) > threshold) lit++
        return lit.toFloat() / distances.size
    }

    /** Mean brightness 0..1 over the whole frame under [style]: roughly proportional to an OLED panel's draw. */
    fun meanIntensity(style: OutlineStyle): Float {
        var sum = 0.0
        for (d in distances) sum += OutlineShading.intensity(d, style)
        return (sum / distances.size).toFloat()
    }
}
