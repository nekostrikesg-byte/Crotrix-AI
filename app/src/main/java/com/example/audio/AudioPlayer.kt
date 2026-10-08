package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles real-time sequential PCM audio playback through Android AudioTrack.
 * Supports dynamic sample rates (16kHz / 24kHz), sequential queueing,
 * live output amplitude visualization, interruption clearing, and 440Hz diagnostic tone.
 */
class AudioPlayer(
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000 // Standard Gemini Live output rate
    }

    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private var audioTrack: AudioTrack? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var playbackJob: Job? = null
    private var isPlaying = false

    init {
        startPlaybackWorker()
    }

    private fun ensureAudioTrack(sampleRate: Int) {
        if (audioTrack != null && currentSampleRate == sampleRate &&
            audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            return
        }

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup old AudioTrack: ${e.message}")
        }

        currentSampleRate = sampleRate
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 8192)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(audioFormat)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack?.play()
            onLog("[AUDIO TRACK] Initialized at ${sampleRate}Hz Mono PCM16, state=PLAYING")
        } else {
            onLog("[AUDIO TRACK ERROR] AudioTrack failed initialization at ${sampleRate}Hz")
        }
    }

    private fun startPlaybackWorker() {
        playbackJob = scope.launch {
            while (isActive) {
                try {
                    val chunk = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        if (!isPlaying) {
                            isPlaying = true
                            onPlaybackStateChanged(true)
                            onLog("[AUDIO PLAYBACK] Started playing audio stream")
                        }

                        // Ensure AudioTrack is initialized and running
                        ensureAudioTrack(currentSampleRate)
                        audioTrack?.let { track ->
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                track.play()
                            }
                            
                            // Measure amplitude of outgoing chunk for orb pulse
                            val amplitude = calculateRms(chunk, chunk.size)
                            onAmplitudeChanged(amplitude)

                            // Write PCM samples to speaker
                            track.write(chunk, 0, chunk.size)
                        }
                    } else {
                        // Queue empty
                        if (isPlaying && queue.isEmpty()) {
                            isPlaying = false
                            onPlaybackStateChanged(false)
                            onAmplitudeChanged(0f)
                            onLog("[AUDIO PLAYBACK] Finished playing current response stream")
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    onLog("[AUDIO PLAYBACK ERROR] Exception during playback: ${e.message}")
                    Log.e(TAG, "Playback loop error", e)
                }
            }
        }
    }

    /**
     * Enqueue a PCM audio chunk for sequential playback.
     */
    fun queueAudioChunk(data: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (data.isEmpty()) return
        if (sampleRate != currentSampleRate) {
            onLog("[AUDIO PIPELINE] Sample rate updated: ${currentSampleRate}Hz -> ${sampleRate}Hz")
            currentSampleRate = sampleRate
        }
        queue.offer(data)
        onLog("[AUDIO CHUNK] Queued ${data.size} bytes (${data.size / 2} samples, ~${(data.size / 2.0 / currentSampleRate * 1000).toInt()}ms)")
    }

    /**
     * Immediately cancels currently playing audio and clears all queued chunks.
     * Essential for handling user interruptions!
     */
    fun interrupt() {
        queue.clear()
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
            }
            onLog("[AUDIO INTERRUPT] Audio playback interrupted and queue cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting playback", e)
        }
        isPlaying = false
        onPlaybackStateChanged(false)
        onAmplitudeChanged(0f)
    }

    /**
     * Speaker Diagnostic Test (Requirement 15):
     * Generates a 440Hz sine wave tone and plays it through the SAME AudioTrack output pipeline
     * to verify speaker hardware, volume, and routing.
     */
    fun playDiagnosticTone(frequency: Float = 440f, durationMs: Int = 1200) {
        scope.launch {
            onLog("[SPEAKER TEST] Starting 440Hz diagnostic sine tone test (${durationMs}ms)...")
            val sampleRate = 24000
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmData = ByteArray(totalSamples * 2)

            for (i in 0 until totalSamples) {
                // Sine wave formula: A * sin(2 * PI * f * t)
                val angle = 2.0 * PI * frequency * i / sampleRate
                val sampleValue = (sin(angle) * 30000).toInt().toShort()

                // Little endian 16-bit PCM
                pcmData[i * 2] = (sampleValue.toInt() and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
            }

            // Queue tone
            ensureAudioTrack(sampleRate)
            queueAudioChunk(pcmData, sampleRate)
            onLog("[SPEAKER TEST] Diagnostic tone dispatched to speaker pipeline successfully!")
        }
    }

    fun release() {
        interrupt()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    private fun calculateRms(data: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < length - 1) {
            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
            val normalized = sample.toShort() / 32768.0
            sum += normalized * normalized
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count).toFloat()
        return (rms * 3.0f).coerceIn(0f, 1f)
    }
}
