package com.santiya.localaihub.browser

import java.net.URI

object BrowserUrlPolicy {
    private val allowedSchemes = setOf("http", "https")

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "URL is required" }

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid URL: $rawUrl")
        val scheme = uri.scheme?.lowercase()
        require(scheme in allowedSchemes) { "Only http and https URLs are allowed" }
        require(!uri.host.isNullOrBlank()) { "URL host is required" }
        require(uri.userInfo == null) { "URLs with embedded credentials are not allowed" }
        return uri.toASCIIString()
    }

    fun normalizeOrNull(rawUrl: String?): String? {
        return rawUrl?.let { runCatching { normalize(it) }.getOrNull() }
    }
}
