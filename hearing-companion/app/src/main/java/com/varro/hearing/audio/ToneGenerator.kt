package com.varro.hearing.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates tones, chimes and masking noise through the phone's media output.
 * If the hearing aids are the active audio device, the sound plays in them.
 * This is the audio path — NOT a GATT command to the aid.
 */
class ToneGenerator(private val scope: CoroutineScope) {

    private val sampleRate = 44100

    fun playTone(freqHz: Double, durationMs: Int, volume: Float = 0.3f) {
        scope.launch(Dispatchers.Default) { renderAndPlay(buildTone(freqHz, durationMs, volume)) }
    }

    /** Pleasant 3-note arpeggio (C5–E5–G5). */
    fun playChime(volume: Float = 0.3f) {
        scope.launch(Dispatchers.Default) {
            val notes = listOf(523.25, 659.25, 783.99)
            val out = ShortArray(0).toMutableList()
            notes.forEach { out.addAll(buildTone(it, 220, volume).toList()); out.addAll(silence(40).toList()) }
            renderAndPlay(out.toShortArray())
        }
    }

    /** Rising sweep across audiometric frequencies — quick "can I hear across the range?" check. */
    fun playSweep(volume: Float = 0.3f) {
        scope.launch(Dispatchers.Default) {
            val freqs = listOf(250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)
            val out = mutableListOf<Short>()
            freqs.forEach { out.addAll(buildTone(it, 180, volume).toList()); out.addAll(silence(25).toList()) }
            renderAndPlay(out.toShortArray())
        }
    }

    /** Continuous masking noise for tinnitus comfort; call [stopNoise] to end. */
    @Volatile private var noiseTrack: AudioTrack? = null
    fun startNoise(volume: Float = 0.15f, notchHz: Double? = null) {
        stopNoise()
        scope.launch(Dispatchers.Default) {
            val track = newTrack(streaming = true)
            noiseTrack = track
            track.play()
            val buf = ShortArray(sampleRate / 10)
            var prev = 0.0
            while (noiseTrack === track) {
                for (i in buf.indices) {
                    // simple pink-ish noise via one-pole low-pass on white noise
                    val white = (Math.random() * 2 - 1)
                    prev = 0.98 * prev + 0.02 * white
                    var v = prev
                    notchHz?.let { v *= (1 - 0.6 * sin(2 * PI * it * i / sampleRate)) } // crude notch tint
                    buf[i] = (v * volume * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(buf, 0, buf.size)
            }
            track.stop(); track.release()
        }
    }
    fun stopNoise() { noiseTrack = null }

    // ---- helpers ----------------------------------------------------------
    private fun buildTone(freqHz: Double, durationMs: Int, volume: Float): ShortArray {
        val n = sampleRate * durationMs / 1000
        val out = ShortArray(n)
        val fade = (sampleRate * 0.01).toInt().coerceAtLeast(1) // 10ms fade to avoid clicks
        for (i in 0 until n) {
            var amp = volume
            if (i < fade) amp *= i.toFloat() / fade
            if (i > n - fade) amp *= (n - i).toFloat() / fade
            out[i] = (sin(2 * PI * freqHz * i / sampleRate) * amp * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
    private fun silence(ms: Int) = ShortArray(sampleRate * ms / 1000)

    private fun renderAndPlay(samples: ShortArray) {
        val track = newTrack(streaming = false, sizeBytes = samples.size * 2)
        track.write(samples, 0, samples.size)
        track.play()
        // release after playback completes
        Thread {
            Thread.sleep((samples.size * 1000L / sampleRate) + 120)
            track.stop(); track.release()
        }.start()
    }

    private fun newTrack(streaming: Boolean, sizeBytes: Int = sampleRate * 2): AudioTrack {
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, sizeBytes))
            .setTransferMode(if (streaming) AudioTrack.MODE_STREAM else AudioTrack.MODE_STATIC)
            .build()
    }
}
