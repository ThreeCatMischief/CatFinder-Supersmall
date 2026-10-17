package com.catfinder.app

import com.catfinder.app.data.ClockSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockSyncTest {
    @Test fun usesLowestRttSample() {
        val c = ClockSync()
        assertNull(c.offset())
        c.onSample(sentAt = 1_000, rttMs = 400, espMs = 5_300)  // offset 5_300 - 1_200 = 4_100
        c.onSample(sentAt = 2_000, rttMs = 40, espMs = 6_020)   // offset 6_020 - 2_020 = 4_000
        assertEquals(4_000L, c.offset())
        assertEquals(20L, c.uncertainty())
    }

    @Test fun ignoresSlowSamplesAndResetsOnReboot() {
        val c = ClockSync()
        c.onSample(sentAt = 1_000, rttMs = 20, espMs = 50_010)
        c.onSample(sentAt = 2_000, rttMs = 5_000, espMs = 1)    // too slow, ignored
        assertEquals(49_000L, c.offset())
        c.onSample(sentAt = 3_000, rttMs = 60, espMs = 500)     // ESP rebooted
        assertEquals(500L - 3_030L, c.offset())
    }
}
