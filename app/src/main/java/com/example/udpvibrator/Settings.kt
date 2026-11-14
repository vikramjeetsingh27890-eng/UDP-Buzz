package com.example.udpvibrator

import android.content.Context

data class VibeSettings(
    val port: Int = 5005,
    val allowedIp: String = "",   // empty = accept from any sender
    val durationMs: Int = 300,
    val strength: Int = 255,      // 1..255 (only on devices with amplitude control)
    val repeats: Int = 1,         // pulses per signal
    val gapMs: Int = 150,         // pause between pulses
)

class SettingsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = VibeSettings(
        port = prefs.getInt("port", 5005),
        allowedIp = prefs.getString("allowedIp", "") ?: "",
        durationMs = prefs.getInt("durationMs", 300),
        strength = prefs.getInt("strength", 255),
        repeats = prefs.getInt("repeats", 1),
        gapMs = prefs.getInt("gapMs", 150),
    )

    fun save(s: VibeSettings) {
        prefs.edit()
            .putInt("port", s.port)
            .putString("allowedIp", s.allowedIp)
            .putInt("durationMs", s.durationMs)
            .putInt("strength", s.strength)
            .putInt("repeats", s.repeats)
            .putInt("gapMs", s.gapMs)
            .apply()
    }
}
