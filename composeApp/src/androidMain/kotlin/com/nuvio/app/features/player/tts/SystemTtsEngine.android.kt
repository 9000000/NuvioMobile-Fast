package com.nuvio.app.features.player.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

actual object SystemTtsEngine {
    private const val TAG = "SystemTtsEngine"

    private data class PendingUtterance(
        val text: String,
        val voiceName: String?,
        val pitch: Float,
        val speed: Float,
        val volume: Float,
        val onCompleted: (() -> Unit)? = null,
    )

    private val ttsLock = Any()
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var isReady: Boolean = false
    @Volatile private var initFailed: Boolean = false
    @Volatile private var currentEnginePackage: String? = null
    @Volatile private var pendingUtterance: PendingUtterance? = null
    @Volatile private var fallbackTriedForPackage: String? = null
    private val utteranceSeq = AtomicLong(0L)
    private val activeUtteranceId = AtomicReference<String?>(null)
    private var activeOnCompleted: (() -> Unit)? = null
    private var appContext: Context? = null

    private val _availableEngines = MutableStateFlow<List<SystemTtsEngineInfo>>(emptyList())
    actual val availableEngines: StateFlow<List<SystemTtsEngineInfo>> = _availableEngines.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<SystemVoiceInfo>>(emptyList())
    actual val availableVoices: StateFlow<List<SystemVoiceInfo>> = _availableVoices.asStateFlow()

    actual val isInitialized: Boolean
        get() = tts != null && isReady

    private fun resolveContext(): Context? {
        appContext?.let { return it }
        val context = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (context != null) {
            appContext = context.applicationContext
        }
        return appContext
    }

    actual fun initialize(preferredEnginePackage: String?) {
        synchronized(ttsLock) {
            initializeLocked(preferredEnginePackage)
        }
    }

    private fun initializeLocked(preferredEnginePackage: String?) {
        val context = resolveContext() ?: return
        val targetPkg = preferredEnginePackage?.takeIf { it.isNotBlank() }
        if (tts != null && currentEnginePackage == targetPkg && !initFailed) {
            return
        }

        releaseInternalLocked(clearPending = false)
        currentEnginePackage = targetPkg
        isReady = false
        initFailed = false

        var createdTts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            onTtsInit(createdTts, targetPkg, status)
        }
        createdTts = runCatching {
            if (targetPkg != null) {
                TextToSpeech(context, listener, targetPkg)
            } else {
                TextToSpeech(context, listener)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed constructing TextToSpeech for engine=$targetPkg", e)
            initFailed = true
        }.getOrNull()
        tts = createdTts
    }

    private fun attachProgressListener(instance: TextToSpeech) {
        runCatching {
            instance.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        consumeCompletionCallback(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        consumeCompletionCallback(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        consumeCompletionCallback(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        clearCompletionIfMatches(utteranceId)
                    }
                },
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed attaching UtteranceProgressListener", e)
        }
    }

    private fun consumeCompletionCallback(utteranceId: String?) {
        if (utteranceId == null) return
        val callback = synchronized(ttsLock) {
            if (activeUtteranceId.compareAndSet(utteranceId, null)) {
                activeOnCompleted.also { activeOnCompleted = null }
            } else {
                null
            }
        }
        callback?.invoke()
    }

    private fun clearCompletionIfMatches(utteranceId: String?) {
        if (utteranceId == null) return
        synchronized(ttsLock) {
            if (activeUtteranceId.compareAndSet(utteranceId, null)) {
                activeOnCompleted = null
            }
        }
    }

    private fun onTtsInit(instance: TextToSpeech?, targetPkg: String?, status: Int) {
        var pendingToSpeak: PendingUtterance? = null
        var pendingUtteranceId: String? = null
        var failedPending: PendingUtterance? = null
        var readyInstance: TextToSpeech? = null
        var shouldFallbackToDefault = false

        synchronized(ttsLock) {
            // Note: createdTts may still be getting assigned if onInit runs synchronously on some OEMs,
            // so treat tts == null during constructor as matching when initFailed is false.
            if (instance != null && tts != null && tts !== instance) {
                return
            }
            val active = instance ?: tts ?: return
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                initFailed = false
                fallbackTriedForPackage = null
                readyInstance = active
                pendingToSpeak = pendingUtterance
                pendingUtterance = null
                if (pendingToSpeak != null) {
                    val id = "nuvio_cue_${utteranceSeq.incrementAndGet()}"
                    activeUtteranceId.set(id)
                    activeOnCompleted = pendingToSpeak!!.onCompleted
                    pendingUtteranceId = id
                }
            } else {
                isReady = false
                initFailed = true
                Log.e(TAG, "TextToSpeech onInit failed: status=$status, engine=$targetPkg")
                if (targetPkg != null && fallbackTriedForPackage != targetPkg) {
                    fallbackTriedForPackage = targetPkg
                    shouldFallbackToDefault = true
                } else {
                    failedPending = pendingUtterance
                    pendingUtterance = null
                    activeUtteranceId.set(null)
                    activeOnCompleted = null
                }
            }
        }

        if (shouldFallbackToDefault) {
            Log.w(TAG, "Falling back to system default TTS engine after failure for $targetPkg")
            initialize(null)
            return
        }

        failedPending?.onCompleted?.invoke()

        if (readyInstance != null) {
            attachProgressListener(readyInstance!!)
            refreshEnginesAndVoices(readyInstance!!)
            if (pendingToSpeak != null && pendingUtteranceId != null) {
                speakInternal(readyInstance!!, pendingToSpeak!!, pendingUtteranceId!!)
            }
        }
    }

    actual fun setEngine(enginePackage: String) {
        val target = enginePackage.takeIf { it.isNotBlank() }
        synchronized(ttsLock) {
            if (currentEnginePackage == target && tts != null && !initFailed) return
            _availableVoices.value = emptyList()
            fallbackTriedForPackage = null
            initializeLocked(target)
        }
    }

    private fun refreshEnginesAndVoices(currentTts: TextToSpeech) {
        runCatching {
            val engines = currentTts.engines.map { engineInfo ->
                SystemTtsEngineInfo(
                    packageName = engineInfo.name,
                    label = engineInfo.label.ifBlank { engineInfo.name },
                )
            }.sortedWith(
                compareByDescending<SystemTtsEngineInfo> {
                    it.packageName.contains("google", ignoreCase = true)
                }.thenBy { it.label }
            )
            _availableEngines.value = engines

            val voices = currentTts.voices?.mapNotNull { v ->
                val isVi = v.locale.language.equals("vi", ignoreCase = true)
                val isNet = v.isNetworkConnectionRequired
                val netLabel = if (isNet) " (Online)" else " (Offline)"
                val cleanName = v.name
                    .replace("vi-vn-x-", "Giọng ")
                    .replace("-local", "")
                    .replace("-network", "")
                SystemVoiceInfo(
                    name = v.name,
                    displayName = if (isVi) "$cleanName$netLabel" else "${v.locale.displayLanguage} - ${v.name}$netLabel",
                    locale = v.locale.toLanguageTag(),
                    isNetworkRequired = isNet,
                )
            }?.sortedWith(
                compareByDescending<SystemVoiceInfo> {
                    it.locale.startsWith("vi", ignoreCase = true)
                }.thenBy {
                    it.isNetworkRequired
                }.thenBy {
                    it.displayName
                }
            ) ?: emptyList()

            _availableVoices.value = voices
        }.onFailure { e ->
            Log.w(TAG, "Failed refreshing TTS engines/voices", e)
        }
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
        val utterance = PendingUtterance(
            text = text,
            voiceName = voiceName,
            pitch = pitch,
            speed = speed,
            volume = volume,
            onCompleted = onCompleted,
        )

        var utteranceIdToSpeak: String? = null
        val readyInstance: TextToSpeech? = synchronized(ttsLock) {
            activeUtteranceId.set(null)
            activeOnCompleted = null
            if (tts == null || initFailed) {
                pendingUtterance = utterance
                initializeLocked(currentEnginePackage)
                null
            } else if (!isReady) {
                // TextToSpeech is still binding asynchronously; buffer until onInit(SUCCESS)
                pendingUtterance = utterance
                null
            } else {
                pendingUtterance = null
                val id = "nuvio_cue_${utteranceSeq.incrementAndGet()}"
                activeUtteranceId.set(id)
                activeOnCompleted = utterance.onCompleted
                utteranceIdToSpeak = id
                tts
            }
        }

        if (readyInstance != null && utteranceIdToSpeak != null) {
            speakInternal(readyInstance, utterance, utteranceIdToSpeak!!)
        }
    }

    private fun speakInternal(
        currentTts: TextToSpeech,
        utterance: PendingUtterance,
        utteranceId: String,
    ) {
        runCatching {
            if (activeUtteranceId.get() != utteranceId) return
            currentTts.setPitch(utterance.pitch.coerceIn(0.6f, 1.5f))
            currentTts.setSpeechRate(utterance.speed.coerceIn(0.7f, SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED))

            val voicesSnapshot = runCatching { currentTts.voices }.getOrNull()
            if (!utterance.voiceName.isNullOrBlank()) {
                val matchedVoice = voicesSnapshot?.firstOrNull { it.name == utterance.voiceName }
                if (matchedVoice != null) {
                    currentTts.voice = matchedVoice
                } else {
                    currentTts.language = Locale("vi", "VN")
                }
            } else {
                val viVoice = voicesSnapshot?.firstOrNull {
                    it.locale.language.equals("vi", ignoreCase = true) && !it.isNetworkConnectionRequired
                } ?: voicesSnapshot?.firstOrNull {
                    it.locale.language.equals("vi", ignoreCase = true)
                }
                if (viVoice != null) {
                    currentTts.voice = viVoice
                } else {
                    currentTts.language = Locale("vi", "VN")
                }
            }

            if (activeUtteranceId.get() != utteranceId) return

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, utterance.volume.coerceIn(0f, 1f))
            }
            val result = currentTts.speak(
                utterance.text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId,
            )
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "TextToSpeech.speak returned ERROR for engine=$currentEnginePackage")
                consumeCompletionCallback(utteranceId)
            }
        }.onFailure { e ->
            Log.e(TAG, "SystemTtsEngine speak failed", e)
            consumeCompletionCallback(utteranceId)
        }
    }

    actual fun stop() {
        val readyInstance = synchronized(ttsLock) {
            pendingUtterance = null
            activeUtteranceId.set(null)
            activeOnCompleted = null
            if (isReady) tts else null
        }
        runCatching {
            readyInstance?.stop()
        }
    }

    private fun releaseInternalLocked(clearPending: Boolean) {
        activeUtteranceId.set(null)
        activeOnCompleted = null
        if (clearPending) {
            pendingUtterance = null
        }
        val existing = tts
        val wasReady = isReady
        tts = null
        isReady = false
        if (existing != null) {
            if (wasReady) {
                runCatching { existing.stop() }
            }
            runCatching { existing.shutdown() }
        }
    }

    actual fun release() {
        synchronized(ttsLock) {
            releaseInternalLocked(clearPending = true)
            initFailed = false
        }
    }

    actual fun openSystemTtsSettings() {
        val context = resolveContext() ?: return
        runCatching {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }.recoverCatching {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
