package com.nuvio.app.features.player.tts

expect object PiperOnnxEngine {
    val isSupported: Boolean
    val isModelLoaded: Boolean
    val currentLoadedModelId: String?

    fun loadModel(model: InstalledPiperModel): Boolean
    fun generatePcm(text: String, speed: Float = 1.0f): FloatArray?
    fun release()
}
