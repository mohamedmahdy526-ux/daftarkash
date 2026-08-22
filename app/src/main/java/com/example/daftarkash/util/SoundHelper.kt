package com.example.daftarkash.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundHelper {

    private var soundEnabled: Boolean = true
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = soundEnabled

    /**
     * Cash register chime (Ka-ching) for recording payment / cash sale
     */
    fun playCashChime() {
        if (!soundEnabled) return
        scope.launch {
            try {
                // Synthesize a crisp dual-bell register chime (frequencies: 1318.5 Hz [E6] then 1760 Hz [A6] + 2093 Hz [C7])
                val sampleRate = 44100
                val durationSec = 0.35
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Fade out envelope
                    val envelope = (1.0 - (t / durationSec)).coerceIn(0.0, 1.0)
                    
                    val note1 = if (t < 0.15) sin(2.0 * Math.PI * 1318.5 * t) else 0.0
                    val note2 = if (t >= 0.08) sin(2.0 * Math.PI * 1760.0 * (t - 0.08)) * 0.7 else 0.0
                    val note3 = if (t >= 0.12) sin(2.0 * Math.PI * 2093.0 * (t - 0.12)) * 0.8 else 0.0
                    val sample = ((note1 + note2 + note3) * envelope * 0.5 * Short.MAX_VALUE).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                // Fallback to ToneGenerator
                playToneFallback(ToneGenerator.TONE_PROP_BEEP2, 150)
            }
        }
    }

    /**
     * Barcode scanner laser beep for POS product scan & cart additions
     */
    fun playScannerBeep() {
        if (!soundEnabled) return
        scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.08
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = (1.0 - (t / durationSec)).coerceIn(0.0, 1.0)
                    val note = sin(2.0 * Math.PI * 2400.0 * t)
                    buffer[i] = (note * envelope * 0.4 * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                playToneFallback(ToneGenerator.TONE_PROP_BEEP, 80)
            }
        }
    }

    /**
     * Debt recorded sound (a lower authoritative double tone)
     */
    fun playDebtRecorded() {
        if (!soundEnabled) return
        scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.22
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = (1.0 - (t / durationSec)).coerceIn(0.0, 1.0)
                    val note = if (t < 0.1) sin(2.0 * Math.PI * 523.25 * t) else sin(2.0 * Math.PI * 659.25 * t)
                    buffer[i] = (note * envelope * 0.4 * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                playToneFallback(ToneGenerator.TONE_PROP_ACK, 120)
            }
        }
    }

    /**
     * Celebration victory chime when customer completely settles their debt!
     */
    fun playCelebrationFanfare() {
        if (!soundEnabled) return
        scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 0.6
                val numSamples = (durationSec * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                // C5 -> E5 -> G5 -> C6 happy progression
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = when {
                        t < 0.12 -> 523.25 // C5
                        t < 0.24 -> 659.25 // E5
                        t < 0.36 -> 783.99 // G5
                        else -> 1046.50    // C6
                    }
                    val segmentT = t % 0.12
                    val noteEnvelope = (1.0 - (segmentT / 0.15)).coerceIn(0.0, 1.0)
                    val note = sin(2.0 * Math.PI * freq * t)
                    buffer[i] = (note * noteEnvelope * 0.5 * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                playToneFallback(ToneGenerator.TONE_PROP_PROMPT, 300)
            }
        }
    }

    /**
     * Delete / Trash sound
     */
    fun playDeleteSound() {
        if (!soundEnabled) return
        scope.launch {
            try {
                playToneFallback(ToneGenerator.TONE_PROP_NACK, 100)
            } catch (_: Exception) {}
        }
    }

    /**
     * Success sound
     */
    fun playSuccess() {
        if (!soundEnabled) return
        scope.launch {
            try {
                playToneFallback(ToneGenerator.TONE_PROP_ACK, 100)
            } catch (_: Exception) {}
        }
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        // Release resources after playback
        scope.launch {
            kotlinx.coroutines.delay(1000)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    private fun playToneFallback(toneType: Int, durationMs: Int) {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGenerator.startTone(toneType, durationMs)
            scope.launch {
                kotlinx.coroutines.delay(durationMs.toLong() + 50)
                try {
                    toneGenerator.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
