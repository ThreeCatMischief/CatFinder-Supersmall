package com.catfinder.app.data

import android.content.Context
import com.catfinder.app.drive.Shaping

enum class StickMode { ARCADE, TANK }

/** Phone-side settings. Device settings live on the robot (/drive_cfg). */
data class AppConfig(
    /** IP address, host name or URL of the robot; blank until set up. */
    val address: String = "",
    val mode: StickMode = StickMode.ARCADE,
    val maxSpeed: Int = 100,
    /** Stream quality level on the robot, 0 = best; AUTO lets VideoGovernor pick. */
    val videoLevel: Int = VIDEO_AUTO,
) {
    val shaping: Shaping get() = Shaping(maxSpeed, DEAD_ZONE, EXPO)

    companion object {
        const val VIDEO_AUTO = -1
        /** /drive requests per second while driving; the robot stops after 500 ms without one. */
        const val SEND_RATE_HZ = 10
        private const val DEAD_ZONE = 0.08f
        private const val EXPO = 0.3f
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("catfinder", Context.MODE_PRIVATE)

    fun load(): AppConfig {
        val d = AppConfig()
        return AppConfig(
            address = prefs.getString("address", null) ?: d.address,
            mode = runCatching { StickMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(d.mode),
            maxSpeed = prefs.getInt("max_speed", d.maxSpeed),
            videoLevel = prefs.getInt("video_level", d.videoLevel),
        )
    }

    fun save(c: AppConfig) = prefs.edit()
        .putString("address", c.address)
        .putString("mode", c.mode.name)
        .putInt("max_speed", c.maxSpeed)
        .putInt("video_level", c.videoLevel)
        .apply()
}
