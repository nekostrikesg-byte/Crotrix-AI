package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.actions.DeviceActionHandler
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.live.GeminiLiveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ArushiUiState(
    val state: GeminiLiveClient.LiveState = GeminiLiveClient.LiveState.IDLE,
    val micAmplitude: Float = 0f,
    val speakerAmplitude: Float = 0f,
    val combinedAmplitude: Float = 0f,
    val transcriptUser: String = "",
    val transcriptArushi: String = "",
    val activeActionBadge: String? = null,
    val logs: List<String> = emptyList(),
    val apiKey: String = "",
    val model: String = GeminiLiveClient.MODEL_LIVE,
    val isSpeakerTesting: Boolean = false,
    val statusMessage: String = "Tap to talk with Arushi"
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("crotrix_settings", Context.MODE_PRIVATE)

    private fun loadApiKey(): String = prefs.getString("gemini_api_key", null)?.trim()?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
    private fun loadModel(): String = prefs.getString("gemini_model", null)?.trim()?.takeIf { it.isNotBlank() } ?: GeminiLiveClient.MODEL_LIVE

    private val _uiState = MutableStateFlow(
        ArushiUiState(
            apiKey = loadApiKey(),
            model = loadModel()
        )
    )
    val uiState: StateFlow<ArushiUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val audioPlayer: AudioPlayer
    private val audioRecorder: AudioRecorder
    private val deviceActionHandler: DeviceActionHandler
    private val liveClient: GeminiLiveClient

    init {
        logEvent("[INIT] Initializing Arushi Voice Assistant...")

        audioPlayer = AudioPlayer(
            onPlaybackStateChanged = { isPlaying ->
                _uiState.update { current ->
                    val newState = if (isPlaying) {
                        GeminiLiveClient.LiveState.SPEAKING
                    } else if (current.state == GeminiLiveClient.LiveState.SPEAKING) {
                        GeminiLiveClient.LiveState.LISTENING
                    } else {
                        current.state
                    }
                    current.copy(
                        state = newState,
                        statusMessage = if (isPlaying) "Arushi is speaking..." else "Arushi is listening..."
                    )
                }
            },
            onAmplitudeChanged = { amp ->
                _uiState.update { current ->
                    current.copy(
                        speakerAmplitude = amp,
                        combinedAmplitude = maxOf(current.micAmplitude, amp)
                    )
                }
            },
            onLog = { logEvent(it) }
        )

        deviceActionHandler = DeviceActionHandler(
            context = getApplication(),
            onLog = { logEvent(it) }
        )

        liveClient = GeminiLiveClient(
            audioPlayer = audioPlayer,
            deviceActionHandler = deviceActionHandler,
            onStateChanged = { state ->
                when (state) {
                    GeminiLiveClient.LiveState.LISTENING -> {
                        if (!audioRecorder.isRecordingActive()) {
                            val started = audioRecorder.startRecording()
                            if (!started) {
                                logEvent("[MIC] Could not start microphone.")
                            }
                        }
                    }
                    GeminiLiveClient.LiveState.ERROR -> {
                        audioRecorder.stopRecording()
                    }
                    GeminiLiveClient.LiveState.IDLE -> {
                        audioRecorder.stopRecording()
                    }
                    else -> Unit
                }

                _uiState.update { current ->
                    val msg = when (state) {
                        GeminiLiveClient.LiveState.IDLE -> {
                            if (current.apiKey.isBlank()) "Add your Gemini API key in Settings" else "Tap to talk with Arushi"
                        }
                        GeminiLiveClient.LiveState.CONNECTING -> "Waking Arushi..."
                        GeminiLiveClient.LiveState.LISTENING -> "Arushi is listening..."
                        GeminiLiveClient.LiveState.SPEAKING -> "Arushi is speaking..."
                        GeminiLiveClient.LiveState.ERROR -> "Arushi could not connect. Check the API key or internet."
                    }
                    current.copy(state = state, statusMessage = msg)
                }
            },
            onTranscriptUpdated = { text, isUser ->
                _uiState.update { current ->
                    if (isUser) {
                        current.copy(transcriptUser = text)
                    } else {
                        val currentText = current.transcriptArushi
                        val combined = if (currentText.isBlank()) text else "$currentText $text"
                        current.copy(transcriptArushi = combined)
                    }
                }
            },
            onActionTriggered = { actionName ->
                val badge = when (actionName) {
                    "openWhatsApp" -> "Opening WhatsApp..."
                    "openApp" -> "Launching App..."
                    "openUrl" -> "Opening Browser..."
                    "makeCall" -> "Dialing Phone Number..."
                    "callContact" -> "Searching Contacts & Calling..."
                    else -> "Executing: $actionName"
                }
                _uiState.update { it.copy(activeActionBadge = badge) }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(4000)
                    _uiState.update { it.copy(activeActionBadge = null) }
                }
            },
            onLog = { logEvent(it) }
        )

        audioRecorder = AudioRecorder(
            onAudioChunk = { chunk ->
                liveClient.sendAudioChunk(chunk)
            },
            onAmplitudeChanged = { amp ->
                _uiState.update { current ->
                    current.copy(
                        micAmplitude = amp,
                        combinedAmplitude = maxOf(amp, current.speakerAmplitude)
                    )
                }
            },
            onLog = { logEvent(it) }
        )

        wakeOnLaunchIfConfigured()
    }

    fun toggleSession() {
        val current = _uiState.value
        if (current.state == GeminiLiveClient.LiveState.IDLE || current.state == GeminiLiveClient.LiveState.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    private fun startSession() {
        val key = _uiState.value.apiKey
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            logEvent("[WARN] GEMINI_API_KEY not configured. Please enter a valid API key in Settings.")
            _uiState.update {
                it.copy(
                    state = GeminiLiveClient.LiveState.ERROR,
                    statusMessage = "API Key not set. Open Settings (⚙) to configure."
                )
            }
            return
        }

        logEvent("[SESSION] Starting Live Voice Session...")
        _uiState.update {
            it.copy(
                transcriptUser = "",
                transcriptArushi = "",
                statusMessage = "Connecting..."
            )
        }

        // 1. Connect Gemini Live WebSocket
        liveClient.connect(apiKey = key, model = _uiState.value.model)


    }

    fun stopSession() {
        logEvent("[SESSION] Stopping Voice Session...")
        audioRecorder.stopRecording()
        liveClient.disconnect()
        audioPlayer.interrupt()
        _uiState.update {
            it.copy(
                state = GeminiLiveClient.LiveState.IDLE,
                micAmplitude = 0f,
                speakerAmplitude = 0f,
                combinedAmplitude = 0f,
                statusMessage = "Tap to talk with Arushi"
            )
        }
    }

    fun testSpeaker() {
        _uiState.update { it.copy(isSpeakerTesting = true) }
        logEvent("[SPEAKER TEST] User triggered 440Hz diagnostic tone test")
        audioPlayer.playDiagnosticTone(frequency = 440f, durationMs = 1200)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _uiState.update { it.copy(isSpeakerTesting = false) }
        }
    }

    fun onMicrophonePermissionChanged(granted: Boolean) {
        if (!granted) {
            audioRecorder.stopRecording()
            if (_uiState.value.apiKey.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        state = GeminiLiveClient.LiveState.IDLE,
                        statusMessage = "Microphone permission is required for Arushi."
                    )
                }
            }
            return
        }

        val key = _uiState.value.apiKey.trim()
        if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
            if (_uiState.value.state == GeminiLiveClient.LiveState.IDLE ||
                _uiState.value.state == GeminiLiveClient.LiveState.ERROR
            ) {
                startSession()
            }
        }
    }

    fun updateApiKey(newKey: String) {
        val cleanKey = newKey.trim()
        prefs.edit().putString("gemini_api_key", cleanKey).apply()
        _uiState.update { it.copy(apiKey = cleanKey) }
        logEvent("[CONFIG] API key saved locally.")

        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            stopSession()
        } else {
            stopSession()
            viewModelScope.launch {
                kotlinx.coroutines.delay(200)
                startSession()
            }
        }
    }

    fun updateModel(newModel: String) {
        val cleanModel = newModel.trim().ifBlank { GeminiLiveClient.MODEL_LIVE }
        prefs.edit().putString("gemini_model", cleanModel).apply()
        _uiState.update { it.copy(model = cleanModel) }
        logEvent("[CONFIG] Selected model: $cleanModel")
    }

    fun sendPrompt(prompt: String) {
        val text = prompt.trim()
        if (text.isBlank()) return
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            _uiState.update { it.copy(state = GeminiLiveClient.LiveState.ERROR, statusMessage = "API Key not set. Open Settings.") }
            return
        }
        if (!liveClient.isSessionActive()) liveClient.connect(key, _uiState.value.model)
        _uiState.update { it.copy(transcriptUser = text, statusMessage = "Arushi is thinking...") }
        liveClient.sendText(text)
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    private fun logEvent(msg: String) {
        val timestamp = timeFormat.format(Date())
        val formatted = "[$timestamp] $msg"
        _uiState.update { current ->
            // Keep last 100 log lines
            val updated = (current.logs + formatted).takeLast(100)
            current.copy(logs = updated)
        }
    }

    private fun wakeOnLaunchIfConfigured() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") return

        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            if (_uiState.value.state == GeminiLiveClient.LiveState.IDLE) {
                startSession()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        liveClient.disconnect()
        audioPlayer.release()
    }
}
