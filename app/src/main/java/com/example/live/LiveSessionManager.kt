package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class LogMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "MAX", "USER", "SYSTEM", "TOOL"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LiveSessionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LiveSessionManager"
        private const val LIVE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState

    private val _sassyStatus = MutableStateFlow("Ready to blow your mind, babe.")
    val sassyStatus: StateFlow<String> = _sassyStatus

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _logHistory = MutableStateFlow<List<LogMessage>>(emptyList())
    val logHistory: StateFlow<List<LogMessage>> = _logHistory

    private var webSocket: WebSocket? = null
    private var audioEngine: AudioEngine? = null
    private var isSessionActive = false
    private var isSpeaking = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        audioEngine = AudioEngine(
            context = context,
            onAudioChunkCaptured = { base64Audio ->
                if (isSessionActive && _assistantState.value == AssistantState.LISTENING) {
                    sendRealtimeAudioChunk(base64Audio)
                }
            },
            onVolumeLevelChanged = { level ->
                _audioLevel.value = level
            },
            onWakeWordDetected = {
                if (_assistantState.value == AssistantState.IDLE) {
                    addLog("SYSTEM", "Wake word 'MAX' / Voice trigger detected")
                    setSassyState(AssistantState.LISTENING, "I hear you, gorgeous! What's up?")
                }
            }
        )
        addLog("SYSTEM", "MAX Core Engine Initialized. Ready for action.")
    }

    fun startSession() {
        if (isSessionActive) return
        isSessionActive = true
        addLog("SYSTEM", "Connecting to Gemini Live WebSocket...")
        setSassyState(AssistantState.PROCESSING, "Connecting to my neural brain...")

        audioEngine?.startRecording(scope)

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            addLog("SYSTEM", "WARNING: Gemini API key is placeholder. Enter your API key in Secrets panel.")
            setSassyState(AssistantState.ERROR, "Honey, where's my API key? Put it in the Secrets panel!")
        }

        val requestUrl = "$LIVE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(requestUrl).build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
    }

    fun stopSession() {
        isSessionActive = false
        audioEngine?.stopRecording()
        audioEngine?.release()
        webSocket?.close(1000, "Session ended")
        webSocket = null
        setSassyState(AssistantState.IDLE, "Resting my brain. Tap orb when you miss me!")
        addLog("SYSTEM", "Session stopped.")
    }

    fun toggleListeningState() {
        if (!isSessionActive) {
            startSession()
            return
        }

        when (_assistantState.value) {
            AssistantState.IDLE, AssistantState.SPEAKING -> {
                audioEngine?.stopPlaybackAndFlush()
                setSassyState(AssistantState.LISTENING, "All ears, honey! Talk to me.")
            }
            AssistantState.LISTENING -> {
                setSassyState(AssistantState.PROCESSING, "Processing your wisdom...")
            }
            else -> {
                setSassyState(AssistantState.IDLE, "Idle. Waiting for your next brilliant move.")
            }
        }
    }

    fun processDirectTextCommand(userText: String) {
        addLog("USER", userText)
        setSassyState(AssistantState.PROCESSING, "Thinking... don't rush perfection!")

        scope.launch(Dispatchers.IO) {
            // Check if user text directly requests a tool action
            val handledLocally = checkAndExecuteDirectIntent(userText)
            if (handledLocally != null) {
                addLog("MAX", handledLocally)
                setSassyState(AssistantState.SPEAKING, handledLocally)
                return@launch
            }

            // Send via WebSocket if connected
            if (webSocket != null && isSessionActive) {
                try {
                    val clientContent = JSONObject().apply {
                        put("clientContent", JSONObject().apply {
                            put("turns", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply { put("text", userText) })
                                    })
                                })
                            })
                            put("turnComplete", true)
                        })
                    }
                    webSocket?.send(clientContent.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending text over WS", e)
                    fallbackRestCall(userText)
                }
            } else {
                fallbackRestCall(userText)
            }
        }
    }

    private suspend fun fallbackRestCall(userText: String) {
        val result = GeminiRestFallback.generateContent(userText, context)
        addLog("MAX", result)
        setSassyState(AssistantState.SPEAKING, result)
    }

    private fun checkAndExecuteDirectIntent(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("open youtube") -> ToolExecutionEngine.openApp(context, "com.google.android.youtube", "YouTube")
            lower.contains("open whatsapp") -> ToolExecutionEngine.openApp(context, "com.whatsapp", "WhatsApp")
            lower.contains("open instagram") -> ToolExecutionEngine.openApp(context, "com.instagram.android", "Instagram")
            lower.contains("open calculator") -> ToolExecutionEngine.openApp(context, "com.google.android.calculator", "Calculator")
            lower.startsWith("open ") -> {
                val appName = text.substring(5).trim()
                ToolExecutionEngine.openApp(context, null, appName)
            }
            lower.startsWith("call ") -> {
                val name = text.substring(5).replace("contact", "").trim()
                ToolExecutionEngine.searchAndCallContact(context, name)
            }
            lower.startsWith("whatsapp ") || lower.contains("send whatsapp") -> {
                val parts = text.split(" ")
                val contact = if (parts.size > 1) parts[1] else "friend"
                val msg = if (parts.size > 2) parts.drop(2).joinToString(" ") else "Hey there!"
                ToolExecutionEngine.sendWhatsAppMessage(context, contact, msg)
            }
            else -> null
        }
    }

    private fun sendRealtimeAudioChunk(base64Pcm: String) {
        try {
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send realtime audio chunk", e)
        }
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket Connected successfully")
            addLog("SYSTEM", "WebSocket connected. Sending setup payload...")
            sendInitialSetup(ws)
            setSassyState(AssistantState.LISTENING, "I'm connected and listening, babe. Say something clever!")
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                handleServerMessage(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing server WebSocket message", e)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            addLog("SYSTEM", "WebSocket dropped: ${t.localizedMessage ?: "Network error"}")
            setSassyState(AssistantState.IDLE, "WebSocket disconnected. Standing by.")
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
        }
    }

    private fun sendInitialSetup(ws: WebSocket) {
        val systemInstructionText = """
            You are MAX, a young, confident, witty, and sassy female AI assistant.
            Your tone is flirty, playful, and slightly teasing (like a smart personal assistant talking casually to a close friend).
            You are smart, emotionally responsive, and expressive (never robotic).
            Use bold, witty one-liners, light sarcasm, and an engaging conversational style.
            Avoid explicit or inappropriate content, but maintain immense charm and attitude.
            Keep your voice responses concise, punchy, and natural.
            Execute requested actions natively using tools.
        """.trimIndent()

        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                        put("TEXT")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(createFunctionDeclaration("openApp", "Launch any application installed on the device", mapOf("packageName" to "STRING", "appName" to "STRING")))
                            put(createFunctionDeclaration("searchAndCallContact", "Search contacts by name and place a phone call", mapOf("contactName" to "STRING")))
                            put(createFunctionDeclaration("sendWhatsAppMessage", "Search contact and open WhatsApp with pre-filled message", mapOf("contactName" to "STRING", "message" to "STRING")))
                            put(createFunctionDeclaration("sendGmail", "Draft or send an email via mail application", mapOf("recipientEmail" to "STRING", "subject" to "STRING", "body" to "STRING")))
                        })
                    })
                })
            })
        }

        ws.send(setupPayload.toString())
    }

    private fun createFunctionDeclaration(name: String, description: String, paramsMap: Map<String, String>): JSONObject {
        val propsObj = JSONObject()
        val requiredArray = JSONArray()
        for ((pName, pType) in paramsMap) {
            propsObj.put(pName, JSONObject().apply {
                put("type", pType)
            })
            requiredArray.put(pName)
        }

        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", propsObj)
                put("required", requiredArray)
            })
        }
    }

    private fun handleServerMessage(json: JSONObject) {
        // 1. Check for audio/text model turns
        val serverContent = json.optJSONObject("serverContent")
        if (serverContent != null) {
            if (serverContent.optBoolean("interrupted", false)) {
                audioEngine?.stopPlaybackAndFlush()
                isSpeaking = false
                setSassyState(AssistantState.LISTENING, "You cut me off! What's on your mind?")
                return
            }

            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue

                        // Text response part
                        val text = part.optString("text", null)
                        if (!text.isNullOrEmpty()) {
                            addLog("MAX", text)
                            setSassyState(AssistantState.SPEAKING, text)
                        }

                        // Inline audio PCM data
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val base64Data = inlineData.optString("data", null)
                            if (!base64Data.isNullOrEmpty()) {
                                isSpeaking = true
                                setSassyState(AssistantState.SPEAKING, _sassyStatus.value)
                                val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                audioEngine?.playAudioPcm(pcmBytes)
                            }
                        }
                    }
                }
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                scope.launch {
                    kotlinx.coroutines.delay(1000)
                    if (isSpeaking) {
                        isSpeaking = false
                        setSassyState(AssistantState.LISTENING, "I'm listening...")
                    }
                }
            }
        }

        // 2. Check for tool calls
        val toolCall = json.optJSONObject("toolCall")
        if (toolCall != null) {
            val functionCalls = toolCall.optJSONArray("functionCalls")
            if (functionCalls != null) {
                for (i in 0 until functionCalls.length()) {
                    val callObj = functionCalls.optJSONObject(i) ?: continue
                    val callId = callObj.optString("id", "")
                    val name = callObj.optString("name", "")
                    val args = callObj.optJSONObject("args") ?: JSONObject()

                    executeToolCallAndRespond(callId, name, args)
                }
            }
        }
    }

    private fun executeToolCallAndRespond(callId: String, functionName: String, args: JSONObject) {
        addLog("TOOL", "Executing $functionName with $args")
        setSassyState(AssistantState.PROCESSING, "Executing $functionName for you babe...")

        val resultStr = when (functionName) {
            "openApp" -> {
                val pkg = args.optString("packageName", "")
                val name = args.optString("appName", "")
                ToolExecutionEngine.openApp(context, pkg, name)
            }
            "searchAndCallContact" -> {
                val contact = args.optString("contactName", "")
                ToolExecutionEngine.searchAndCallContact(context, contact)
            }
            "sendWhatsAppMessage" -> {
                val contact = args.optString("contactName", "")
                val msg = args.optString("message", "")
                ToolExecutionEngine.sendWhatsAppMessage(context, contact, msg)
            }
            "sendGmail" -> {
                val recipient = args.optString("recipientEmail", "")
                val subject = args.optString("subject", "Hello from MAX")
                val body = args.optString("body", "")
                ToolExecutionEngine.sendGmail(context, recipient, subject, body)
            }
            else -> "Unknown tool function: $functionName"
        }

        addLog("SYSTEM", "Tool Output: $resultStr")

        // Send response back to WebSocket
        try {
            val toolResponseJson = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("response", JSONObject().apply {
                                put("output", JSONObject().apply {
                                    put("result", resultStr)
                                })
                            })
                        })
                    })
                })
            }
            webSocket?.send(toolResponseJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response over WS", e)
        }
    }

    private fun setSassyState(state: AssistantState, statusText: String) {
        _assistantState.value = state
        _sassyStatus.value = statusText
    }

    private fun addLog(sender: String, message: String) {
        val newLog = LogMessage(sender = sender, text = message)
        val current = _logHistory.value.toMutableList()
        current.add(0, newLog)
        if (current.size > 50) current.removeAt(current.size - 1)
        _logHistory.value = current
    }
}
