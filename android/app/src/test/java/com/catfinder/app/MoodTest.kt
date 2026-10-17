package com.catfinder.app

import com.catfinder.app.drive.Wheels
import com.catfinder.app.ui.mood
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodTest {
    @Test fun noLink() = assertEquals("Lost the scent", mood(Wheels(80, 80), false))
    @Test fun stopped() = assertEquals("Lurking", mood(Wheels.STOP, true))
    @Test fun spinInPlace() = assertEquals("Sniffing around", mood(Wheels(60, -60), true))
    @Test fun reverse() = assertEquals("Backing off", mood(Wheels(-50, -40), true))
    @Test fun slow() = assertEquals("Stalking", mood(Wheels(30, 25), true))
    @Test fun medium() = assertEquals("Prowling", mood(Wheels(60, 70), true))
    @Test fun fast() = assertEquals("Pouncing!", mood(Wheels(100, 90), true))
}
