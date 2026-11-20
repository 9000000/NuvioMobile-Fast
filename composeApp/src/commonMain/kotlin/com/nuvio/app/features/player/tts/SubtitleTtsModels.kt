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
