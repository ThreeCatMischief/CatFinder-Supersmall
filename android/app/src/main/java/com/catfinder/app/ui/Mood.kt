package com.catfinder.app.ui

import com.catfinder.app.drive.Wheels
import kotlin.math.abs

/** One word for what the robot is doing, shown first in the status chip. */
fun mood(w: Wheels, linkOk: Boolean?): String {
    if (linkOk == false) return "Lost the scent"
    if (w == Wheels.STOP) return "Lurking"
    val forward = (w.left + w.right) / 2
    if (w.left * w.right < 0 && abs(forward) < 20) return "Sniffing around"
    if (forward < 0) return "Backing off"
    val pace = maxOf(abs(w.left), abs(w.right))
    return when {
        pace < 40 -> "Stalking"
        pace < 80 -> "Prowling"
        else -> "Pouncing!"
    }
}
