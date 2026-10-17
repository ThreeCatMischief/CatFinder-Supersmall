package com.catfinder.app.data

import android.content.Context
import com.catfinder.app.drive.Shaping

enum class StickMode { ARCADE, TANK }

/** Phone-side settings. Device settings live on the ESP (/drive_cfg). */
data class AppConfig(
    /** What the user entered: an IP, a hostname or a full URL; see [EndpointRules]. Blank until set up. */
    val address: String = "",
    val sendRateHz: Int = 10,
    val mode: StickMode = StickMode.ARCADE,
    val maxSpeed: Int = 100,
    val deadZonePct: Int = 8,
    val expoPct: Int = 30,
    /** Stream quality level on the ESP, 0 = best; AUTO lets VideoGovernor pick. */
    val videoLevel: Int = VIDEO_AUTO,
) {
    val shaping: Shaping get() = Shaping(maxSpeed, deadZonePct / 100f, expoPct / 100f)

    companion object {
        const val VIDEO_AUTO = -1
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("catfinder", Context.MODE_PRIVATE)

    fun load(): AppConfig {
        val d = AppConfig()
        return AppConfig(
            address = prefs.getString("address", null) ?: d.address,
            sendRateHz = prefs.getInt("send_rate_hz", d.sendRateHz),
            mode = runCatching { StickMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(d.mode),
            maxSpeed = prefs.getInt("max_speed", d.maxSpeed),
            deadZonePct = prefs.getInt("dead_zone", d.deadZonePct),
            expoPct = prefs.getInt("expo", d.expoPct),
            videoLevel = prefs.getInt("video_level", d.videoLevel),
        )
    }

    fun save(c: AppConfig) = prefs.edit()
        .putString("address", c.address)
        .putInt("send_rate_hz", c.sendRateHz)
        .putString("mode", c.mode.name)
        .putInt("max_speed", c.maxSpeed)
        .putInt("dead_zone", c.deadZonePct)
        .putInt("expo", c.expoPct)
        .putInt("video_level", c.videoLevel)
        .apply()
}
