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

@Singleton
class OpenAiCompatibleAdapter @Inject constructor() : AiProviderAdapter {
    override val providerName: String = "OpenAI-Compatible"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun streamWithEndpoint(
        endpointUrl: String,
        prompt: String,
        systemPrompt: String,
        modelId: String,
        apiKey: String?
    ): Flow<String> = flow {
        if (apiKey.isNullOrBlank()) {
            emit("[Cyrus] No API key configured for $providerName.")
            return@flow
        }
        val text = withContext(Dispatchers.IO) {
            val url = endpointUrl.trimEnd('/') + "/chat/completions"
            val payload = JSONObject().apply {
                put("model", modelId)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            }.toString()
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext "[$providerName Error ${resp.code}] Check endpoint, model ID, or API key."
                }
                JSONObject(raw).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: "[$providerName] Empty response."
            }
        }
        emit(text)
    }

    override fun streamCompletion(
        prompt: String,
        systemPrompt: String,
        modelId: String,
        apiKey: String?
    ): Flow<String> = streamWithEndpoint("https://api.openai.com/v1", prompt, systemPrompt, modelId, apiKey)
}

@Singleton
class FastApiBackendAdapter @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun sendChat(backendBaseUrl: String, prompt: String, mode: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = backendBaseUrl.trimEnd('/') + "/v1/chat/completions"
                val body = JSONObject().apply {
                    put("prompt", prompt)
                    put("mode", mode)
                }.toString()
                val req = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Backend HTTP ${resp.code}")
                    JSONObject(raw).optString("reply", raw)
                }
            }
        }
}
