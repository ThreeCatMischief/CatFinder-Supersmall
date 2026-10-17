package com.catfinder.app

import com.catfinder.app.drive.Mixer
import com.catfinder.app.drive.Shaping
import com.catfinder.app.drive.Wheels
import org.junit.Assert.assertEquals
import org.junit.Test

class MixerTest {
    private val linear = Shaping(maxSpeed = 100, deadZone = 0f, expo = 0f)

    @Test fun centreIsStop() = assertEquals(Wheels.STOP, Mixer.arcade(0f, 0f, linear))

    @Test fun fullForward() = assertEquals(Wheels(100, 100), Mixer.arcade(0f, 1f, linear))

    @Test fun fullReverse() = assertEquals(Wheels(-100, -100), Mixer.arcade(0f, -1f, linear))

    @Test fun spinRightInPlace() = assertEquals(Wheels(100, -100), Mixer.arcade(1f, 0f, linear))

    @Test fun forwardRightSaturatesProportionally() =
        assertEquals(Wheels(100, 0), Mixer.arcade(1f, 1f, linear))

    @Test fun maxSpeedScales() =
        assertEquals(Wheels(40, 40), Mixer.arcade(0f, 1f, linear.copy(maxSpeed = 40)))

    @Test fun deadZoneSuppressesSmallInput() {
        val s = linear.copy(deadZone = 0.1f)
        assertEquals(Wheels.STOP, Mixer.arcade(0.05f, 0.09f, s))
        assertEquals(Wheels(100, 100), Mixer.arcade(0f, 1f, s))
    }

    @Test fun deadZoneRescalesSoOutputStartsAtZero() {
        val s = linear.copy(deadZone = 0.2f)
        assertEquals(50, Mixer.arcade(0f, 0.6f, s).left)
    }

    @Test fun expoSoftensCentreKeepsEnds() {
        val s = linear.copy(expo = 1f)
        assertEquals(13, Mixer.arcade(0f, 0.5f, s).left)  // 0.5^3 = 0.125
        assertEquals(100, Mixer.arcade(0f, 1f, s).left)
    }

    @Test fun tankIsPerWheel() = assertEquals(Wheels(100, -50), Mixer.tank(1f, -0.5f, linear))

    @Test fun inputBeyondRangeIsClamped() = assertEquals(Wheels(100, 100), Mixer.tank(3f, 1.5f, linear))
}
