package com.nuvio.app.features.player.tts

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual class SubtitleTtsPlayer actual constructor() {
    private var audioPlayer: AVAudioPlayer? = null

    actual fun playPcm(
        samples: FloatArray,
        sampleRate: Int,
        volume: Float,
        tailMs: Int,
        onCompleted: (() -> Unit)?,
    ) {
        // iOS offline PCM stub (AVSpeechSynthesizer handles speech directly on iOS)
    }

    actual fun playMp3(mp3Bytes: ByteArray, volume: Float, speed: Float) {
        if (mp3Bytes.isEmpty()) return
        stop()
        val safeVolume = volume.coerceIn(0f, 1f)
        val safeSpeed = speed.coerceIn(0.7f, 2.0f)

        runCatching {
            val nsData = mp3Bytes.usePinned { pinned ->
                NSData.create(
                    bytes = pinned.addressOf(0),
                    length = mp3Bytes.size.toULong(),
                )
            }
            val player = AVAudioPlayer(data = nsData, error = null)
            player.enableRate = true
            player.volume = safeVolume
            player.rate = safeSpeed
            player.prepareToPlay()
            player.play()
            audioPlayer = player
        }.onFailure {
            stop()
        }
    }

    actual fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        runCatching {
            audioPlayer?.volume = safeVolume
        }
    }

    actual fun stop() {
        val existing = audioPlayer
        audioPlayer = null
        if (existing != null) {
            runCatching {
                existing.stop()
            }
        }
    }

    actual fun release() {
        stop()
    }
}
