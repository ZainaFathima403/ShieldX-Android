package com.shieldx.app


object ApiKeys {
    const val GROQ_API_KEY = "your_groq_api_key_here"
    const val SAFE_BROWSING_API_KEY = "your_safe_browsing_api_key_here"

    fun hasGroqKey() = GROQ_API_KEY.isNotBlank() && GROQ_API_KEY != "your_groq_api_key_here"
    fun hasSafeBrowsingKey() = SAFE_BROWSING_API_KEY.isNotBlank() && SAFE_BROWSING_API_KEY != "your_safe_browsing_api_key_here"
}
