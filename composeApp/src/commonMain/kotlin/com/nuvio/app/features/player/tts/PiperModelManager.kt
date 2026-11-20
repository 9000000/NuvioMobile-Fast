package com.nuvio.app.features.player.tts

import kotlinx.coroutines.flow.StateFlow

data class PiperPreset(
    val id: String,
    val name: String,
    val onnxUrl: String,
    val jsonUrl: String,
    val sampleRate: Int = 22050,
)

expect object PiperModelManager {
    val presets: List<PiperPreset>
    val downloadProgress: StateFlow<Map<String, Float>>
    val isEspeakDownloading: StateFlow<Boolean>
    val espeakDownloadProgress: StateFlow<Float>

    fun isModelInstalled(modelId: String): Boolean
    fun getInstalledModel(modelId: String): InstalledPiperModel?
    fun downloadPreset(preset: PiperPreset, onComplete: (InstalledPiperModel) -> Unit, onError: (String) -> Unit)
    fun downloadCustomModel(
        id: String,
        name: String,
        onnxUrl: String,
        jsonUrl: String,
        onComplete: (InstalledPiperModel) -> Unit,
        onError: (String) -> Unit,
    )
    fun deleteModel(modelId: String)
    fun getEspeakDir(): String
}
