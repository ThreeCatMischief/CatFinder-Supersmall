package com.catfinder.app

import com.catfinder.app.drive.HoldDetector
import com.catfinder.app.drive.StraightCal
import com.catfinder.app.drive.Wheels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StraightCalTest {
    private val zero = listOf(0, 0, 0, 0)

    @Test fun balanceAtInterpolatesLikeTheFirmware() {
        val bal = listOf(8, 16, -4, 20)
        assertEquals(0, StraightCal.balanceAt(bal, 0))
        assertEquals(3, StraightCal.balanceAt(bal, 12))   // 8 * 12 / 25, truncated
        assertEquals(8, StraightCal.balanceAt(bal, 25))
        assertEquals(11, StraightCal.balanceAt(bal, 37))  // 8 + 8 * 12 / 25 in integer maths
        assertEquals(16, StraightCal.balanceAt(bal, 50))
        assertEquals(20, StraightCal.balanceAt(bal, 100))
        assertEquals(20, StraightCal.balanceAt(bal, -100))
    }

    @Test fun pointForPicksTheNearest() {
        assertNull(StraightCal.pointFor(10))
        assertEquals(0, StraightCal.pointFor(15))
        assertEquals(0, StraightCal.pointFor(37))
        assertEquals(1, StraightCal.pointFor(38))
        assertEquals(1, StraightCal.pointFor(-50))
        assertEquals(3, StraightCal.pointFor(100))
    }

    @Test fun holdableNeedsBothWheelsTheSameWayAndSomeSpeed() {
        assertTrue(StraightCal.holdable(Wheels(50, 46)))
        assertTrue(StraightCal.holdable(Wheels(-50, -46)))
        assertFalse(StraightCal.holdable(Wheels(50, -46)))
        assertFalse(StraightCal.holdable(Wheels(10, 12)))
        assertFalse(StraightCal.holdable(Wheels(30, 0)))
    }

    // Left held faster: the left wheel is the slow one, so the right wheel must be slowed.
    @Test fun steeringRightToGoStraightSlowsTheRightWheel() =
        assertEquals(-20, StraightCal.balanceFor(52.0, 48.0, zero, spanUs = 500))

    @Test fun steeringLeftToGoStraightSlowsTheLeftWheel() =
        assertEquals(20, StraightCal.balanceFor(48.0, 52.0, zero, spanUs = 500))

    @Test fun existingBalanceIsTakenIntoAccount() {
        // Balance 10 already slows the left wheel; holding even means it is right.
        assertEquals(10, StraightCal.balanceFor(50.0, 50.0, listOf(5, 10, 10, 10), spanUs = 500))
        // Still had to steer left by 2 %: slow the left wheel by another 10 µs.
        assertEquals(20, StraightCal.balanceFor(49.0, 51.0, listOf(5, 10, 10, 10), spanUs = 500))
    }

    @Test fun reverseUsesMagnitudes() =
        assertEquals(-20, StraightCal.balanceFor(-52.0, -48.0, zero, spanUs = 500))

    @Test fun balanceIsClampedToTheFirmwareLimit() =
        assertEquals(-200, StraightCal.balanceFor(100.0, 40.0, zero, spanUs = 500))

    @Test fun holdCompletesAfterFiveSeconds() {
        val d = HoldDetector()
        assertEquals(0f, d.update(0, Wheels(50, 48)))
        assertEquals(0.5f, d.update(2_500, Wheels(51, 47)))
        assertEquals(1f, d.update(5_000, Wheels(49, 48)))
        val (l, r) = d.average()!!
        assertEquals(50.0, l, 1e-9)
        assertEquals(47.666, r, 1e-3)
    }

    @Test fun movingTheStickRestartsTheHold() {
        val d = HoldDetector()
        d.update(0, Wheels(50, 48))
        d.update(3_000, Wheels(50, 48))
        assertEquals(0f, d.update(3_100, Wheels(60, 56)))
        assertEquals(0.5f, d.update(5_600, Wheels(60, 56)))
    }

    @Test fun stoppingEndsTheHold() {
        val d = HoldDetector()
        d.update(0, Wheels(50, 48))
        assertEquals(0f, d.update(1_000, Wheels.STOP))
        assertNull(d.average())
        assertEquals(0f, d.update(1_100, Wheels(50, 48)))
    }
}
