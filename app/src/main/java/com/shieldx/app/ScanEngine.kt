package com.shieldx.app

import java.net.URI

enum class RiskLevel { SAFE, WARN, DANGER, SKIP }

data class ScanResult(
    val level: RiskLevel,
    val score: Int,
    val reasons: List<String>,
    val host: String = ""
)

object ScanEngine {

    private val SHORTENERS = listOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
        "buff.ly", "rebrand.ly", "cutt.ly", "shorturl.at"
    )
    private val SUSPICIOUS_TLDS = listOf(
        ".zip", ".mov", ".xyz", ".top", ".gq", ".tk", ".ml", ".cf", ".ga", ".icu", ".click", ".link", ".work"
    )
    private val BAIT_WORDS = listOf(
        "verify your account", "urgent action", "suspended", "claim your prize", "won a",
        "limited time", "confirm your identity", "update payment", "unusual activity",
        "click here immediately", "free gift", "congratulations you", "act now",
        "security alert", "password expired", "otp", "refund pending"
    )
    private val BRAND_KEYWORDS = listOf(
        "paypal", "amazon", "apple", "microsoft", "google", "netflix", "bank", "irs",
        "instagram", "facebook", "whatsapp"
    )
    private val URL_REGEX = Regex("""((https?://|www\.)[^\s]+)""", RegexOption.IGNORE_CASE)
    private val IP_HOST_REGEX = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

    fun extractUrls(text: String): List<String> =
        URL_REGEX.findAll(text).map { it.value }.toList()

    fun analyzeUrl(raw: String): ScanResult {
        val reasons = mutableListOf<String>()
        var score = 0

        val normalized = if (raw.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) raw else "http://$raw"
        val uri = try { URI(normalized) } catch (e: Exception) {
            return ScanResult(RiskLevel.WARN, 40, listOf("Could not parse this as a valid link — double-check it before using it."))
        }

        val host = (uri.host ?: "").lowercase()
        if (host.isEmpty()) {
            return ScanResult(RiskLevel.SKIP, 0, emptyList())
        }
        val full = raw.lowercase()

        if (!raw.lowercase().startsWith("https://")) {
            score += 15; reasons.add("Connection is not secured with HTTPS.")
        }
        if (IP_HOST_REGEX.matches(host)) {
            score += 35; reasons.add("The domain is a raw IP address, a very common phishing pattern.")
        }
        if (full.contains("@")) {
            score += 30; reasons.add("Link contains an \"@\" symbol, which can hide the real destination.")
        }
        if (host.count { it == '-' } >= 3) {
            score += 15; reasons.add("Domain has an unusually high number of hyphens.")
        }
        if (SHORTENERS.any { host.contains(it) }) {
            score += 20; reasons.add("This is a link shortener — the real destination is hidden.")
        }
        val tldHit = SUSPICIOUS_TLDS.firstOrNull { host.endsWith(it) }
        if (tldHit != null) {
            score += 20; reasons.add("Domain uses a top-level domain ($tldHit) frequently abused for scams.")
        }
        if (host.split(".").size > 3) {
            score += 15; reasons.add("Domain has excessive subdomains, often used to disguise the true host.")
        }
        val brandHit = BRAND_KEYWORDS.firstOrNull { host.contains(it) && !host.endsWith("$it.com") }
        if (brandHit != null) {
            score += 30; reasons.add("Mentions the brand \"$brandHit\" but is not on that brand's real domain — likely impersonation.")
        }
        if (full.length > 90) {
            score += 10; reasons.add("Unusually long link, often used to obscure a fake domain.")
        }
        if (BAIT_WORDS.any { full.contains(it.split(" ")[0]) }) {
            score += 10; reasons.add("Link contains urgency/bait wording.")
        }

        if (reasons.isEmpty()) reasons.add("No known phishing indicators detected. Stay cautious with unfamiliar links regardless.")

        val level = when {
            score >= 45 -> RiskLevel.DANGER
            score >= 20 -> RiskLevel.WARN
            else -> RiskLevel.SAFE
        }
        return ScanResult(level, score.coerceAtMost(100), reasons, host)
    }

    /** Analyzes a shared message (SMS/WhatsApp/email text etc.), including any links inside it. */
    fun analyzeMessage(text: String): ScanResult {
        val reasons = mutableListOf<String>()
        var score = 0
        val lower = text.lowercase()

        val hits = BAIT_WORDS.filter { lower.contains(it) }
        if (hits.isNotEmpty()) {
            score += hits.size * 12
            reasons.add("Contains urgency/bait phrasing: \"${hits[0]}\"${if (hits.size > 1) " (+${hits.size - 1} more)" else ""}.")
        }
        if (Regex("(\\$|₹|money|payment|gift card|wire transfer|bitcoin|crypto|bank details|cvv|otp|pin number)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            score += 15; reasons.add("Message asks about money, payment details, or sensitive financial info.")
        }
        if (Regex("(click here|tap here|link below|verify now)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            score += 10; reasons.add("Pushes you to click a link immediately.")
        }
        if (Regex("(dear customer|dear user|valued customer)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            score += 8; reasons.add("Generic greeting instead of your name — common in mass phishing.")
        }

        val urls = extractUrls(text)
        val urlResults = urls.map { it to analyzeUrl(it) }
        urlResults.forEach { (_, r) ->
            when (r.level) {
                RiskLevel.DANGER -> score += 25
                RiskLevel.WARN -> score += 10
                else -> {}
            }
        }
        if (urls.isNotEmpty()) reasons.add("Found ${urls.size} link(s) in the message.")

        if (reasons.isEmpty()) reasons.add("No strong phishing indicators found in the text. Still verify the sender independently.")

        val level = when {
            score >= 45 -> RiskLevel.DANGER
            score >= 20 -> RiskLevel.WARN
            else -> RiskLevel.SAFE
        }
        return ScanResult(level, score.coerceAtMost(100), reasons)
    }

    /**
     * Same as analyzeUrl, but also checks Google's Safe Browsing database. If Google has this
     * URL flagged as a known threat, that overrides the heuristic score — a confirmed real-world
     * match is stronger evidence than pattern-matching alone.
     */
    suspend fun analyzeUrlWithSafeBrowsing(raw: String): ScanResult {
        val heuristic = analyzeUrl(raw)
        val threatType = SafeBrowsingClient.checkUrl(raw.let {
            if (it.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) it else "http://$it"
        })
        return if (threatType != null) {
            ScanResult(
                level = RiskLevel.DANGER,
                score = 100,
                reasons = listOf("Google Safe Browsing has this URL flagged as a known threat (${threatType.replace('_', ' ').lowercase()}).") + heuristic.reasons,
                host = heuristic.host
            )
        } else {
            heuristic
        }
    }

    /** Same as analyzeMessage, but also checks any embedded links against Safe Browsing. */
    suspend fun analyzeMessageWithSafeBrowsing(text: String): ScanResult {
        val heuristic = analyzeMessage(text)
        if (!ApiKeys.hasSafeBrowsingKey()) return heuristic

        val urls = extractUrls(text)
        for (url in urls) {
            val threatType = SafeBrowsingClient.checkUrl(url)
            if (threatType != null) {
                return ScanResult(
                    level = RiskLevel.DANGER,
                    score = 100,
                    reasons = listOf("Google Safe Browsing flagged a link in this message as a known threat (${threatType.replace('_', ' ').lowercase()}).") + heuristic.reasons
                )
            }
        }
        return heuristic
    }
}
