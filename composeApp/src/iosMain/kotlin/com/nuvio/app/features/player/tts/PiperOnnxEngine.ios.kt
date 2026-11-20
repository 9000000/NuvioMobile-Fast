package com.nuvio.app.features.player.tts

actual object PiperOnnxEngine {
    actual val isSupported: Boolean = false
    actual val isModelLoaded: Boolean = false
    actual val currentLoadedModelId: String? = null

    actual fun loadModel(model: InstalledPiperModel): Boolean = false
    actual fun generatePcm(text: String, speed: Float): FloatArray? = null
    actual fun release() {}
}
