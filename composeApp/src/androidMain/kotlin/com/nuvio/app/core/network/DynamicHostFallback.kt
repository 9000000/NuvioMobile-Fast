package com.nuvio.app.core.network

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object DynamicHostFallback {
    private val workingHostsByRootDomain = ConcurrentHashMap<String, String>()

    fun registerWorkingHost(urlOrHost: String) {
        val host = runCatching {
            if (urlOrHost.contains("://")) URI(urlOrHost).host else urlOrHost
        }.getOrNull()?.lowercase()?.trim() ?: return
        val root = extractRootDomain(host)
        if (root.isNotBlank()) workingHostsByRootDomain[root] = host
    }

    fun getFallbackHost(deadHost: String): String? {
        val normalized = deadHost.lowercase().trim()
        val root = extractRootDomain(normalized)
        if (root.isBlank()) return null
        val candidate = workingHostsByRootDomain[root]
        return if (candidate != null && !candidate.equals(normalized, ignoreCase = true)) candidate else null
    }

    fun normalizeUrlWithFallback(url: String, preferredFallbackHost: String? = null): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        val candidate = if (!preferredFallbackHost.isNullOrBlank() && !preferredFallbackHost.equals(host, ignoreCase = true)) {
            val streamRoot = extractRootDomain(host)
            val fallbackRoot = extractRootDomain(preferredFallbackHost)
            if (streamRoot.isNotBlank() && streamRoot.equals(fallbackRoot, ignoreCase = true)) {
                preferredFallbackHost
            } else {
                getFallbackHost(host)
            }
        } else {
            getFallbackHost(host)
        }
        val targetHost = candidate ?: return url
        return if (!host.equals(targetHost, ignoreCase = true)) {
            url.replaceFirst(host, targetHost)
        } else {
            url
        }
    }

    fun extractRootDomain(host: String): String {
        val parts = host.lowercase().trim().split('.')
        if (parts.size <= 2) return host
        val secondLevel = setOf("co", "com", "net", "org", "edu", "gov", "ac", "biz", "info")
        return if (parts.size >= 3 && secondLevel.contains(parts[parts.size - 2])) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    fun isDynamicLiveStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrl = url.substringBefore('#')
        val path = cleanUrl.substringBefore('?')
        val fileName = path.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        val staticVideoExtensions = setOf(
            "mp4", "mkv", "avi", "webm", "flv", "mov", "wmv", "3gp", "m4v", "mpg", "mpeg"
        )
        if (staticVideoExtensions.contains(extension)) {
            return false
        }

        val dynamicScriptExtensions = setOf("php", "ashx", "asp", "aspx", "jsp", "cgi", "do", "action")
        if (dynamicScriptExtensions.contains(extension)) {
            return true
        }

        return url.contains('?') || !cleanUrl.contains('.')
    }
}
