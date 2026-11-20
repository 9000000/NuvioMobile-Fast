package com.nuvio.app.features.player.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal fun padPcmWithSilence(
    samples: FloatArray,
    sampleRate: Int,
    leadMs: Int = 40,
    tailMs: Int = SubtitleTtsTimingCalculator.DEFAULT_TAIL_MS,
    fadeOutMs: Int = 12,
): FloatArray {
    if (samples.isEmpty() || sampleRate <= 0) return samples
    val safeLeadMs = leadMs.coerceAtLeast(0)
    val safeTailMs = tailMs.coerceAtLeast(0)
    val safeFadeOutMs = fadeOutMs.coerceAtLeast(0)
    val leadCount = ((sampleRate.toLong() * safeLeadMs) / 1000L).toInt()
    val tailCount = ((sampleRate.toLong() * safeTailMs) / 1000L).toInt()
    val fadeCount = minOf(((sampleRate.toLong() * safeFadeOutMs) / 1000L).toInt(), samples.size)

    val padded = FloatArray(leadCount + samples.size + tailCount)
    samples.copyInto(padded, destinationOffset = leadCount)

    // Smooth linear fade-out on the last ~12ms of speech to eliminate abrupt cutoffs/DC clicks.
    if (fadeCount > 0) {
        val fadeStart = leadCount + samples.size - fadeCount
        for (i in 0 until fadeCount) {
            val factor = 1f - ((i + 1).toFloat() / fadeCount.toFloat())
            padded[fadeStart + i] *= factor
        }
    }
    return padded
}

