package com.example.udpvibrator

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    @Suppress("DEPRECATION")
    private fun vibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun hasAmplitudeControl(ctx: Context): Boolean = vibrator(ctx).hasAmplitudeControl()

    @Suppress("DEPRECATION")
    fun vibrate(ctx: Context, s: VibeSettings) {
        val v = vibrator(ctx)
        if (!v.hasVibrator()) return

        val n = s.repeats.coerceIn(1, 10)
        val timings = LongArray(n * 2)
        val amps = IntArray(n * 2)
        for (i in 0 until n) {
            timings[i * 2] = if (i == 0) 0L else s.gapMs.toLong()   // off
            timings[i * 2 + 1] = s.durationMs.toLong()              // on
            amps[i * 2] = 0
            amps[i * 2 + 1] = s.strength.coerceIn(1, 255)
        }

        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amps, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }

        // USAGE_ALARM keeps the vibration working from the background / in silent mode.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        v.vibrate(effect, attrs)
    }
}
