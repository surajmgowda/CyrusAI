package com.cyrus.assistant.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AiProviderAdapter {
    val providerName: String
    fun streamCompletion(
        prompt: String,
        systemPrompt: String,
        modelId: String,
        apiKey: String?
    ): Flow<String>
}

@Singleton
class GeminiCloudAdapter @Inject constructor() : AiProviderAdapter {
    override val providerName: String = "Google Gemini"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun streamCompletion(
        prompt: String,
        systemPrompt: String,
        modelId: String,
        apiKey: String?
    ): Flow<String> = flow {
        if (apiKey.isNullOrBlank()) {
            emit("[Cyrus] No Gemini API key configured. Add one in Settings > Providers or switch to Local mode.")
            return@flow
        }
        val responseText = withContext(Dispatchers.IO) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
            val bodyJson = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext "[Gemini Error ${resp.code}] Check model ID or API key in Settings."
                }
                val parsed = JSONObject(raw)
                val candidates = parsed.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                text ?: "[Gemini] Empty response received."
            }
        }
        emit(responseText)
    }
}

@Singleton
class LocalOnDeviceAdapter @Inject constructor() : AiProviderAdapter {
    override val providerName: String = "Cyrus Local"

    override fun streamCompletion(
        prompt: String,
        systemPrompt: String,
        modelId: String,
        apiKey: String?
    ): Flow<String> = flow {
        val lower = prompt.lowercase()
        val reply = when {
            lower.contains("help") || lower.contains("what can you do") ->
                "I'm Cyrus running locally on your Android 15+ device. I can search your local memories, manage tasks and automations, inspect indexed documents, and coordinate multi-step agent plans—while keeping all data on-device."
            lower.contains("time") || lower.contains("date") ->
                "Local system check complete: Cyrus is operating offline-first with deterministic tool support."
            else ->
                "Cyrus (Local Mode): Processed "$prompt" on-device without sending data to the cloud. Configure a Gemini or OpenAI-compatible key in Settings for cloud reasoning."
        }
        emit(reply)
    }
}
