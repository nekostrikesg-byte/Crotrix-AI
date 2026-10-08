package com.example.live

import android.util.Base64
import android.util.Log
import com.example.actions.DeviceActionHandler
import com.example.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages the real-time Gemini Live Multimodal WebSocket session.
 * Handles continuous audio streaming, audio chunk reception & decoding,
 * function/tool calling dispatch with DeviceActionHandler, interruption handling,
 * and comprehensive diagnostic logging.
 */
class GeminiLiveClient(
    private val audioPlayer: AudioPlayer,
    private val deviceActionHandler: DeviceActionHandler,
    private val onStateChanged: (LiveState) -> Unit,
    private val onTranscriptUpdated: (String, Boolean) -> Unit, // text, isUser
    private val onActionTriggered: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    enum class LiveState {
        IDLE, CONNECTING, LISTENING, SPEAKING, ERROR
    }

    companion object {
        private const val TAG = "GeminiLiveClient"
        // Primary Live model supporting real-time native audio bidirectional streaming
        const val MODEL_LIVE = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val MODEL_LIVE_FALLBACK = "models/gemini-3.1-flash-live-preview"
        const val PREFERRED_VOICE = "Aoede" // Lively, young, expressive voice for Arushi

        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        private const val SYSTEM_INSTRUCTION =
            "You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. " +
            "Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. " +
            "Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. " +
            "Automatically understand and respond in the language the user is speaking, including Hindi, English, Hinglish, Marathi, " +
            "Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, and Urdu. " +
            "Keep responses natural, engaging, and concise enough for real-time voice conversation. " +
            "You can execute safe supported device actions through available tools (openWhatsApp, openApp, openUrl, makeCall, callContact). " +
            "Never claim that an action was completed unless the application actually executed it. " +
            "Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive for WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var setupComplete = false
    private var currentModel = MODEL_LIVE
    private val scope = CoroutineScope(Dispatchers.IO)
    private var reconnectJob: Job? = null

    fun connect(apiKey: String, model: String = MODEL_LIVE) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            onLog("[GEMINI ERROR] API key is missing. Please configure GEMINI_API_KEY.")
            onStateChanged(LiveState.ERROR)
            return
        }

        currentModel = model
        disconnect()

        onStateChanged(LiveState.CONNECTING)
        onLog("[GEMINI LIVE] Connecting to Gemini Live WebSocket: $WS_BASE_URL")
        onLog("[GEMINI LIVE] Using model: $currentModel, voice: $PREFERRED_VOICE")

        val url = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                onLog("[GEMINI LIVE] WebSocket connected successfully! Response code: ${response.code}")
                setupComplete = false
                sendInitialSetup()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onLog("[GEMINI LIVE] WebSocket closing: code=$code, reason=$reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onLog("[GEMINI LIVE] WebSocket closed: code=$code, reason=$reason")
                isConnected = false
                onStateChanged(LiveState.IDLE)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                val errorMsg = t.message ?: "Unknown error"
                onLog("[GEMINI LIVE ERROR] WebSocket failure: $errorMsg, response=${response?.code}")
                Log.e(TAG, "WebSocket failure", t)
                onStateChanged(LiveState.ERROR)
            }
        })
    }

    private fun sendInitialSetup() {
        try {
            val setupObj = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", currentModel)
                    
                    // Generation configuration: Native AUDIO response
                    val genConfig = JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", PREFERRED_VOICE)
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)

                    // System Instruction: Arushi personality & multi-language
                    val sysInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", sysInstruction)

                    // Safe Android Tools
                    put("tools", buildToolsDeclarations())
                }
                put("setup", setup)
            }

            val payload = setupObj.toString()
            onLog("[GEMINI LIVE] Sending setup payload (length=${payload.length})")
            webSocket?.send(payload)
        } catch (e: Exception) {
            onLog("[GEMINI LIVE ERROR] Error constructing setup payload: ${e.message}")
            Log.e(TAG, "Setup payload construction error", e)
        }
    }

    private fun buildToolsDeclarations(): JSONArray {
        val toolsArray = JSONArray()
        val funcArray = JSONArray()

        // 1. openWhatsApp
        funcArray.put(JSONObject().apply {
            put("name", "openWhatsApp")
            put("description", "Opens the WhatsApp application on the user's Android device")
        })

        // 2. openApp
        funcArray.put(JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens an installed safe app by common name, e.g., YouTube, Instagram, Camera, Settings, Chrome, Maps")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("appName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the app to launch, e.g., YouTube, Instagram, Maps")
                    })
                })
                put("required", JSONArray().apply { put("appName") })
            })
        })

        // 3. openUrl
        funcArray.put(JSONObject().apply {
            put("name", "openUrl")
            put("description", "Opens a website URL in the device browser")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The website URL starting with https://")
                    })
                })
                put("required", JSONArray().apply { put("url") })
            })
        })

        // 4. makeCall
        funcArray.put(JSONObject().apply {
            put("name", "makeCall")
            put("description", "Dials or calls a specific phone number on the device")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phoneNumber", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The phone number to dial")
                    })
                })
                put("required", JSONArray().apply { put("phoneNumber") })
            })
        })

        // 5. callContact
        funcArray.put(JSONObject().apply {
            put("name", "callContact")
            put("description", "Searches contacts by name and initiates a call or dialer action")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The contact name to look up and call, e.g., Mom, Rahul, Priya")
                    })
                })
                put("required", JSONArray().apply { put("contactName") })
            })
        })

        toolsArray.put(JSONObject().apply {
            put("functionDeclarations", funcArray)
        })
        return toolsArray
    }

    /**
     * Streams real-time 16kHz PCM audio chunk from microphone to Gemini Live session.
     */
    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (!isConnected || !setupComplete || webSocket == null) return
        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("data", base64Data)
                        put("mimeType", "audio/pcm;rate=16000")
                    })
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || !isConnected || !setupComplete) return
        try {
            val payload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("text", cleanText)
                })
            }
            webSocket?.send(payload.toString())
            onLog("[GEMINI TEXT] Prompt sent.")
        } catch (e: Exception) {
            onLog("[GEMINI TEXT ERROR] " + (e.message ?: "unknown error"))
        }
    }

    /**
     * Parses incoming JSON message from Gemini Live WebSocket.
     */
    private fun handleIncomingMessage(text: String) {
        try {
            val root = JSONObject(text)

            // Setup complete acknowledgement
            if (root.has("setupComplete")) {
                setupComplete = true
                onLog("[GEMINI LIVE] Setup completed and ready! Live session active.")
                onStateChanged(LiveState.LISTENING)
                return
            }

            // Server Content (Audio / Text / Interruption)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Interruption check: User spoke while Arushi was speaking
                if (serverContent.optBoolean("interrupted", false)) {
                    onLog("[GEMINI LIVE] Interruption detected! Halting Arushi audio playback.")
                    audioPlayer.interrupt()
                    onStateChanged(LiveState.LISTENING)
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Native Audio chunk
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val base64Audio = inlineData.optString("data", "")

                                if (base64Audio.isNotEmpty()) {
                                    onLog("[GEMINI AUDIO] Audio received, mimeType=$mimeType, b64Len=${base64Audio.length}")
                                    val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    val sampleRate = parseSampleRate(mimeType)
                                    onLog("[GEMINI AUDIO] Decoded PCM: ${pcmBytes.size} bytes at ${sampleRate}Hz")

                                    onStateChanged(LiveState.SPEAKING)
                                    audioPlayer.queueAudioChunk(pcmBytes, sampleRate)
                                }
                            }

                            // Subtitle / Text part
                            if (part.has("text")) {
                                val transcript = part.optString("text", "")
                                if (transcript.isNotBlank()) {
                                    onTranscriptUpdated(transcript, false)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onLog("[GEMINI LIVE] Model turn complete")
                }
            }

            // Tool / Function Call
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "")
                        val funcName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        onLog("[GEMINI TOOL] Tool call received: $funcName (id=$callId)")
                        onActionTriggered(funcName)

                        // Execute action on device
                        val result = deviceActionHandler.executeAction(funcName, args)

                        // Send tool response back to Gemini
                        sendToolResponse(callId, funcName, result)
                    }
                }
            }

        } catch (e: Exception) {
            onLog("[GEMINI PARSE ERROR] Error parsing message: ${e.message}")
            Log.e(TAG, "Error handling incoming message", e)
        }
    }

    private fun sendToolResponse(callId: String, funcName: String, output: JSONObject) {
        try {
            val responseObj = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val functionResponses = JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("name", funcName)
                            val res = JSONObject().apply {
                                put("output", output)
                            }
                            put("response", res)
                        })
                    }
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }
            val payload = responseObj.toString()
            onLog("[GEMINI TOOL RESPONSE] Sending output for $funcName: $payload")
            webSocket?.send(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response", e)
        }
    }

    private fun parseSampleRate(mimeType: String): Int {
        return try {
            val match = Regex("rate=(\\d+)").find(mimeType)
            match?.groupValues?.get(1)?.toIntOrNull() ?: AudioPlayer.DEFAULT_SAMPLE_RATE
        } catch (e: Exception) {
            AudioPlayer.DEFAULT_SAMPLE_RATE
        }
    }

    fun isSessionActive(): Boolean = isConnected

    fun disconnect() {
        if (isConnected || webSocket != null) {
            onLog("[GEMINI LIVE] Disconnecting session...")
            try {
                webSocket?.close(1000, "User disconnected")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing WebSocket: ${e.message}")
            }
            webSocket = null
            isConnected = false
            audioPlayer.interrupt()
            onStateChanged(LiveState.IDLE)
        }
    }
}
