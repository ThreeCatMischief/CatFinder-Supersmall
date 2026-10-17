package com.catfinder.app.data

/**
 * Picks the ESP stream quality level (0 = best, [LEVELS]-1 = cheapest) from
 * request round-trip times. Video shares the link with /drive; when the link
 * is saturated, /drive queues behind video frames and its RTT rises above the
 * idle baseline. That rise, or a failed request, steps the video down; a quiet
 * period steps it back up.
 *
 * The baseline is the lowest RTT seen in the last [BASELINE_WINDOW_MS].
 */
class VideoGovernor(start: Int = START_LEVEL) {
    var level: Int = start.coerceIn(0, LEVELS - 1)
        private set

    private val samples = ArrayDeque<Pair<Long, Long>>()  // time, rtt
    private var lastChange = Long.MIN_VALUE / 2
    private var lastCongestion = Long.MIN_VALUE / 2
    private var firstSample: Long? = null

    /** Feeds one sample; rttMs null means the request failed. Returns true if [level] changed. */
    fun onSample(now: Long, rttMs: Long?): Boolean {
        val first = firstSample ?: now.also { firstSample = it }
        if (rttMs == null) {
            lastCongestion = now
            return stepDown(now, 2)
        }
        samples.addLast(now to rttMs)
        while (samples.first().first < now - BASELINE_WINDOW_MS) samples.removeFirst()
        val excess = rttMs - samples.minOf { it.second }

        if (excess > SLOW_MS) lastCongestion = now
        if (excess > DOWN_MS) return stepDown(now, 1)
        val quietSince = maxOf(first, lastChange, lastCongestion)
        if (level > 0 && now - quietSince >= UP_AFTER_MS) {
            level--
            lastChange = now
            return true
        }
        return false
    }

    private fun stepDown(now: Long, steps: Int): Boolean {
        // Frames already queued keep the RTT high for a while; wait before stepping again.
        if (level == LEVELS - 1 || now - lastChange < DOWN_HOLD_MS) return false
        level = (level + steps).coerceAtMost(LEVELS - 1)
        lastChange = now
        return true
    }

    companion object {
        /** Must match Camera::LEVELS in the firmware. */
        const val LEVELS = 5
        const val START_LEVEL = 2
        const val BASELINE_WINDOW_MS = 30_000L
        const val DOWN_MS = 150L
        const val SLOW_MS = 60L
        const val DOWN_HOLD_MS = 1_000L
        const val UP_AFTER_MS = 8_000L

        /** Labels for the firmware ladder in Camera.cpp. */
        val LABELS = listOf("VGA", "VGA low", "CIF", "QVGA 12 fps", "QVGA 6 fps")
    }
}
