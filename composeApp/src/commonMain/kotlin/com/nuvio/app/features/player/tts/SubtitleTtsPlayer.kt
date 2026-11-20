package com.nuvio.app.features.player.tts

internal expect class SubtitleTtsPlayer() {
    fun playPcm(
        samples: FloatArray,
        sampleRate: Int = 22050,
        volume: Float = 1.0f,
        tailMs: Int = SubtitleTtsTimingCalculator.DEFAULT_TAIL_MS,
        onCompleted: (() -> Unit)? = null,
    )
    fun playMp3(mp3Bytes: ByteArray, volume: Float = 1.0f, speed: Float = 1.0f)
    fun setVolume(volume: Float)
    fun stop()
    fun release()
}