internal actual class SubtitleTtsPlayer actual constructor() {
    private val stateLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var currentVolume: Float = 1.0f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playJob: Job? = null
    private val isPlayingPcm = AtomicBoolean(false)
    private val playbackGeneration = AtomicLong(0L)

    actual fun playPcm(
        samples: FloatArray,
        sampleRate: Int,
        volume: Float,
        tailMs: Int,
        onCompleted: (() -> Unit)?,
    ) {
        if (samples.isEmpty()) return
        stop()
        val generation = playbackGeneration.incrementAndGet()
        val safeVolume = volume.coerceIn(0f, 1f)
        synchronized(stateLock) {
            currentVolume = safeVolume
        }
        val paddedSamples = padPcmWithSilence(samples, sampleRate, tailMs = tailMs)

        runCatching {
            val useFloatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            val encoding = if (useFloatPcm) {
                AudioFormat.ENCODING_PCM_FLOAT
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            val bytesPerSample = if (useFloatPcm) 4 else 2
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                encoding,
            ).coerceAtLeast(sampleRate * bytesPerSample / 10)
            // Keep a bounded streaming ring buffer (~0.5s) to avoid AudioFlinger shared-memory overflow
            // while streaming all padded samples cleanly with WRITE_BLOCKING.
            val bufferSize = maxOf(minBufferSize * 4, (sampleRate * bytesPerSample) / 2)

            val track = if (useFloatPcm) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                )
            }

            track.setVolume(safeVolume)
            track.play()

            val attached = synchronized(stateLock) {
                if (playbackGeneration.get() != generation) {
                    false
                } else {
                    audioTrack = track
                    isPlayingPcm.set(true)
                    true
                }
            }
            if (!attached) {
                runCatching { track.stop() }
                runCatching { track.release() }
                return
            }

            val job = scope.launch {
                var completedNormally = false
                try {
                    var writeFailed = false
                    if (useFloatPcm) {
                        var offset = 0
                        while (isActive && playbackGeneration.get() == generation && offset < paddedSamples.size) {
                            val written = track.write(
                                paddedSamples,
                                offset,
                                paddedSamples.size - offset,
                                AudioTrack.WRITE_BLOCKING,
                            )
                            if (written <= 0) {
                                writeFailed = true
                                break
                            }
                            offset += written
                        }
                    } else {
                        val pcm16 = ShortArray(paddedSamples.size) { idx ->
                            (paddedSamples[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        }
                        var offset = 0
                        while (isActive && playbackGeneration.get() == generation && offset < pcm16.size) {
                            val written = track.write(pcm16, offset, pcm16.size - offset)
                            if (written <= 0) {
                                writeFailed = true
                                break
                            }
                            offset += written
                        }
                    }

                    // Drain remaining queued frames (including trailing silence) cleanly to the end.
                    if (!writeFailed &&
                        isActive &&
                        playbackGeneration.get() == generation &&
                        synchronized(stateLock) { audioTrack === track }
                    ) {
                        runCatching { track.stop() }
                        val waitStartMs = System.currentTimeMillis()
                        val maxDrainWaitMs = maxOf(900L, tailMs.coerceAtLeast(0).toLong() + 500L)
                        while (isActive &&
                            playbackGeneration.get() == generation &&
                            synchronized(stateLock) { audioTrack === track }
                        ) {
                            val headPos = runCatching { track.playbackHeadPosition }.getOrDefault(paddedSamples.size)
                            if (headPos >= paddedSamples.size - 128) break
                            if (System.currentTimeMillis() - waitStartMs >= maxDrainWaitMs) break
                            delay(35L)
                        }
                        if (isActive &&
                            playbackGeneration.get() == generation &&
                            synchronized(stateLock) { audioTrack === track }
                        ) {
                            completedNormally = true
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    completedNormally = false
                } finally {
                    val shouldReleaseTrack = synchronized(stateLock) {
                        if (audioTrack === track) {
                            audioTrack = null
                            isPlayingPcm.set(false)
                            true
                        } else {
                            false
                        }
                    }
                    if (shouldReleaseTrack) {
                        runCatching { track.release() }
                    }
                    if (completedNormally && playbackGeneration.get() == generation) {
                        postCompletion(generation, onCompleted)
                    }
                }
            }
            synchronized(stateLock) {
                if (playbackGeneration.get() == generation && audioTrack === track) {
                    playJob = job
                } else {
                    job.cancel()
                }
            }
        }.onFailure {
            stop()
        }
    }

    private fun postCompletion(generation: Long, onCompleted: (() -> Unit)?) {
        if (onCompleted == null || playbackGeneration.get() != generation) return
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        if (mainLooper != null) {
            Handler(mainLooper).post {
                if (playbackGeneration.get() == generation) {
                    onCompleted()
                }
            }
        } else {
            scope.launch {
                if (playbackGeneration.get() == generation) {
                    onCompleted()
                }
            }
        }
    }

    actual fun playMp3(mp3Bytes: ByteArray, volume: Float, speed: Float) {
        if (mp3Bytes.isEmpty()) return
        stop()
        val safeVolume = volume.coerceIn(0f, 1f)
        val safeSpeed = speed.coerceIn(0.7f, 2.0f)
        synchronized(stateLock) {
            currentVolume = safeVolume
        }

        runCatching {
            val tmpDir = System.getProperty("java.io.tmpdir")?.let(::File)
            val tempFile = File(tmpDir, "nuvio_tts_cue.mp3")
            tempFile.writeBytes(mp3Bytes)

            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            FileInputStream(tempFile).use { fis ->
                player.setDataSource(fis.fd)
            }
            player.prepare()
            player.setVolume(safeVolume, safeVolume)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching {
                    player.playbackParams = player.playbackParams.setSpeed(safeSpeed)
                }
            }
            player.setOnCompletionListener { mp ->
                runCatching {
                    mp.release()
                }
                synchronized(stateLock) {
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                }
            }
            synchronized(stateLock) {
                mediaPlayer = player
            }
            if (!player.isPlaying) {
                player.start()
            }
        }.onFailure {
            stop()
        }
    }

    actual fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        val (currentMp, currentTrack) = synchronized(stateLock) {
            currentVolume = safeVolume
            mediaPlayer to audioTrack
        }
        runCatching {
            currentMp?.setVolume(safeVolume, safeVolume)
        }
        runCatching {
            currentTrack?.setVolume(safeVolume)
        }
    }

    actual fun stop() {
        playbackGeneration.incrementAndGet()
        val existingJob: Job?
        val existingTrack: AudioTrack?
        val existingPlayer: MediaPlayer?
        synchronized(stateLock) {
            existingJob = playJob
            playJob = null
            isPlayingPcm.set(false)
            existingTrack = audioTrack
            audioTrack = null
            existingPlayer = mediaPlayer
            mediaPlayer = null
        }

        existingJob?.cancel()

        if (existingTrack != null) {
            runCatching {
                if (existingTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    existingTrack.pause()
                    existingTrack.flush()
                }
                existingTrack.stop()
            }
            runCatching {
                existingTrack.release()
            }
        }

        if (existingPlayer != null) {
            runCatching {
                if (existingPlayer.isPlaying) {
                    existingPlayer.stop()
                }
            }
            runCatching {
                existingPlayer.reset()
                existingPlayer.release()
            }
        }
    }

    actual fun release() {
        stop()
    }
}
