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

/**
 * Wraps Google's Safe Browsing API (v4) — a free, real threat-intelligence database of known
 * phishing/malware/unwanted-software URLs. This is the "real fix" for the heuristics-only engine:
 * a hand-typed fake link can dodge pattern checks, but if it's an actual known-bad URL already
 * reported to Google, this catches it directly instead of guessing from patterns.
 */
object SafeBrowsingClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private const val ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find"

    /**
     * Returns a human-readable threat type string (e.g. "SOCIAL_ENGINEERING", "MALWARE") if
     * Google's database flags this URL, or null if it's clean / the check couldn't run
     * (no key configured, no network, or the request failed — callers should treat null as
     * "unknown," not "safe," and keep relying on the heuristic engine as well).
     */
    suspend fun checkUrl(url: String): String? {
        if (!ApiKeys.hasSafeBrowsingKey()) return null

        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientId", "shieldx-android")
                        put("clientVersion", "1.0.0")
                    })
                    put("threatInfo", JSONObject().apply {
                        put("threatTypes", JSONArray(listOf(
                            "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"
                        )))
                        put("platformTypes", JSONArray(listOf("ANY_PLATFORM")))
                        put("threatEntryTypes", JSONArray(listOf("URL")))
                        put("threatEntries", JSONArray().put(JSONObject().put("url", url)))
                    })
                }.toString()

                val request = Request.Builder()
                    .url("$ENDPOINT?key=${ApiKeys.SAFE_BROWSING_API_KEY}")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string().orEmpty()
                    if (text.isBlank() || text.trim() == "{}") return@withContext null // no match = clean

                    val json = JSONObject(text)
                    val matches = json.optJSONArray("matches") ?: return@withContext null
                    if (matches.length() == 0) return@withContext null
                    matches.getJSONObject(0).optString("threatType", "KNOWN_THREAT")
                }
            } catch (e: Exception) {
                null // network hiccup etc. — fall back to heuristics only, don't crash the scan
            }
        }
    }
}
