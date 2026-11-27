package com.cyrus.assistant.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ResearchResult(
    val summary: String,
    val citations: List<String>,
    val isUntrustedNotice: String = "External web content treated as untrusted data (FR-RES-05)."
)

@Singleton
class WebResearchTool @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun isSafePublicHttpsUrl(rawUrl: String): Boolean {
        return runCatching {
            val uri = URI(rawUrl)
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host ?: return false
            val addr = InetAddress.getByName(host)
            !(addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress)
        }.getOrDefault(false)
    }

    suspend fun searchAndCite(query: String): ResearchResult = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        if (!isSafePublicHttpsUrl(url)) {
            return@withContext ResearchResult("Blocked unsafe URL by SSRF policy.", emptyList())
        }
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "CyrusAndroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@runCatching ResearchResult("Search provider returned HTTP ${resp.code}.", emptyList())
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val abstractText = json.optString("AbstractText").takeIf { it.isNotBlank() }
                val abstractUrl = json.optString("AbstractURL").takeIf { it.isNotBlank() }
                val citations = mutableListOf<String>()
                if (abstractUrl != null) citations.add(abstractUrl)

                val related = json.optJSONArray("RelatedTopics")
                val bullets = mutableListOf<String>()
                if (abstractText != null) bullets.add(abstractText)
                if (related != null) {
                    for (i in 0 until minOf(3, related.length())) {
                        val item = related.optJSONObject(i) ?: continue
                        val t = item.optString("Text")
                        val u = item.optString("FirstURL")
                        if (t.isNotBlank()) bullets.add("• $t")
                        if (u.isNotBlank()) citations.add(u)
                    }
                }
                val body = if (bullets.isEmpty()) {
                    "No instant summary returned for "$query". Try refining your query or configuring a dedicated search API."
                } else {
                    bullets.joinToString("
")
                }
                ResearchResult(body, citations.distinct())
            }
        }.getOrElse { ex ->
            ResearchResult("Research failed (offline or network error): ${ex.message}", emptyList())
        }
    }
}
