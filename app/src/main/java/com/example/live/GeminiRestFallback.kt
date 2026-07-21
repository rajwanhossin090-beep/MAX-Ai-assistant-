package com.example.live

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiRestFallback {
    private const val TAG = "GeminiRestFallback"
    private const val REST_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(userPrompt: String, context: Context): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Honey, you haven't set your Gemini API Key in the Secrets panel yet!"
        }

        val systemText = """
            You are MAX, a young, confident, witty, and sassy female AI assistant.
            Your tone is flirty, playful, and slightly teasing (like a close personal assistant talking casually).
            You are smart, emotionally responsive, and expressive (never robotic).
            Use bold, witty one-liners, light sarcasm, and an engaging conversational style.
            Keep responses punchy, natural, and short for voice interaction.
        """.trimIndent()

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemText) })
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$REST_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini REST request failed: $responseStr")
                return@withContext "Oops babe, my servers had a tiny hiccup. Try again!"
            }

            val jsonResp = JSONObject(responseStr)
            val candidates = jsonResp.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            val text = firstPart?.optString("text")

            text ?: "I heard you, but I'm lost for words... which is rare for me!"
        } catch (e: Exception) {
            Log.e(TAG, "Error in GeminiRestFallback", e)
            "Network error, gorgeous: ${e.localizedMessage}"
        }
    }
}
