package com.catfinder.app.data

import org.json.JSONObject

/** The fields of /drive_cfg, as strings keyed by their POST names. */
object DriveConfigForm {
    val FLAGS = listOf(
        "swap" to "Swap left and right",
        "inv_a" to "Reverse servo A",
        "inv_b" to "Reverse servo B",
    )
    // Set on the robot's web page; read here for the drive-straight calculation.
    val NUMBERS = listOf(
        "stop_a" to "Stop A (µs)",
        "stop_b" to "Stop B (µs)",
        "span" to "Span (µs)",
        "timeout" to "Timeout (ms)",
    )
    // Same order as the "bal" array: calibration points at 25/50/75/100 % speed.
    val BALANCE = listOf(
        "bal0" to "Balance 25 %",
        "bal1" to "Balance 50 %",
        "bal2" to "Balance 75 %",
        "bal3" to "Balance 100 %",
    )

    fun fromJson(j: JSONObject): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for ((key, _) in FLAGS) out[key] = if (j.optInt(key) != 0) "1" else "0"
        for ((key, _) in NUMBERS) out[key] = j.optInt(key).toString()
        val bal = j.optJSONArray("bal")
        BALANCE.forEachIndexed { i, (key, _) -> out[key] = (bal?.optInt(i) ?: 0).toString() }
        return out
    }
}
