package com.nuvio.app.features.player.tts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PiperConfigParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class ParsedPiperConfig(
        val sampleRate: Int = 22050,
        val espeakVoice: String = "vi",
        val numSpeakers: Int = 1,
        val noiseScale: Float = 0.667f,
        val lengthScale: Float = 1.0f,
        val noiseW: Float = 0.8f,
        val tokensText: String = "",
    )

    fun parseConfig(jsonContent: String): ParsedPiperConfig {
        val root = json.parseToJsonElement(jsonContent).jsonObject

        val sampleRate = (root["audio"]?.jsonObject?.get("sample_rate")?.jsonPrimitive?.intOrNull ?: 22050)
            .coerceAtLeast(8000)
        val espeakVoice = root["espeak"]?.jsonObject?.get("voice")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: "vi"
        val numSpeakers = (root["num_speakers"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)

        val inference = root["inference"]?.jsonObject
        val noiseScale = inference?.get("noise_scale")?.jsonPrimitive?.floatOrNull ?: 0.667f
        val lengthScale = inference?.get("length_scale")?.jsonPrimitive?.floatOrNull ?: 1.0f
        val noiseW = inference?.get("noise_w")?.jsonPrimitive?.floatOrNull ?: 0.8f

        // Generate tokens.txt matching csukuangfj/vits-piper-vi_VN-vais1000-medium format:
        // "<symbol> <id>\n" sorted by id, preserving single space ("  3\n") with no blank lines.
        val idEntries = mutableListOf<Pair<String, Int>>()
        val idMapObj = root["phoneme_id_map"]?.jsonObject
        if (idMapObj != null) {
            for ((symbol, idElement) in idMapObj) {
                if (symbol.isEmpty() || symbol.contains('\n') || symbol.contains('\r')) continue
                val id = when (idElement) {
                    is JsonArray -> idElement.firstOrNull()?.jsonPrimitive?.intOrNull
                    else -> idElement.jsonPrimitive.intOrNull
                } ?: continue
                idEntries.add(symbol to id)
            }
        }
        idEntries.sortBy { it.second }

        val tokensBuilder = StringBuilder()
        for ((symbol, id) in idEntries) {
            tokensBuilder.append(symbol).append(' ').append(id).append('\n')
        }

        return ParsedPiperConfig(
            sampleRate = sampleRate,
            espeakVoice = espeakVoice,
            numSpeakers = numSpeakers,
            noiseScale = noiseScale,
            lengthScale = lengthScale,
            noiseW = noiseW,
            tokensText = tokensBuilder.toString(),
        )
    }

    fun normalizeHuggingFaceUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("huggingface.co") && url.contains("/blob/")) {
            url = url.replace("/blob/", "/resolve/")
        }
        return url
    }

    fun inferJsonUrl(onnxUrl: String): String {
        val normalized = normalizeHuggingFaceUrl(onnxUrl)
        val queryIdx = normalized.indexOf('?')
        val base = if (queryIdx >= 0) normalized.substring(0, queryIdx) else normalized
        val query = if (queryIdx >= 0) normalized.substring(queryIdx) else ""
        return when {
            base.endsWith(".json", ignoreCase = true) -> normalized
            base.endsWith(".onnx", ignoreCase = true) -> "$base.json$query"
            else -> normalized
        }
    }
}
