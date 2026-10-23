package com.catfinder.app.drive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/** Wheel speeds as /drive takes them: -100..100, positive is forward. */
data class Wheels(val left: Int, val right: Int) {
    companion object {
        val STOP = Wheels(0, 0)
    }
}

/**
 * Stick response. [deadZone] and [expo] are 0..1; [maxSpeed] is the wheel
 * speed at full deflection, 0..100.
 */
data class Shaping(val maxSpeed: Int, val deadZone: Float, val expo: Float)

/** Turns stick deflection (-1..1 per axis, up/right positive) into wheel speeds. */
object Mixer {
    /** Dead zone, then rescale to the full range, then cubic expo. */
    fun shape(v: Float, s: Shaping): Float {
        val a = abs(v).coerceAtMost(1f)
        if (a <= s.deadZone) return 0f
        val n = (a - s.deadZone) / (1f - s.deadZone)
        return sign(v) * ((1f - s.expo) * n + s.expo * n * n * n)
    }

    /** One stick: y is throttle, x is turn. Both wheels scale down together when a side saturates. */
    fun arcade(x: Float, y: Float, s: Shaping): Wheels = mix(shape(y, s), shape(x, s), s)

    /**
     * Drive-straight calibration: linear throttle up to full speed, so every calibration
     * point can be reached, and steering at [CAL_TURN] of its range, so a small
     * correction is easy to hold.
     */
    fun calibration(x: Float, y: Float): Wheels {
        val s = Shaping(maxSpeed = 100, deadZone = CAL_DEAD_ZONE, expo = 0f)
        return mix(shape(y, s), shape(x, s) * CAL_TURN, s)
    }

    const val CAL_TURN = 0.25f
    private const val CAL_DEAD_ZONE = 0.05f

    private fun mix(throttle: Float, turn: Float, s: Shaping): Wheels {
        var l = throttle + turn
        var r = throttle - turn
        val m = max(abs(l), abs(r))
        if (m > 1f) {
            l /= m
            r /= m
        }
        return Wheels(speed(l, s), speed(r, s))
    }

    /** Two vertical sticks, one per wheel. */
    fun tank(left: Float, right: Float, s: Shaping): Wheels =
        Wheels(speed(shape(left, s), s), speed(shape(right, s), s))

    private fun speed(v: Float, s: Shaping): Int = (v * s.maxSpeed).roundToInt().coerceIn(-100, 100)
}
