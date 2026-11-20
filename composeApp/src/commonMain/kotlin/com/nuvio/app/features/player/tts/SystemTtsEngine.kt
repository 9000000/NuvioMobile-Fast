package com.nuvio.app.features.player.tts

import kotlinx.coroutines.flow.StateFlow

expect object SystemTtsEngine {
    val isInitialized: Boolean
    val availableEngines: StateFlow<List<SystemTtsEngineInfo>>
    val availableVoices: StateFlow<List<SystemVoiceInfo>>

    fun initialize(preferredEnginePackage: String? = null)
    fun setEngine(enginePackage: String)
    fun speak(
        text: String,
        voiceName: String? = null,
        pitch: Float = 1.0f,
        speed: Float = 1.0f,
        volume: Float = 1.0f,
        onCompleted: (() -> Unit)? = null,
    )
    fun stop()
    fun release()
    fun openSystemTtsSettings()
}
