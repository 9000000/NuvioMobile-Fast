package com.nuvio.app.features.home

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object HomeHeroCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): List<MetaPreview> {
        val payload = runCatching {
            HomeHeroStorage.loadPayload()
        }.getOrNull()?.trim() ?: return emptyList()
        if (payload.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<MetaPreview>>(payload)
        }.getOrDefault(emptyList())
    }

    fun save(items: List<MetaPreview>) {
        if (items.isEmpty()) return
        runCatching {
            val encoded = json.encodeToString(items)
            HomeHeroStorage.savePayload(encoded)
        }
    }
}
