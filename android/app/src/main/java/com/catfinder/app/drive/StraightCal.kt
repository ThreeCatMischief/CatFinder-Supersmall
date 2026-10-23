package com.catfinder.app.drive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Straight-line calibration from a held stick position.
 *
 * The firmware evens out the wheels with a balance in µs per calibration point
 * (25/50/75/100 % speed, interpolated between, looked up at the faster wheel's speed;
 * see balanceAt and wheelOffsetUs in Drive.cpp). A positive balance shortens the left
 * wheel's pulse offset, a negative one the right's.
 *
 * While the user holds the command (l, r) and the tank drives straight, the pulse
 * offsets differ by (|l| - |r|) * span / 100 - applied, where applied is the balance
 * the firmware used for that command. The balance that makes (s, s) drive straight
 * produces the same difference: balance = applied - (|l| - |r|) * span / 100.
 */
object StraightCal {
    val POINTS = listOf(25, 50, 75, 100)
    /** Slower than this is too slow to judge; matches the stick's useful range. */
    const val MIN_SPEED = 15
    /** How long the stick has to stay still before the hold is saved. */
    const val HOLD_MS = 5_000L
    /** Firmware limit (Drive.cpp BALANCE_MAX_US). */
    const val BALANCE_MAX_US = 200

    /** The balance the firmware applies at [speed]; integer maths as in Drive.cpp. */
    fun balanceAt(bal: List<Int>, speed: Int): Int {
        val step = 100 / POINTS.size
        val s = abs(speed)
        val i = s / step
        if (i >= POINTS.size) return bal[POINTS.size - 1]
        val from = if (i > 0) bal[i - 1] else 0
        return from + (bal[i] - from) * (s - i * step) / step
    }

    /** Index of the calibration point nearest to [speed], or null below [MIN_SPEED]. */
    fun pointFor(speed: Int): Int? {
        val s = abs(speed)
        if (s < MIN_SPEED) return null
        return POINTS.indices.minBy { abs(POINTS[it] - s) }
    }

    /** Both wheels turn the same way, fast enough to judge whether the tank runs straight. */
    fun holdable(w: Wheels): Boolean = w.left * w.right > 0 && max(abs(w.left), abs(w.right)) >= MIN_SPEED

    /**
     * Balance for the point nearest to the held speed, from the average command
     * ([left], [right]) that drove straight, the balance stored now and the span.
     */
    fun balanceFor(left: Double, right: Double, bal: List<Int>, spanUs: Int): Int {
        val speed = max(abs(left), abs(right)).roundToInt()
        val applied = balanceAt(bal, speed)
        val diffUs = (abs(left) - abs(right)) * spanUs / 100.0
        return (applied - diffUs).roundToInt().coerceIn(-BALANCE_MAX_US, BALANCE_MAX_US)
    }
}

/**
 * Measures how long the same wheel command has been held. Each wheel may wander by
 * [tolerance] from where the hold started; more starts a new hold. Commands that are
 * not [StraightCal.holdable] end the hold.
 */
class HoldDetector(private val holdMs: Long = StraightCal.HOLD_MS, private val tolerance: Int = 3) {
    private var anchor: Wheels? = null
    private var since = 0L
    private var sumLeft = 0.0
    private var sumRight = 0.0
    private var samples = 0

    /** Progress of the current hold, 0..1; 1 means held for [holdMs]. */
    fun update(now: Long, w: Wheels): Float {
        if (!StraightCal.holdable(w)) {
            reset()
            return 0f
        }
        val a = anchor
        if (a == null || abs(w.left - a.left) > tolerance || abs(w.right - a.right) > tolerance) {
            anchor = w
            since = now
            sumLeft = 0.0
            sumRight = 0.0
            samples = 0
        }
        sumLeft += w.left
        sumRight += w.right
        samples++
        return ((now - since).toFloat() / holdMs).coerceIn(0f, 1f)
    }

    /** Average command over the current hold; null when nothing is held. */
    fun average(): Pair<Double, Double>? =
        if (anchor == null || samples == 0) null else (sumLeft / samples) to (sumRight / samples)

    fun reset() {
        anchor = null
        samples = 0
    }
}
