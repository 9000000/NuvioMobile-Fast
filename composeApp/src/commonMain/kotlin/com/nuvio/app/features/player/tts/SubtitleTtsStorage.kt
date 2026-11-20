package com.nuvio.app.features.player.tts

internal expect object SubtitleTtsStorage {
    fun loadSettings(): SubtitleTtsSettings
    fun saveSettings(settings: SubtitleTtsSettings)
}
