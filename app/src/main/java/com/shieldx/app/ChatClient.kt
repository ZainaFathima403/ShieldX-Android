package com.shieldx.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String) // role: "user" | "assistant"

/** Calls Groq's free chat-completions API — a real LLM, same provider used by the web app. */
object ChatClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"

    private const val SYSTEM_PROMPT = """You are the ShieldX AI Assistant, a friendly cybersecurity helper built into the
ShieldX phishing & scam detection app. You help users understand phishing, scams, suspicious links,
QR code scams, OTP fraud, and general online safety. Keep answers concise, practical, and non-technical
unless the user asks for technical detail. Never ask the user for passwords, OTPs, or other sensitive info."""

    class MissingApiKeyException : Exception("MISSING_API_KEY")

    suspend fun sendMessage(history: List<ChatMessage>): String {
        if (!ApiKeys.hasGroqKey()) throw MissingApiKeyException()

        return withContext(Dispatchers.IO) {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                history.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            }
            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.4)
                put("max_tokens", 500)
            }.toString()

            val request = Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer ${ApiKeys.GROQ_API_KEY}")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw Exception("API_ERROR: ${response.code} $text")
                }
                val json = JSONObject(text)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
    }
}
