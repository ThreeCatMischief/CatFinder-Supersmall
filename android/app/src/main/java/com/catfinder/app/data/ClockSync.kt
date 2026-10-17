package com.catfinder.app.data

/**
 * Estimates ESP uptime from phone time (elapsedRealtime). Each answered request
 * that carries the ESP's millis() gives offset = espMs - midpoint of the request;
 * its error is at most rtt / 2, so the sample with the lowest RTT in the last
 * [WINDOW_MS] is used.
 */
class ClockSync {
    private val samples = ArrayDeque<Triple<Long, Long, Long>>()  // phone time, rtt, offset

    @Synchronized
    fun onSample(sentAt: Long, rttMs: Long, espMs: Long) {
        if (rttMs > MAX_RTT_MS) return
        val now = sentAt + rttMs
        // An ESP reboot resets millis(); the old samples no longer apply.
        val offset = espMs - (sentAt + rttMs / 2)
        if (samples.isNotEmpty() && kotlin.math.abs(samples.last().third - offset) > REBOOT_JUMP_MS) samples.clear()
        samples.addLast(Triple(now, rttMs, offset))
        while (samples.first().first < now - WINDOW_MS) samples.removeFirst()
    }

    /** espMs - phoneMs, or null without samples. */
    @Synchronized
    fun offset(): Long? = samples.minByOrNull { it.second }?.third

    /** Error bound of [offset] in ms. */
    @Synchronized
    fun uncertainty(): Long? = samples.minByOrNull { it.second }?.second?.div(2)

    companion object {
        const val WINDOW_MS = 60_000L
        const val REBOOT_JUMP_MS = 10_000L
        const val MAX_RTT_MS = 1_000L
    }
}
