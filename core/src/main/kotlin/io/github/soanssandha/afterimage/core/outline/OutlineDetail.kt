package io.github.soanssandha.afterimage.core.outline

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The "detail" setting: one level from [MIN] (only the strongest edges become lines) to [MAX] (soft and fine edges
 * too), mapped to Canny's thresholds and the speck size. [DEFAULT] gives exactly [EdgeDetector.Canny]'s and
 * [OutlineParams]' defaults, the ones tuned in the outline lab.
 *
 * Each step scales the thresholds by the same factor, so the slider feels even across its range. From [MIN] to [MAX]:
 * the strong-edge floor goes from 4× the default to a quarter of it, the strong-edge percentile from 0.95 to 0.75, and
 * the smallest line kept from 48 to 12 px.
 */
object OutlineDetail {
    const val MIN = 0f
    const val MAX = 1f
    const val DEFAULT = 0.5f

    // The floor and speck size change by these factors over the whole range, centred on the defaults.
    private const val FLOOR_RANGE = 16f
    private const val SPECK_RANGE = 4f
    private const val PERCENTILE_SPAN = 0.2f

    private val defaults = EdgeDetector.Canny()
    private val defaultParams = OutlineParams()

    /** [detail] within [MIN]..[MAX]; NaN, from a damaged or foreign value, becomes [DEFAULT]. */
    fun coerce(detail: Float): Float = if (detail.isNaN()) DEFAULT else detail.coerceIn(MIN, MAX)

    /** Canny at [detail], with the default blur and weak-edge ratio. */
    fun canny(detail: Float): EdgeDetector.Canny {
        val fromDefault = DEFAULT - checked(detail)
        return defaults.copy(
            highPercentile = defaults.highPercentile + fromDefault * PERCENTILE_SPAN,
            minMagnitude = defaults.minMagnitude * FLOOR_RANGE.pow(fromDefault),
        )
    }

    /** The smallest group of touching line pixels kept at [detail]; smaller ones are dropped as specks. */
    fun minComponentPixels(detail: Float): Int =
        (defaultParams.minComponentPixels * SPECK_RANGE.pow(DEFAULT - checked(detail))).roundToInt()

    fun params(detail: Float): OutlineParams = OutlineParams(canny(detail), minComponentPixels(detail))

    private fun checked(detail: Float): Float {
        require(detail in MIN..MAX) { "detail must be within $MIN..$MAX, was $detail" }
        return detail
    }
}
