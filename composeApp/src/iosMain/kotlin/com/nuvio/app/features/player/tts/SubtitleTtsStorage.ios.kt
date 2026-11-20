package com.nuvio.app.features.player.tts

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object SubtitleTtsStorage {
    private const val enabledKey = "tts_enabled"
    private const val engineTypeKey = "tts_engine_type"
    private const val systemEnginePackageKey = "tts_system_engine_package"
    private const val systemVoiceNameKey = "tts_system_voice_name"
    private const val systemPitchKey = "tts_system_pitch"
    private const val activePiperModelIdKey = "tts_active_piper_model_id"
    private const val installedPiperModelsKey = "tts_installed_piper_models_json"
    private const val voiceIdKey = "tts_voice_id"
    private const val movieVolumeKey = "tts_movie_volume"
    private const val ttsVolumeKey = "tts_volume"
    private const val baseSpeedKey = "tts_base_speed"
    private const val autoSpeedEnabledKey = "tts_auto_speed_enabled"
    private const val hideSubtitlesKey = "tts_hide_subtitles"
    private const val maxAutoSpeedKey = "tts_max_auto_speed"
    private const val waitForCueCompletionKey = "tts_wait_for_cue_completion"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    actual fun loadSettings(): SubtitleTtsSettings {
        val default = SubtitleTtsSettings()
        val defaults = NSUserDefaults.standardUserDefaults
        val enabledScoped = ProfileScopedKey.of(enabledKey)
        val engineTypeScoped = ProfileScopedKey.of(engineTypeKey)
        val systemEnginePackageScoped = ProfileScopedKey.of(systemEnginePackageKey)
        val systemVoiceNameScoped = ProfileScopedKey.of(systemVoiceNameKey)
        val systemPitchScoped = ProfileScopedKey.of(systemPitchKey)
        val activePiperModelScoped = ProfileScopedKey.of(activePiperModelIdKey)
        val installedPiperModelsScoped = ProfileScopedKey.of(installedPiperModelsKey)
        val voiceScoped = ProfileScopedKey.of(voiceIdKey)
        val movieVolScoped = ProfileScopedKey.of(movieVolumeKey)
        val ttsVolScoped = ProfileScopedKey.of(ttsVolumeKey)
        val baseSpeedScoped = ProfileScopedKey.of(baseSpeedKey)
        val autoSpeedScoped = ProfileScopedKey.of(autoSpeedEnabledKey)
        val hideSubsScoped = ProfileScopedKey.of(hideSubtitlesKey)
        val maxAutoSpeedScoped = ProfileScopedKey.of(maxAutoSpeedKey)
        val waitForCueCompletionScoped = ProfileScopedKey.of(waitForCueCompletionKey)

        val engineTypeName = defaults.stringForKey(engineTypeScoped)
        val engineType = engineTypeName?.let { name ->
            runCatching { SubtitleTtsEngineType.valueOf(name) }.getOrNull()
        } ?: default.engineType

        val installedModelsJson = defaults.stringForKey(installedPiperModelsScoped)
        val installedModels = installedModelsJson?.let { rawJson ->
            runCatching { json.decodeFromString<List<InstalledPiperModel>>(rawJson) }.getOrNull()
        } ?: default.installedPiperModels

        return SubtitleTtsSettings(
            enabled = if (defaults.objectForKey(enabledScoped) != null) {
                defaults.boolForKey(enabledScoped)
            } else {
                default.enabled
            },
            engineType = engineType,
            systemEnginePackage = defaults.stringForKey(systemEnginePackageScoped) ?: default.systemEnginePackage,
            systemVoiceName = defaults.stringForKey(systemVoiceNameScoped) ?: default.systemVoiceName,
            systemPitch = if (defaults.objectForKey(systemPitchScoped) != null) {
                defaults.floatForKey(systemPitchScoped).coerceIn(0.6f, 1.5f)
            } else {
                default.systemPitch
            },
            activePiperModelId = defaults.stringForKey(activePiperModelScoped) ?: default.activePiperModelId,
            installedPiperModels = installedModels,
            voice = SubtitleTtsVoice.fromId(defaults.stringForKey(voiceScoped)),
            movieVolume = if (defaults.objectForKey(movieVolScoped) != null) {
                defaults.floatForKey(movieVolScoped).coerceIn(0f, 1f)
            } else {
                default.movieVolume
            },
            ttsVolume = if (defaults.objectForKey(ttsVolScoped) != null) {
                defaults.floatForKey(ttsVolScoped).coerceIn(0f, 1f)
            } else {
                default.ttsVolume
            },
            baseSpeed = if (defaults.objectForKey(baseSpeedScoped) != null) {
                SubtitleTtsTimingCalculator.clampFinite(
                    value = defaults.floatForKey(baseSpeedScoped),
                    fallback = default.baseSpeed,
                    min = SubtitleTtsTimingCalculator.MIN_BASE_SPEED,
                    max = SubtitleTtsTimingCalculator.MAX_BASE_SPEED,
                )
            } else {
                default.baseSpeed
            },
            autoSpeedEnabled = if (defaults.objectForKey(autoSpeedScoped) != null) {
                defaults.boolForKey(autoSpeedScoped)
            } else {
                default.autoSpeedEnabled
            },
            hideSubtitlesWhenEnabled = if (defaults.objectForKey(hideSubsScoped) != null) {
                defaults.boolForKey(hideSubsScoped)
            } else {
                default.hideSubtitlesWhenEnabled
            },
            maxAutoSpeed = if (defaults.objectForKey(maxAutoSpeedScoped) != null) {
                SubtitleTtsTimingCalculator.clampFinite(
                    value = defaults.floatForKey(maxAutoSpeedScoped),
                    fallback = default.maxAutoSpeed,
                    min = SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED,
                    max = SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
                )
            } else {
                default.maxAutoSpeed
            },
            waitForCueCompletion = if (defaults.objectForKey(waitForCueCompletionScoped) != null) {
                defaults.boolForKey(waitForCueCompletionScoped)
            } else {
                default.waitForCueCompletion
            },
        )
    }

    actual fun saveSettings(settings: SubtitleTtsSettings) {
        val defaults = NSUserDefaults.standardUserDefaults
        val modelsJson = runCatching { json.encodeToString(settings.installedPiperModels) }.getOrDefault("[]")
        val safeBaseSpeed = SubtitleTtsTimingCalculator.clampFinite(
            value = settings.baseSpeed,
            fallback = SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED,
            min = SubtitleTtsTimingCalculator.MIN_BASE_SPEED,
            max = SubtitleTtsTimingCalculator.MAX_BASE_SPEED,
        )
        val safeMaxAutoSpeed = SubtitleTtsTimingCalculator.clampFinite(
            value = settings.maxAutoSpeed,
            fallback = SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED,
            min = SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED,
            max = SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
        )
        defaults.setBool(settings.enabled, forKey = ProfileScopedKey.of(enabledKey))
        defaults.setObject(settings.engineType.name, forKey = ProfileScopedKey.of(engineTypeKey))
        defaults.setObject(settings.systemEnginePackage, forKey = ProfileScopedKey.of(systemEnginePackageKey))
        defaults.setObject(settings.systemVoiceName, forKey = ProfileScopedKey.of(systemVoiceNameKey))
        defaults.setFloat(settings.systemPitch.coerceIn(0.6f, 1.5f), forKey = ProfileScopedKey.of(systemPitchKey))
        defaults.setObject(settings.activePiperModelId, forKey = ProfileScopedKey.of(activePiperModelIdKey))
        defaults.setObject(modelsJson, forKey = ProfileScopedKey.of(installedPiperModelsKey))
        defaults.setObject(settings.voice.id, forKey = ProfileScopedKey.of(voiceIdKey))
        defaults.setFloat(settings.movieVolume.coerceIn(0f, 1f), forKey = ProfileScopedKey.of(movieVolumeKey))
        defaults.setFloat(settings.ttsVolume.coerceIn(0f, 1f), forKey = ProfileScopedKey.of(ttsVolumeKey))
        defaults.setFloat(safeBaseSpeed, forKey = ProfileScopedKey.of(baseSpeedKey))
        defaults.setBool(settings.autoSpeedEnabled, forKey = ProfileScopedKey.of(autoSpeedEnabledKey))
        defaults.setBool(settings.hideSubtitlesWhenEnabled, forKey = ProfileScopedKey.of(hideSubtitlesKey))
        defaults.setFloat(safeMaxAutoSpeed, forKey = ProfileScopedKey.of(maxAutoSpeedKey))
        defaults.setBool(settings.waitForCueCompletion, forKey = ProfileScopedKey.of(waitForCueCompletionKey))
    }
}
