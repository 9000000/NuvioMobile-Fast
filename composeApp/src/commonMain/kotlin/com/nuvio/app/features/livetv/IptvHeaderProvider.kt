package com.nuvio.app.features.livetv

object IptvHeaderProvider {
    const val DEFAULT_IPTV_USER_AGENT = "Dalvik/2.1.0"

    private val KNOWN_IPTV_PATTERNS = listOf(
        "iptv", "/live/", "/hls/", "/stream/", ".ts", ".m3u8", ".mpd", "manifest", "mytv", "seenow", "tv360", "fptplay", "vietanhtv", "cleankey", "vtv"
    )

    fun isIptvStream(url: String): Boolean {
        if (url.isBlank()) return false
        val urlLower = url.lowercase()
        return KNOWN_IPTV_PATTERNS.any { pattern -> urlLower.contains(pattern) }
    }

    fun extractBaseUrl(url: String): String? {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val hostStart = schemeEnd + 3
        val nextSlash = trimmed.indexOf('/', startIndex = hostStart)
        val host = if (nextSlash > hostStart) trimmed.substring(0, nextSlash) else trimmed.substringBefore('?').substringBefore('#')
        return if (host.length > hostStart) "$host/" else null
    }

    fun getDefaultHeaders(url: String): Map<String, String> {
        if (!isIptvStream(url)) return emptyMap()
        val headers = mutableMapOf("User-Agent" to DEFAULT_IPTV_USER_AGENT)
        extractBaseUrl(url)?.let { referer -> headers["Referer"] = referer }
        return headers
    }

    fun mergeWithDefaults(url: String, userHeaders: Map<String, String>): Map<String, String> {
        if (userHeaders.isNotEmpty()) {
            val hasUserAgent = userHeaders.keys.any { it.equals("User-Agent", ignoreCase = true) }
            if (hasUserAgent) return userHeaders
            if (isIptvStream(url)) {
                return mapOf("User-Agent" to DEFAULT_IPTV_USER_AGENT) + userHeaders
            }
            return userHeaders
        }
        if (!isIptvStream(url)) return emptyMap()
        return getDefaultHeaders(url)
    }
}
