package com.nuvio.app.features.player.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

actual object SystemTtsEngine {
    private const val TAG = "SystemTtsEngine"
    private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    private const val DEFAULT_VI_VOICE_ID = "default_vi"

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
    @Volatile private var cachedAndroidVoices: List<Voice> = emptyList()
    private var hasAppliedVoice: Boolean = false
    private var lastAppliedVoiceKey: String? = null
    private var lastAppliedPitch: Float = Float.NaN
    private var lastAppliedSpeed: Float = Float.NaN
    private val utteranceSeq = AtomicLong(0L)
    private val activeUtteranceId = AtomicReference<String?>(null)
    private var activeOnCompleted: (() -> Unit)? = null
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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

    private fun discoverInstalledEngines(context: Context): List<SystemTtsEngineInfo> {
        val pm = context.packageManager
        val discovered = LinkedHashMap<String, SystemTtsEngineInfo>()

        runCatching {
            val services = pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            for (resolveInfo in services) {
                val serviceInfo = resolveInfo.serviceInfo ?: continue
                val pkg = serviceInfo.packageName?.takeIf { it.isNotBlank() } ?: continue
                val label = runCatching {
                    serviceInfo.applicationInfo?.loadLabel(pm)?.toString()?.trim()
                }.getOrNull().orEmpty().ifBlank {
                    runCatching { resolveInfo.loadLabel(pm)?.toString()?.trim() }.getOrNull().orEmpty()
                }.ifBlank { pkg }
                discovered[pkg] = SystemTtsEngineInfo(packageName = pkg, label = label)
            }
        }

        // Fallback direct package probe for Chinese domestic ROMs that restrict queryIntentServices
        if (!discovered.containsKey(GOOGLE_TTS_PACKAGE)) {
            runCatching {
                val appInfo = pm.getApplicationInfo(GOOGLE_TTS_PACKAGE, 0)
                val label = runCatching { pm.getApplicationLabel(appInfo).toString().trim() }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { "Google Speech Services" }
                discovered[GOOGLE_TTS_PACKAGE] = SystemTtsEngineInfo(
                    packageName = GOOGLE_TTS_PACKAGE,
                    label = label,
                )
            }
        }

        return discovered.values.sortedWith(
            compareByDescending<SystemTtsEngineInfo> {
                it.packageName.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true)
            }.thenByDescending {
                it.packageName.contains("google", ignoreCase = true)
            }.thenBy { it.label }
        )
    }

    private fun resolveTargetEnginePackage(
        context: Context,
        preferredEnginePackage: String?,
        discovered: List<SystemTtsEngineInfo>,
    ): String? {
        val explicit = preferredEnginePackage?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit

        // On Chinese domestic ROMs, null binds to OEM Chinese TTS (XiaoAi/Vivo/Oppo/iFlytek) which lacks
        // Google offline voices. Explicitly prefer com.google.android.tts when installed.
        val googleInstalled = discovered.any { it.packageName.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true) }
        if (googleInstalled && fallbackTriedForPackage != GOOGLE_TTS_PACKAGE) {
            return GOOGLE_TTS_PACKAGE
        }
        return null
    }

    actual fun initialize(preferredEnginePackage: String?) {
        var readyToRefresh: TextToSpeech? = null
        synchronized(ttsLock) {
            val context = resolveContext() ?: return
            val discovered = discoverInstalledEngines(context)
            if (discovered.isNotEmpty() && _availableEngines.value.isEmpty()) {
                _availableEngines.value = discovered
            }
            val targetPkg = resolveTargetEnginePackage(context, preferredEnginePackage, discovered)
            if (tts != null && currentEnginePackage == targetPkg && !initFailed) {
                if (isReady && _availableVoices.value.isEmpty()) {
                    readyToRefresh = tts
                } else {
                    return
                }
            } else {
                initializeLocked(preferredEnginePackage)
            }
        }
        readyToRefresh?.let { refreshEnginesAndVoices(it, scheduleRetryIfEmpty = true) }
    }

    private fun initializeLocked(preferredEnginePackage: String?) {
        val context = resolveContext() ?: return
        val discovered = discoverInstalledEngines(context)
        if (discovered.isNotEmpty()) {
            _availableEngines.value = discovered
        }
        val targetPkg = resolveTargetEnginePackage(context, preferredEnginePackage, discovered)
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
                        // Our own stop()/speak() clears activeUtteranceId before invoking TTS.stop()/QUEUE_FLUSH,
                        // so a matching utteranceId here means the OS/TTS service stopped unexpectedly;
                        // fire completion so waitForCueCompletion never gets stuck active.
                        consumeCompletionCallback(utteranceId)
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
            // Warm up Vietnamese locale on cold-bound Google TTS service so offline voice packs mount on Chinese ROMs.
            runCatching { readyInstance!!.setLanguage(Locale("vi", "VN")) }
            refreshEnginesAndVoices(readyInstance!!, scheduleRetryIfEmpty = true)
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

    private fun Voice.isVoiceInstalled(): Boolean =
        features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true

    private fun refreshEnginesAndVoices(
        currentTts: TextToSpeech,
        scheduleRetryIfEmpty: Boolean = false,
    ) {
        runCatching {
            val context = resolveContext()
            val pmEngines = if (context != null) discoverInstalledEngines(context) else emptyList()
            val ttsEngines = runCatching {
                currentTts.engines.map { engineInfo ->
                    SystemTtsEngineInfo(
                        packageName = engineInfo.name,
                        label = engineInfo.label.ifBlank { engineInfo.name },
                    )
                }
            }.getOrDefault(emptyList())

            val mergedEngines = LinkedHashMap<String, SystemTtsEngineInfo>()
            for (e in pmEngines) mergedEngines[e.packageName] = e
            for (e in ttsEngines) mergedEngines[e.packageName] = e

            val engines = mergedEngines.values.sortedWith(
                compareByDescending<SystemTtsEngineInfo> {
                    it.packageName.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true)
                }.thenByDescending {
                    it.packageName.contains("google", ignoreCase = true)
                }.thenBy { it.label }
            )
            if (engines.isNotEmpty()) {
                _availableEngines.value = engines
            }

            val rawVoices = runCatching { currentTts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            cachedAndroidVoices = rawVoices

            // Prefer voices that are actually installed on disk (!KEY_FEATURE_NOT_INSTALLED).
            // On Chinese domestic ROMs without GMS/Play Asset Delivery, Google TTS still lists uninstalled
            // *-local voice stubs with isNetworkConnectionRequired == false + feature "notInstalled".
            val installedRawVoices = rawVoices.filter { it.isVoiceInstalled() }
            val effectiveRawVoices = installedRawVoices.ifEmpty { rawVoices }

            val voices = effectiveRawVoices.mapNotNull { v ->
                val isVi = v.locale.language.equals("vi", ignoreCase = true)
                val isInstalled = v.isVoiceInstalled()
                val isNet = v.isNetworkConnectionRequired
                val netLabel = when {
                    !isInstalled -> " (Chưa tải dữ liệu)"
                    isNet -> " (Online)"
                    else -> " (Offline)"
                }
                val cleanName = v.name
                    .replace("vi-vn-x-", "Giọng ")
                    .replace("-local", "")
                    .replace("-network", "")
                SystemVoiceInfo(
                    name = v.name,
                    displayName = if (isVi) "$cleanName$netLabel" else "${v.locale.displayLanguage} - ${v.name}$netLabel",
                    locale = v.locale.toLanguageTag(),
                    isNetworkRequired = isNet || !isInstalled,
                )
            }.sortedWith(
                compareByDescending<SystemVoiceInfo> {
                    it.locale.startsWith("vi", ignoreCase = true)
                }.thenBy {
                    it.isNetworkRequired
                }.thenBy {
                    it.displayName
                }
            ).toMutableList()

            val hasViVoice = voices.any { it.locale.startsWith("vi", ignoreCase = true) }
            if (!hasViVoice) {
                val viAvailability = runCatching {
                    currentTts.isLanguageAvailable(Locale("vi", "VN"))
                }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (viAvailability >= TextToSpeech.LANG_AVAILABLE) {
                    voices.add(
                        0,
                        SystemVoiceInfo(
                            name = DEFAULT_VI_VOICE_ID,
                            displayName = "Giọng Tiếng Việt mặc định (Offline)",
                            locale = "vi-VN",
                            isNetworkRequired = false,
                        ),
                    )
                }
            }

            _availableVoices.value = voices

            if (scheduleRetryIfEmpty && !hasViVoice) {
                for (delayMs in longArrayOf(400L, 1200L)) {
                    mainHandler.postDelayed({
                        val active = synchronized(ttsLock) { if (isReady) tts else null }
                        if (active === currentTts) {
                            runCatching { active.setLanguage(Locale("vi", "VN")) }
                            refreshEnginesAndVoices(active, scheduleRetryIfEmpty = false)
                        }
                    }, delayMs)
                }
            }
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
        if (text.isBlank()) {
            onCompleted?.invoke()
            return
        }
        val utterance = PendingUtterance(
            text = text,
            voiceName = voiceName,
            pitch = pitch,
            speed = speed,
            volume = volume,
            onCompleted = onCompleted,
        )

        var utteranceIdToSpeak: String? = null
        var failedImmediately = false
        val readyInstance: TextToSpeech? = synchronized(ttsLock) {
            activeUtteranceId.set(null)
            activeOnCompleted = null
            if (tts == null || initFailed) {
                pendingUtterance = utterance
                initializeLocked(currentEnginePackage)
                if (tts == null || initFailed) {
                    pendingUtterance = null
                    failedImmediately = true
                }
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

        if (failedImmediately) {
            onCompleted?.invoke()
            return
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
            val clampedPitch = utterance.pitch.coerceIn(0.6f, 1.5f)
            val clampedSpeed = utterance.speed.coerceIn(0.7f, SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED)
            val desiredVoiceKey = utterance.voiceName?.takeIf { it.isNotBlank() }

            val needsPitch = clampedPitch != lastAppliedPitch
            val needsSpeed = clampedSpeed != lastAppliedSpeed
            val needsVoice = !hasAppliedVoice || desiredVoiceKey != lastAppliedVoiceKey

            if (needsPitch) {
                currentTts.setPitch(clampedPitch)
                lastAppliedPitch = clampedPitch
            }
            if (needsSpeed) {
                currentTts.setSpeechRate(clampedSpeed)
                lastAppliedSpeed = clampedSpeed
            }
            if (needsVoice) {
                val voicesSnapshot = cachedAndroidVoices.ifEmpty {
                    runCatching { currentTts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                        .also { if (it.isNotEmpty()) cachedAndroidVoices = it }
                }
                if (desiredVoiceKey != null && desiredVoiceKey != DEFAULT_VI_VOICE_ID) {
                    val matchedVoice = voicesSnapshot.firstOrNull {
                        it.name == desiredVoiceKey && it.isVoiceInstalled()
                    } ?: voicesSnapshot.firstOrNull {
                        it.name == desiredVoiceKey
                    }
                    if (matchedVoice != null && matchedVoice.isVoiceInstalled()) {
                        currentTts.voice = matchedVoice
                    } else {
                        val targetLocale = matchedVoice?.locale ?: Locale("vi", "VN")
                        currentTts.language = targetLocale
                    }
                } else {
                    val viVoice = voicesSnapshot.firstOrNull {
                        it.locale.language.equals("vi", ignoreCase = true) &&
                            !it.isNetworkConnectionRequired &&
                            it.isVoiceInstalled()
                    } ?: voicesSnapshot.firstOrNull {
                        it.locale.language.equals("vi", ignoreCase = true) &&
                            it.isVoiceInstalled()
                    }
                    if (viVoice != null) {
                        currentTts.voice = viVoice
                    } else {
                        currentTts.language = Locale("vi", "VN")
                    }
                }
                hasAppliedVoice = true
                lastAppliedVoiceKey = desiredVoiceKey
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
        cachedAndroidVoices = emptyList()
        hasAppliedVoice = false
        lastAppliedVoiceKey = null
        lastAppliedPitch = Float.NaN
        lastAppliedSpeed = Float.NaN
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
        val targetPkg = synchronized(ttsLock) { currentEnginePackage } ?: GOOGLE_TTS_PACKAGE
        // On Chinese domestic ROMs, com.android.settings.TTS_SETTINGS often opens the OEM Chinese engine
        // and hides Google TTS voice downloads; prefer opening Google TTS's voice data installer first.
        runCatching {
            val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                setPackage(targetPkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(installIntent)
        }.recoverCatching {
            val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
        }.recoverCatching {
            val genericInstallIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericInstallIntent)
        }
    }
}
