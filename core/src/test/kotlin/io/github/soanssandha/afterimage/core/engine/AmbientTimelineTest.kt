package io.github.soanssandha.afterimage.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmbientTimelineTest {

    @Test
    fun `starts on the photo, at rest`() {
        val timeline = AmbientTimeline()

        assertEquals(0f, timeline.progress(0))
        assertFalse(timeline.isAnimating(0))
        assertFalse(timeline.ambient)
        assertEquals(0, timeline.entries)
    }

    @Test
    fun `entering AOD animates to the outline over the entry time`() {
        val timeline = AmbientTimeline(enterMs = 2_000, exitMs = 500)

        assertTrue(timeline.onAmbientChanged(true, nowMs = 1_000))

        assertEquals(0f, timeline.progress(1_000))
        assertEquals(0.5f, timeline.progress(2_000), 1e-6f)
        assertTrue(timeline.isAnimating(2_999))
        assertFalse(timeline.isAnimating(3_000))
        assertEquals(1f, timeline.progress(3_000))
        assertEquals(1f, timeline.progress(60_000))
        assertTrue(timeline.ambient)
        assertEquals(1, timeline.entries)
    }

    @Test
    fun `leaving uses SystemUI's duration when it gives one, else the default`() {
        val timeline = AmbientTimeline(enterMs = 1_000, exitMs = 500)
        timeline.onAmbientChanged(true, nowMs = 0)

        timeline.onAmbientChanged(false, nowMs = 5_000, requestedMs = 800)
        assertTrue(timeline.isAnimating(5_799))
        assertFalse(timeline.isAnimating(5_800))
        assertEquals(0f, timeline.progress(5_800))

        timeline.onAmbientChanged(true, nowMs = 10_000)
        timeline.onAmbientChanged(false, nowMs = 20_000, requestedMs = 0)
        assertTrue(timeline.isAnimating(20_499))
        assertFalse(timeline.isAnimating(20_500))
    }

    @Test
    fun `a change mid-animation reverses from where it is, in proportional time`() {
        val timeline = AmbientTimeline(enterMs = 2_000, exitMs = 1_000)
        timeline.onAmbientChanged(true, nowMs = 0)
        val midway = timeline.progress(1_000)

        timeline.onAmbientChanged(false, nowMs = 1_000)

        assertEquals(midway, timeline.progress(1_000), 1e-6f)
        // Half the way back at the 1 s exit rate takes 500 ms.
        assertTrue(timeline.isAnimating(1_499))
        assertFalse(timeline.isAnimating(1_500))
        assertEquals(0f, timeline.progress(1_500))
    }

    @Test
    fun `a repeated state changes nothing`() {
        val timeline = AmbientTimeline()
        timeline.onAmbientChanged(true, nowMs = 0)

        assertFalse(timeline.onAmbientChanged(true, nowMs = 500))
        assertFalse(AmbientTimeline().onAmbientChanged(false, nowMs = 0))
        assertEquals(1, timeline.entries)
    }

    @Test
    fun `counts every AOD entry for the burn-in shift`() {
        val timeline = AmbientTimeline()
        for (i in 0 until 5) {
            timeline.onAmbientChanged(true, nowMs = i * 10_000L)
            timeline.onAmbientChanged(false, nowMs = i * 10_000L + 5_000)
        }

        assertEquals(5, timeline.entries)
    }

    @Test
    fun `snap ends the animation at its target`() {
        val timeline = AmbientTimeline(enterMs = 2_000)
        timeline.onAmbientChanged(true, nowMs = 0)

        timeline.snap()

        assertFalse(timeline.isAnimating(100))
        assertEquals(1f, timeline.progress(100))
    }

    @Test
    fun `rejects an entry over the AOD budget or a negative exit`() {
        assertFailsWith<IllegalArgumentException> { AmbientTimeline(enterMs = 4_001) }
        assertFailsWith<IllegalArgumentException> { AmbientTimeline(enterMs = -1) }
        assertFailsWith<IllegalArgumentException> { AmbientTimeline(exitMs = -1) }
    }

    @Test
    fun `the easing is symmetric and clamped`() {
        assertEquals(0f, Easing.inOutCubic(-1f))
        assertEquals(0.5f, Easing.inOutCubic(0.5f), 1e-6f)
        assertEquals(1f, Easing.inOutCubic(2f))
        assertEquals(1f - Easing.inOutCubic(0.2f), Easing.inOutCubic(0.8f), 1e-6f)
    }
}
