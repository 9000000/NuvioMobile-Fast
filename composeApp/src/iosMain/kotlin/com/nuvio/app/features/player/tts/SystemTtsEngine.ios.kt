package com.nuvio.app.features.player.tts

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.darwin.NSObject

private class SpeechSynthesizerDelegate(
    private val onUtteranceFinished: (AVSpeechUtterance) -> Unit,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance,
    ) {
        onUtteranceFinished(didFinishSpeechUtterance)
    }
}

actual object SystemTtsEngine {
    private val lock = SynchronizedObject()
    private var synthesizer: AVSpeechSynthesizer? = null
    private var synthDelegate: SpeechSynthesizerDelegate? = null
    private var activeUtterance: AVSpeechUtterance? = null
    private var activeOnCompleted: (() -> Unit)? = null

    private val _availableEngines = MutableStateFlow<List<SystemTtsEngineInfo>>(
        listOf(SystemTtsEngineInfo("apple.speech.synthesis", "Apple Speech Synthesis"))
    )
    actual val availableEngines: StateFlow<List<SystemTtsEngineInfo>> = _availableEngines.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<SystemVoiceInfo>>(emptyList())
    actual val availableVoices: StateFlow<List<SystemVoiceInfo>> = _availableVoices.asStateFlow()

    actual val isInitialized: Boolean
        get() = synchronized(lock) { synthesizer != null }

    actual fun initialize(preferredEnginePackage: String?) {
        val created = synchronized(lock) {
            if (synthesizer == null) {
                val synth = AVSpeechSynthesizer()
                val delegate = SpeechSynthesizerDelegate { finishedUtterance ->
                    val callback = synchronized(lock) {
                        if (activeUtterance === finishedUtterance) {
                            activeUtterance = null
                            activeOnCompleted.also { activeOnCompleted = null }
                        } else {
                            null
                        }
                    }
                    callback?.invoke()
                }
                synth.delegate = delegate
                synthDelegate = delegate
                synthesizer = synth
                true
            } else {
                false
            }
        }
        if (created) {
            refreshVoices()
        }
    }

    actual fun setEngine(enginePackage: String) {
        // iOS only has one native synthesis engine
    }

    private fun refreshVoices() {
        val allVoices = AVSpeechSynthesisVoice.speechVoices()
        val list = allVoices.mapNotNull { obj ->
            val v = obj as? AVSpeechSynthesisVoice ?: return@mapNotNull null
            SystemVoiceInfo(
                name = v.identifier,
                displayName = "${v.name} (${v.language})",
                locale = v.language,
                isNetworkRequired = false,
            )
        }.sortedWith(
            compareByDescending<SystemVoiceInfo> {
                it.locale.startsWith("vi", ignoreCase = true)
            }.thenBy { it.displayName }
        )
        _availableVoices.value = list
    }

    actual fun speak(
        text: String,
        voiceName: String?,
        pitch: Float,
        speed: Float,
        volume: Float,
        onCompleted: (() -> Unit)?,
    ) {
        if (text.isBlank()) return
        initialize()
        val currentSynth = synchronized(lock) { synthesizer } ?: return

        val utterance = AVSpeechUtterance(string = text)
        val clampedSpeed = speed.coerceIn(0.5f, SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED)
        val targetRate = if (clampedSpeed <= 1.0f) {
            AVSpeechUtteranceDefaultSpeechRate * clampedSpeed
        } else {
            val range = (SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED - 1.0f).coerceAtLeast(1.0f)
            val fraction = ((clampedSpeed - 1.0f) / range).coerceIn(0f, 1f)
            AVSpeechUtteranceDefaultSpeechRate +
                (AVSpeechUtteranceMaximumSpeechRate - AVSpeechUtteranceDefaultSpeechRate) * fraction
        }
        utterance.pitchMultiplier = pitch.coerceIn(0.6f, 1.5f)
        utterance.rate = targetRate.coerceIn(
            AVSpeechUtteranceMinimumSpeechRate,
            AVSpeechUtteranceMaximumSpeechRate,
        )
        utterance.volume = volume.coerceIn(0f, 1f)

        if (!voiceName.isNullOrBlank()) {
            val voice = AVSpeechSynthesisVoice.voiceWithIdentifier(voiceName)
            if (voice != null) {
                utterance.voice = voice
            }
        } else {
            val viVoice = AVSpeechSynthesisVoice.voiceWithLanguage("vi-VN")
            if (viVoice != null) {
                utterance.voice = viVoice
            }
        }

        val spoke = runCatching {
            synchronized(lock) {
                activeUtterance = utterance
                activeOnCompleted = onCompleted
            }
            currentSynth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
            currentSynth.speakUtterance(utterance)
        }
        if (spoke.isFailure) {
            val callback = synchronized(lock) {
                if (activeUtterance === utterance) {
                    activeUtterance = null
                    activeOnCompleted.also { activeOnCompleted = null }
                } else {
                    null
                }
            }
            callback?.invoke()
        }
    }

    actual fun stop() {
        val currentSynth = synchronized(lock) {
            activeUtterance = null
            activeOnCompleted = null
            synthesizer
        }
        runCatching {
            currentSynth?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
    }

    actual fun release() {
        stop()
        val oldSynth = synchronized(lock) {
            val existing = synthesizer
            synthesizer = null
            synthDelegate = null
            existing
        }
        runCatching {
            oldSynth?.delegate = null
        }
    }

    actual fun openSystemTtsSettings() {
        // No direct deep link to iOS TTS settings
    }
}
