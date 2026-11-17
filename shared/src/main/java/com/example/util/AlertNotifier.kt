package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlertNotifier {

    fun triggerTurnAlert(
        context: Context,
        muteSound: Boolean,
        muteVibration: Boolean,
        isUrgent: Boolean
    ) {
        if (!muteSound) {
            playTone(isUrgent)
        }
        if (!muteVibration) {
            vibrate(context, isUrgent)
        }
    }

    private fun playTone(isUrgent: Boolean) {
        runCatching {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            val toneType = if (isUrgent) {
                ToneGenerator.TONE_PROP_BEEP2
            } else {
                ToneGenerator.TONE_PROP_BEEP
            }
            toneGen.startTone(toneType, 280)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, isUrgent: Boolean) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = if (isUrgent) longArrayOf(0, 180, 100, 240) else longArrayOf(0, 160)
                val amplitudes = if (isUrgent) intArrayOf(0, 220, 0, 255) else intArrayOf(0, 180)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(if (isUrgent) 350L else 180L)
            }
        }
    }
}
