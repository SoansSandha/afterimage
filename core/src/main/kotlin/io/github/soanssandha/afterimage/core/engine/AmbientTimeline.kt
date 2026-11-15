package io.github.soanssandha.afterimage.core.engine

import kotlin.math.abs

/**
 * Photo ↔ outline progress over time: 0 is the photo, 1 the outline. The wallpaper engine reports the ambient-mode
 * changes SystemUI sends and asks for the progress on each frame; this class never reads a clock (docs/plan.md §5.2).
 *
 * Entering AOD animates over [enterMs] (ADR 0003 caps it at 4 s; it has to finish inside SystemUI's doze-entry window).
 * Leaving uses SystemUI's own animation duration when it gives one, else [exitMs]. A change that arrives mid-animation
 * reverses from wherever the progress is, taking only the share of the time that's left to cover.
 */
class AmbientTimeline(val enterMs: Long = DEFAULT_ENTER_MS, val exitMs: Long = DEFAULT_EXIT_MS) {

    init {
        require(enterMs in 0..MAX_ENTER_MS) { "enterMs must be within 0..$MAX_ENTER_MS (ADR 0003), was $enterMs" }
        require(exitMs >= 0) { "exitMs must be 0 or more, was $exitMs" }
    }

    /** Whether the phone is in AOD, as last reported. */
    var ambient: Boolean = false
        private set

    /** How many times the phone has entered AOD: picks the burn-in offset (`BurnInShift.offsetFor`). */
    var entries: Long = 0
        private set

    private var from = 0f
    private var target = 0f
    private var startMs = 0L
    private var durationMs = 0L

    /**
     * SystemUI reported AOD starting ([ambient] true) or ending at [nowMs], with [requestedMs] being its animation
     * duration (0 or less for none). Returns false, changing nothing, if it repeats the current state.
     */
    fun onAmbientChanged(ambient: Boolean, nowMs: Long, requestedMs: Long = 0): Boolean {
        if (ambient == this.ambient) return false
        val current = rawProgress(nowMs)
        this.ambient = ambient
        if (ambient) entries++
        from = current
        target = if (ambient) 1f else 0f
        startMs = nowMs
        val full = when {
            ambient -> enterMs
            requestedMs > 0 -> requestedMs
            else -> exitMs
        }
        durationMs = (full * abs(target - current)).toLong()
        return true
    }

    /** Ends any animation at once, e.g. when the phone unlocks straight from AOD and the wallpaper isn't visible. */
    fun snap() {
        from = target
        durationMs = 0
    }

    /** Eased progress at [nowMs]: 0 is the photo, 1 the outline. */
    fun progress(nowMs: Long): Float = Easing.inOutCubic(rawProgress(nowMs))

    /** Whether frames are still needed at [nowMs]. Once false, one last frame at [progress] is the resting state. */
    fun isAnimating(nowMs: Long): Boolean = durationMs > 0 && nowMs < startMs + durationMs

    private fun rawProgress(nowMs: Long): Float {
        if (durationMs <= 0) return target
        val t = ((nowMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        return from + (target - from) * t
    }

    companion object {
        const val DEFAULT_ENTER_MS = 1_800L
        const val DEFAULT_EXIT_MS = 500L
        const val MAX_ENTER_MS = 4_000L
    }
}
