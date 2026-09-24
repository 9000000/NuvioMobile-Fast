package com.nuvio.app.features.player.skip

import com.nuvio.app.features.addons.httpGetText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ArmIdResolver {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val ARM_BASE = "https://arm.haglund.dev/api/v2"

    data class ArmEntry(
        val mal: String? = null,
        val anilist: String? = null,
        val kitsu: String? = null,
        val imdb: String? = null,
    )

    private val imdbCache = HashMap<String, List<ArmEntry>>()
    private val sourceCache = HashMap<String, ArmEntry?>()

    private fun JsonElement?.asStringOrNull(): String? {
        if (this == null || this is JsonNull) return null
        return try {
            jsonPrimitive.content.takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveImdb(imdbId: String): List<ArmEntry> {
        val cleanId = imdbId.trim()
        if (cleanId.isBlank()) return emptyList()
        imdbCache[cleanId]?.let { return it }

        return try {
            val url = "$ARM_BASE/imdb?id=$cleanId&include=myanimelist,anilist,kitsu"
            val text = httpGetText(url)
            val array = json.parseToJsonElement(text).jsonArray
            val list = mutableListOf<ArmEntry>()
            for (element in array) {
                val obj = element.jsonObject
                list.add(
                    ArmEntry(
                        mal = obj["myanimelist"].asStringOrNull(),
                        anilist = obj["anilist"].asStringOrNull(),
                        kitsu = obj["kitsu"].asStringOrNull(),
                        imdb = cleanId,
                    )
                )
            }
            list.also { imdbCache[cleanId] = it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveBySource(source: String, id: String): ArmEntry? {
        val cleanSource = when (source.lowercase()) {
            "mal", "myanimelist" -> "myanimelist"
            "kitsu" -> "kitsu"
            "anilist" -> "anilist"
            "imdb" -> "imdb"
            else -> source.lowercase()
        }
        val cleanId = id.trim()
        if (cleanId.isBlank()) return null
        if (cleanSource == "imdb") {
            return resolveImdb(cleanId).firstOrNull()
        }
        val cacheKey = "$cleanSource:$cleanId"
        sourceCache[cacheKey]?.let { return it }

        return try {
            val url = "$ARM_BASE/ids?source=$cleanSource&id=$cleanId&include=myanimelist,anilist,kitsu,imdb"
            val text = httpGetText(url)
            val obj = json.parseToJsonElement(text).jsonObject
            ArmEntry(
                mal = obj["myanimelist"].asStringOrNull()
                    ?: if (cleanSource == "myanimelist") cleanId else null,
                anilist = obj["anilist"].asStringOrNull()
                    ?: if (cleanSource == "anilist") cleanId else null,
                kitsu = obj["kitsu"].asStringOrNull()
                    ?: if (cleanSource == "kitsu") cleanId else null,
                imdb = obj["imdb"].asStringOrNull(),
            ).also { sourceCache[cacheKey] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        imdbCache.clear()
        sourceCache.clear()
    }
}
