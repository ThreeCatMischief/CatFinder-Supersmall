package com.catfinder.app

import com.catfinder.app.data.VideoGovernor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGovernorTest {
    /** Feeds [count] samples of [rtt] every 100 ms starting at [from]; returns the next time. */
    private fun VideoGovernor.feed(from: Long, count: Int, rtt: Long?): Long {
        var t = from
        repeat(count) {
            onSample(t, rtt)
            t += 100
        }
        return t
    }

    @Test
    fun steadyLowRttClimbsToBest() {
        val g = VideoGovernor(start = 3)
        g.feed(0, 300, 80)  // 30 s
        assertEquals(0, g.level)
    }

    @Test
    fun rttRiseStepsDown() {
        val g = VideoGovernor(start = 0)
        val t = g.feed(0, 20, 80)
        assertTrue(g.onSample(t, 300))
        assertEquals(1, g.level)
    }

    @Test
    fun stepsDownAtMostOncePerHold() {
        val g = VideoGovernor(start = 0)
        val t = g.feed(0, 20, 80)
        g.onSample(t, 300)
        assertFalse(g.onSample(t + 200, 400))
        assertEquals(1, g.level)
        assertTrue(g.onSample(t + 1_100, 400))
        assertEquals(2, g.level)
    }

    @Test
    fun failureStepsDownTwo() {
        val g = VideoGovernor(start = 0)
        g.onSample(0, null)
        assertEquals(2, g.level)
    }

    @Test
    fun stopsAtCheapestLevel() {
        val g = VideoGovernor(start = VideoGovernor.LEVELS - 1)
        assertFalse(g.onSample(0, null))
        assertEquals(VideoGovernor.LEVELS - 1, g.level)
    }

    @Test
    fun mildJitterBlocksStepUpButNotDown() {
        val g = VideoGovernor(start = 2)
        var t = g.feed(0, 10, 80)
        repeat(100) {
            // 100 ms above baseline every second: not enough to step down, too much to step up.
            g.onSample(t, if (it % 10 == 0) 180 else 80)
            t += 100
        }
        assertEquals(2, g.level)
    }

    @Test
    fun baselineFollowsSlowerLink() {
        val g = VideoGovernor(start = 1)
        var t = g.feed(0, 10, 40)
        t = g.feed(t, 1, 300)  // one step down
        // The link is now steadily slower; once the old baseline ages out it stops counting as congestion.
        g.feed(t, 900, 250)
        assertEquals(0, g.level)
    }
}
