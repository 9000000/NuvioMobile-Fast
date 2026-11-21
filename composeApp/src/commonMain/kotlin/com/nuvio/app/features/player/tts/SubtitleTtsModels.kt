package com.nuvio.app.features.player.tts

import kotlinx.serialization.Serializable

@Serializable
enum class SubtitleTtsEngineType {
    SYSTEM,
    PIPER_ONNX,
}

@Serializable
data class InstalledPiperModel(
    val id: String,
    val name: String,
    val dirPath: String,
    val sampleRate: Int = 22050,
)

@Serializable
data class SystemVoiceInfo(
    val name: String,
    val displayName: String,
    val locale: String,
    val isNetworkRequired: Boolean,
)

@Serializable
data class SystemTtsEngineInfo(
    val packageName: String,
    val label: String,
)

enum class SubtitleTtsVoice(
    val id: String,
    val displayName: String,
    val edgeVoiceName: String?,
    val langCode: String,
) {
    EdgeHoaiMy("edge_hoai_my", "Hoài My (Nữ - Edge AI)", "vi-VN-HoaiMyNeural", "vi-VN"),
    EdgeNamMinh("edge_nam_minh", "Nam Minh (Nam - Edge AI)", "vi-VN-NamMinhNeural", "vi-VN"),
    GoogleVi("google_vi", "Google AI (Tiếng Việt)", null, "vi");

    companion object {
        fun fromId(id: String?): SubtitleTtsVoice =
            entries.firstOrNull { it.id == id } ?: GoogleVi
    }
}

/**
 * Subtitle cue timing metadata used to align TTS speech pacing with subtitle slot boundaries.
 */
data class TimedSpeechCue(
    val rawText: String,
    val startMs: Long,
    val endMs: Long? = null,
    val nextCueStartMs: Long? = null,
)

/**
 * Runtime playback timing context for the active subtitle cue and upcoming lookahead cues.
 */
data class SubtitleCuePlaybackContext(
    val currentCue: TimedSpeechCue,
    val upcomingCues: List<TimedSpeechCue> = emptyList(),
    val playbackSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_PLAYBACK_SPEED,
    val positionMs: Long = currentCue.startMs,
)

/**
 * Persisted user configuration for subtitle TTS synthesis and timing synchronization.
 */
@Serializable
data class SubtitleTtsSettings(
    val enabled: Boolean = false,
    val engineType: SubtitleTtsEngineType = SubtitleTtsEngineType.SYSTEM,
    val systemEnginePackage: String? = null,
    val systemVoiceName: String? = null,
    val systemPitch: Float = 1.0f,
    val activePiperModelId: String? = null,
    val installedPiperModels: List<InstalledPiperModel> = emptyList(),
    val voice: SubtitleTtsVoice = SubtitleTtsVoice.GoogleVi,
    val movieVolume: Float = 0.25f,
    val ttsVolume: Float = 1.0f,
    val baseSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED,
    val autoSpeedEnabled: Boolean = true,
    val hideSubtitlesWhenEnabled: Boolean = false,
    val maxAutoSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED,
    val waitForCueCompletion: Boolean = false,
) {
    val shouldHideVisualSubtitles: Boolean
        get() = enabled && hideSubtitlesWhenEnabled
}

private val iso639_2To1 = mapOf(
    "vie" to "vi",
    "eng" to "en",
    "jpn" to "ja",
    "kor" to "ko",
    "zho" to "zh",
    "chi" to "zh",
    "fra" to "fr",
    "fre" to "fr",
    "spa" to "es",
    "deu" to "de",
    "ger" to "de",
    "ita" to "it",
    "por" to "pt",
    "rus" to "ru",
    "tha" to "th",
    "ind" to "id",
)

fun normalizeTtsLanguageCode(raw: String?): String? {
    val cleaned = raw?.trim()?.lowercase()?.takeIf {
        it.isNotEmpty() && it != "und" && it != "unknown" && it != "local"
    } ?: return null
    val base = cleaned.substringBefore('-').substringBefore('_')
    return iso639_2To1[base] ?: base.takeIf { it.length == 2 }
}

/**
 * Filters system TTS voices to match [targetLanguage] (e.g. "vi", "en", "vi-VN").
 * Falls back to Vietnamese ("vi") when [targetLanguage] is null/blank/unknown,
 * and returns [voices] unchanged if no voice matches the resolved language.
 */
fun filterSystemVoicesByLanguage(
    voices: List<SystemVoiceInfo>,
    targetLanguage: String?,
): List<SystemVoiceInfo> {
    if (voices.isEmpty()) return emptyList()
    val langCode = normalizeTtsLanguageCode(targetLanguage) ?: "vi"
    val matched = voices.filter { voice ->
        val voiceLang = voice.locale.substringBefore('-').substringBefore('_').lowercase()
        voiceLang == langCode
    }
    if (matched.isNotEmpty()) return matched
    if (langCode != "vi") {
        val viFallback = voices.filter { it.locale.startsWith("vi", ignoreCase = true) }
        if (viFallback.isNotEmpty()) return viFallback
    }
    return voices
}
