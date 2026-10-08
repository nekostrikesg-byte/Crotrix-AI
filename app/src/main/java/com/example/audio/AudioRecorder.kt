package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Captures microphone audio as 16kHz, 16-bit, mono PCM chunks for Gemini Live.
 */
class AudioRecorder(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 100ms chunk at 16kHz 16-bit mono = 1600 samples * 2 bytes = 3200 bytes
        private const val CHUNK_SIZE_BYTES = 3200
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) {
            onLog("[MIC] Audio recording already running")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = maxOf(minBufferSize, CHUNK_SIZE_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onLog("[MIC ERROR] AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            onLog("[MIC] Microphone started: 16kHz, 16-bit Mono, buffer: $bufferSize bytes")

            recordingJob = scope.launch {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                while (isActive && isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)
                        
                        // Calculate RMS amplitude for visualizer (0.0 to 1.0)
                        val amplitude = calculateRms(chunk, bytesRead)
                        onAmplitudeChanged(amplitude)

                        // Dispatch audio chunk to Gemini
                        onAudioChunk(chunk)
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read error: $bytesRead")
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            onLog("[MIC ERROR] Failed to start microphone: ${e.message}")
            Log.e(TAG, "Error starting AudioRecord", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording && audioRecord == null) return
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            onLog("[MIC] Microphone stopped")
            onAmplitudeChanged(0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
    }

    fun isRecordingActive(): Boolean = isRecording

    private fun calculateRms(data: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < length - 1) {
            // Little-endian 16-bit PCM
            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
            val normalized = sample.toShort() / 32768.0
            sum += normalized * normalized
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count).toFloat()
        // Boost slightly for visual perception
        return (rms * 3.5f).coerceIn(0f, 1f)
    }
}
