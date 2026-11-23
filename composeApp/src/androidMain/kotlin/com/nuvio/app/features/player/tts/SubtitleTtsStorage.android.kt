package com.nuvio.app.features.player.tts

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal actual object SubtitleTtsStorage {
    private const val preferencesName = "nuvio_subtitle_tts"
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
    private const val transliterateEnglishNamesKey = "tts_transliterate_english_names"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences? {
        preferences?.let { return it }
        val context = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (context != null) {
            preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }
        return preferences
    }

    actual fun loadSettings(): SubtitleTtsSettings {
        val default = SubtitleTtsSettings()
        val sharedPreferences = prefs() ?: return default
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
        val transliterateScoped = ProfileScopedKey.of(transliterateEnglishNamesKey)

        val engineType = sharedPreferences.getString(engineTypeScoped, null)?.let { name ->
            runCatching { SubtitleTtsEngineType.valueOf(name) }.getOrNull()
        } ?: default.engineType

        val installedModels = sharedPreferences.getString(installedPiperModelsScoped, null)?.let { rawJson ->
            runCatching { json.decodeFromString<List<InstalledPiperModel>>(rawJson) }.getOrNull()
        } ?: default.installedPiperModels

        return SubtitleTtsSettings(
            enabled = if (sharedPreferences.contains(enabledScoped)) {
                sharedPreferences.getBoolean(enabledScoped, default.enabled)
            } else {
                default.enabled
            },
            engineType = engineType,
            systemEnginePackage = sharedPreferences.getString(systemEnginePackageScoped, null) ?: default.systemEnginePackage,
            systemVoiceName = sharedPreferences.getString(systemVoiceNameScoped, null) ?: default.systemVoiceName,
            systemPitch = if (sharedPreferences.contains(systemPitchScoped)) {
                sharedPreferences.getFloat(systemPitchScoped, default.systemPitch).coerceIn(0.6f, 1.5f)
            } else {
                default.systemPitch
            },
            activePiperModelId = sharedPreferences.getString(activePiperModelScoped, null) ?: default.activePiperModelId,
            installedPiperModels = installedModels,
            voice = SubtitleTtsVoice.fromId(sharedPreferences.getString(voiceScoped, default.voice.id)),
            movieVolume = if (sharedPreferences.contains(movieVolScoped)) {
                sharedPreferences.getFloat(movieVolScoped, default.movieVolume).coerceIn(0f, 1f)
            } else {
                default.movieVolume
            },
            ttsVolume = if (sharedPreferences.contains(ttsVolScoped)) {
                sharedPreferences.getFloat(ttsVolScoped, default.ttsVolume).coerceIn(0f, 1f)
            } else {
                default.ttsVolume
            },
            baseSpeed = if (sharedPreferences.contains(baseSpeedScoped)) {
                SubtitleTtsTimingCalculator.clampFinite(
                    value = sharedPreferences.getFloat(baseSpeedScoped, default.baseSpeed),
                    fallback = default.baseSpeed,
                    min = SubtitleTtsTimingCalculator.MIN_BASE_SPEED,
                    max = SubtitleTtsTimingCalculator.MAX_BASE_SPEED,
                )
            } else {
                default.baseSpeed
            },
            autoSpeedEnabled = if (sharedPreferences.contains(autoSpeedScoped)) {
                sharedPreferences.getBoolean(autoSpeedScoped, default.autoSpeedEnabled)
            } else {
                default.autoSpeedEnabled
            },
            hideSubtitlesWhenEnabled = if (sharedPreferences.contains(hideSubsScoped)) {
                sharedPreferences.getBoolean(hideSubsScoped, default.hideSubtitlesWhenEnabled)
            } else {
                default.hideSubtitlesWhenEnabled
            },
            maxAutoSpeed = if (sharedPreferences.contains(maxAutoSpeedScoped)) {
                SubtitleTtsTimingCalculator.clampFinite(
                    value = sharedPreferences.getFloat(maxAutoSpeedScoped, default.maxAutoSpeed),
                    fallback = default.maxAutoSpeed,
                    min = SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED,
                    max = SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
                )
            } else {
                default.maxAutoSpeed
            },
            waitForCueCompletion = if (sharedPreferences.contains(waitForCueCompletionScoped)) {
                sharedPreferences.getBoolean(waitForCueCompletionScoped, default.waitForCueCompletion)
            } else {
                default.waitForCueCompletion
            },
            transliterateEnglishNames = if (sharedPreferences.contains(transliterateScoped)) {
                sharedPreferences.getBoolean(transliterateScoped, default.transliterateEnglishNames)
            } else {
                default.transliterateEnglishNames
            },
        )
    }

    actual fun saveSettings(settings: SubtitleTtsSettings) {
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
        prefs()?.edit()
            ?.putBoolean(ProfileScopedKey.of(enabledKey), settings.enabled)
            ?.putString(ProfileScopedKey.of(engineTypeKey), settings.engineType.name)
            ?.putString(ProfileScopedKey.of(systemEnginePackageKey), settings.systemEnginePackage)
            ?.putString(ProfileScopedKey.of(systemVoiceNameKey), settings.systemVoiceName)
            ?.putFloat(ProfileScopedKey.of(systemPitchKey), settings.systemPitch.coerceIn(0.6f, 1.5f))
            ?.putString(ProfileScopedKey.of(activePiperModelIdKey), settings.activePiperModelId)
            ?.putString(ProfileScopedKey.of(installedPiperModelsKey), modelsJson)
            ?.putString(ProfileScopedKey.of(voiceIdKey), settings.voice.id)
            ?.putFloat(ProfileScopedKey.of(movieVolumeKey), settings.movieVolume.coerceIn(0f, 1f))
            ?.putFloat(ProfileScopedKey.of(ttsVolumeKey), settings.ttsVolume.coerceIn(0f, 1f))
            ?.putFloat(ProfileScopedKey.of(baseSpeedKey), safeBaseSpeed)
            ?.putBoolean(ProfileScopedKey.of(autoSpeedEnabledKey), settings.autoSpeedEnabled)
            ?.putBoolean(ProfileScopedKey.of(hideSubtitlesKey), settings.hideSubtitlesWhenEnabled)
            ?.putFloat(ProfileScopedKey.of(maxAutoSpeedKey), safeMaxAutoSpeed)
            ?.putBoolean(ProfileScopedKey.of(waitForCueCompletionKey), settings.waitForCueCompletion)
            ?.putBoolean(ProfileScopedKey.of(transliterateEnglishNamesKey), settings.transliterateEnglishNames)
            ?.apply()
    }
}
