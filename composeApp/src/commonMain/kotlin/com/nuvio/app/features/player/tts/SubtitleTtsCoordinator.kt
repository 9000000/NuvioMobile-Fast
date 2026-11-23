package com.nuvio.app.features.player.tts

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

internal const val MAX_PRE_SYNTH_CACHE_SIZE = 4
internal const val MAX_LOOKAHEAD_CUES = 2
internal const val MAX_CARRY_OVER_MS = 1500L
internal const val MIN_CARRY_OVER_MS = 120L
internal const val MAX_QUEUE_STALE_MS = 2500L
internal const val SYSTEM_TTS_COMPLETION_GRACE_MS = 350L
internal const val PIPER_SYNTH_AND_PLAY_GRACE_MS = 1200L
internal const val PIPER_PLAYBACK_COMPLETION_GRACE_MS = 120L
internal const val MIN_EXPECTED_SPEECH_MS = 400L

private val monotonicClockOrigin = TimeSource.Monotonic.markNow()

internal fun defaultMonotonicNowMs(): Long =
    monotonicClockOrigin.elapsedNow().inWholeMilliseconds

internal fun quantizeSpeedBucket(speed: Float): Int {
    val safeSpeed = if (speed.isFinite()) speed else SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED
    return (safeSpeed * 20f).roundToInt()
}

internal fun computeSpeechDurationMs(sampleCount: Int, sampleRate: Int): Long {
    if (sampleCount <= 0 || sampleRate <= 0) return 0L
    return (sampleCount.toLong() * 1000L) / sampleRate.toLong()
}

internal data class PcmCacheKey(
    val modelId: String,
    val speechText: String,
    val quantizedSpeedBucket: Int,
)

internal class CachedPcmEntry(
    val samples: FloatArray,
    val speed: Float,
)

/**
 * Thread-safe bounded LRU cache for synthesized Piper PCM chunks.
 */
internal class BoundedLruPcmCache(
    private val maxEntries: Int = MAX_PRE_SYNTH_CACHE_SIZE,
) {
    private val lock = SynchronizedObject()
    private val entries = LinkedHashMap<PcmCacheKey, CachedPcmEntry>()

    fun get(key: PcmCacheKey): CachedPcmEntry? = synchronized(lock) {
        val existing = entries.remove(key) ?: return@synchronized null
        entries[key] = existing
        existing
    }

    fun put(key: PcmCacheKey, entry: CachedPcmEntry) {
        if (maxEntries <= 0) return
        synchronized(lock) {
            entries.remove(key)
            entries[key] = entry
            while (entries.size > maxEntries) {
                val eldestKey = entries.keys.firstOrNull() ?: break
                entries.remove(eldestKey)
            }
        }
    }

    fun containsKey(key: PcmCacheKey): Boolean = synchronized(lock) {
        entries.containsKey(key)
    }

    fun size(): Int = synchronized(lock) {
        entries.size
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}

internal data class PendingCueRequest(
    val rawText: String,
    val cleanText: String,
    val timingContext: SubtitleCuePlaybackContext?,
    val arrivedAtMs: Long,
)

internal sealed interface CueDispatchDecision {
    data class SpeakNow(
        val cleanText: String,
        val speechText: String,
        val timingContext: SubtitleCuePlaybackContext?,
        val budgetMs: Long?,
    ) : CueDispatchDecision

    data class QueuedPending(
        val expectedActiveGen: Long,
        val startCarryOverTimer: Boolean,
        val carryOverDelayMs: Long = MAX_CARRY_OVER_MS,
    ) : CueDispatchDecision

    data object Ignored : CueDispatchDecision
}

internal sealed interface CompletionAdvanceDecision {
    data class SpeakQueued(
        val request: PendingCueRequest,
        val adjustedBudgetMs: Long?,
    ) : CompletionAdvanceDecision

    data object DroppedStale : CompletionAdvanceDecision
    data object Idle : CompletionAdvanceDecision
}

/**
 * Coordinates duplicate filtering, single-slot carry-over queueing, and generation-guarded
 * completion advances for subtitle TTS playback.
 */
internal class SubtitleTtsCueQueueState(
    private val nowMs: () -> Long = ::defaultMonotonicNowMs,
    private val maxQueueStaleMs: Long = MAX_QUEUE_STALE_MS,
) {
    private val lock = SynchronizedObject()

    private var lastActiveRawText: String = ""
    private var lastActiveTimingContext: SubtitleCuePlaybackContext? = null
    private var lastSpokenText: String = ""
    private var lastSpokenCueStartMs: Long? = null
    private var isSpeechActive: Boolean = false
    private var activeExpectedEndAtMs: Long? = null
    private var activePlaybackGen: Long = 0L
    private var pendingCue: PendingCueRequest? = null

    fun recordRawCueWhenDisabled(rawText: String, timingContext: SubtitleCuePlaybackContext?) {
        synchronized(lock) {
            lastActiveRawText = rawText
            lastActiveTimingContext = timingContext
        }
    }

    fun onCueArrived(
        rawText: String,
        timingContext: SubtitleCuePlaybackContext?,
        waitForCueCompletion: Boolean,
    ): CueDispatchDecision = synchronized(lock) {
        lastActiveRawText = rawText
        lastActiveTimingContext = timingContext

        val cleanText = SubtitleTtsTextCleaner.clean(rawText)
        if (cleanText.isBlank()) {
            lastActiveRawText = ""
            lastActiveTimingContext = null
            return@synchronized CueDispatchDecision.Ignored
        }

        val cueStartMs = timingContext?.currentCue?.startMs
        if (cleanText == lastSpokenText && (timingContext == null || cueStartMs == lastSpokenCueStartMs)) {
            return@synchronized CueDispatchDecision.Ignored
        }

        val currentPending = pendingCue
        if (currentPending != null &&
            cleanText == currentPending.cleanText &&
            (timingContext == null || cueStartMs == currentPending.timingContext?.currentCue?.startMs)
        ) {
            return@synchronized CueDispatchDecision.Ignored
        }

        if (waitForCueCompletion && isSpeechActive) {
            val currentNow = nowMs()
            val expectedEnd = activeExpectedEndAtMs
            if (expectedEnd != null && currentNow >= expectedEnd) {
                // Active utterance already exceeded its expected duration (e.g. delayed/dropped onDone
                // or trailing silence only); advance immediately instead of stalling on carry-over.
                isSpeechActive = false
                activeExpectedEndAtMs = null
            } else {
                val hadPending = pendingCue != null
                val carryOverDelayMs = expectedEnd
                    ?.let { (it - currentNow).coerceIn(MIN_CARRY_OVER_MS, MAX_CARRY_OVER_MS) }
                    ?: MAX_CARRY_OVER_MS
                pendingCue = PendingCueRequest(
                    rawText = rawText,
                    cleanText = cleanText,
                    timingContext = timingContext,
                    arrivedAtMs = currentNow,
                )
                return@synchronized CueDispatchDecision.QueuedPending(
                    expectedActiveGen = activePlaybackGen,
                    startCarryOverTimer = !hadPending,
                    carryOverDelayMs = carryOverDelayMs,
                )
            }
        }

        pendingCue = null
        lastSpokenText = cleanText
        lastSpokenCueStartMs = cueStartMs

        CueDispatchDecision.SpeakNow(
            cleanText = cleanText,
            speechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(cleanText),
            timingContext = timingContext,
            budgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(timingContext),
        )
    }

    fun beginPlayback(expectedDurationMs: Long? = null): Long = synchronized(lock) {
        isSpeechActive = true
        activeExpectedEndAtMs = expectedDurationMs?.takeIf { it > 0L }?.let { nowMs() + it }
        ++activePlaybackGen
    }

    fun updateActiveExpectedDuration(gen: Long, expectedDurationMs: Long) {
        if (expectedDurationMs <= 0L) return
        synchronized(lock) {
            if (activePlaybackGen == gen && isSpeechActive) {
                activeExpectedEndAtMs = nowMs() + expectedDurationMs
            }
        }
    }

    fun currentGen(): Long = synchronized(lock) {
        activePlaybackGen
    }

    fun hasActiveSpeech(): Boolean = synchronized(lock) {
        isSpeechActive
    }

    fun hasPendingCue(): Boolean = synchronized(lock) {
        pendingCue != null
    }

    fun clearActiveIfGen(gen: Long) {
        synchronized(lock) {
            if (activePlaybackGen == gen) {
                isSpeechActive = false
                activeExpectedEndAtMs = null
            }
        }
    }

    fun onPlaybackCompleted(gen: Long): CompletionAdvanceDecision = synchronized(lock) {
        if (gen != activePlaybackGen || !isSpeechActive) {
            return@synchronized CompletionAdvanceDecision.Idle
        }
        isSpeechActive = false
        activeExpectedEndAtMs = null
        consumePendingCueLocked()
    }

    fun onCarryOverTimeout(expectedGen: Long): CompletionAdvanceDecision = synchronized(lock) {
        if (expectedGen != activePlaybackGen || !isSpeechActive) {
            return@synchronized CompletionAdvanceDecision.Idle
        }
        isSpeechActive = false
        activeExpectedEndAtMs = null
        activePlaybackGen++
        consumePendingCueLocked()
    }

    private fun consumePendingCueLocked(): CompletionAdvanceDecision {
        val next = pendingCue ?: return CompletionAdvanceDecision.Idle
        pendingCue = null

        val overdueMs = (nowMs() - next.arrivedAtMs).coerceAtLeast(0L)
        if (overdueMs > maxQueueStaleMs) {
            return CompletionAdvanceDecision.DroppedStale
        }

        val originalBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(next.timingContext)
        val adjustedBudgetMs = originalBudgetMs?.let { budget ->
            (budget - overdueMs).coerceAtLeast(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS)
        }

        lastSpokenText = next.cleanText
        lastSpokenCueStartMs = next.timingContext?.currentCue?.startMs

        return CompletionAdvanceDecision.SpeakQueued(
            request = next,
            adjustedBudgetMs = adjustedBudgetMs,
        )
    }

    fun clearPendingQueue() {
        synchronized(lock) {
            pendingCue = null
        }
    }

    fun resetActivePlayback(): Long = synchronized(lock) {
        pendingCue = null
        isSpeechActive = false
        activeExpectedEndAtMs = null
        ++activePlaybackGen
    }

    fun resetAllHistory(): Long = synchronized(lock) {
        lastActiveRawText = ""
        lastActiveTimingContext = null
        lastSpokenText = ""
        lastSpokenCueStartMs = null
        pendingCue = null
        isSpeechActive = false
        activeExpectedEndAtMs = null
        ++activePlaybackGen
    }

    fun clearSpokenHistoryAndSnapshotActive(): Pair<String, SubtitleCuePlaybackContext?> = synchronized(lock) {
        lastSpokenText = ""
        lastSpokenCueStartMs = null
        lastActiveRawText to lastActiveTimingContext
    }
}

internal class CalibratedPcmResult(
    val pcm: FloatArray,
    val usedSpeed: Float,
    val speechMs: Long,
)

/**
 * Resolves Piper PCM for an active cue using cache lookup, matching in-flight pre-synthesis
 * reuse, and at most one duration-recalibration pass.
 */
internal suspend fun synthesizePiperWithCalibration(
    modelId: String,
    sampleRate: Int,
    cleanText: String,
    speechText: String,
    budgetMs: Long?,
    baseSpeed: Float,
    autoSpeedEnabled: Boolean,
    maxAutoSpeed: Float,
    pcmCache: BoundedLruPcmCache,
    awaitInFlight: suspend (PcmCacheKey) -> FloatArray?,
    onCacheMissPrepare: () -> Unit,
    synthesize: suspend (text: String, speed: Float) -> FloatArray?,
): CalibratedPcmResult? {
    val initialSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
        cleanText = cleanText,
        budgetMs = budgetMs,
        baseSpeed = baseSpeed,
        autoSpeedEnabled = autoSpeedEnabled,
        maxAutoSpeed = maxAutoSpeed,
    )
    val initialKey = PcmCacheKey(
        modelId = modelId,
        speechText = speechText,
        quantizedSpeedBucket = quantizeSpeedBucket(initialSpeed),
    )

    var pass1Entry = pcmCache.get(initialKey)
    if (pass1Entry == null) {
        val awaitedPcm = awaitInFlight(initialKey)
        if (awaitedPcm != null && awaitedPcm.isNotEmpty()) {
            val cachedAfterAwait = pcmCache.get(initialKey)
            pass1Entry = if (cachedAfterAwait != null) {
                cachedAfterAwait
            } else {
                CachedPcmEntry(awaitedPcm, initialSpeed).also { pcmCache.put(initialKey, it) }
            }
        }
    }

    if (pass1Entry == null) {
        onCacheMissPrepare()
        // Re-check cache after cancelling non-matching lookahead in case a matching entry finished right before cancel.
        pass1Entry = pcmCache.get(initialKey)
    }

    if (pass1Entry == null) {
        val generated = synthesize(speechText, initialSpeed)
        if (generated != null && generated.isNotEmpty()) {
            pass1Entry = CachedPcmEntry(generated, initialSpeed)
            pcmCache.put(initialKey, pass1Entry)
        }
    }

    val pass1 = pass1Entry ?: return null
    val pass1SpeechMs = computeSpeechDurationMs(pass1.samples.size, sampleRate)

    val recalibratedSpeed = if (budgetMs != null && autoSpeedEnabled) {
        SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
            usedSpeed = pass1.speed,
            actualSpeechMs = pass1SpeechMs,
            budgetMs = budgetMs,
            maxAutoSpeed = maxAutoSpeed,
        )
    } else {
        null
    }

    if (recalibratedSpeed != null) {
        val recalKey = PcmCacheKey(
            modelId = modelId,
            speechText = speechText,
            quantizedSpeedBucket = quantizeSpeedBucket(recalibratedSpeed),
        )
        val cachedRecal = if (recalKey != initialKey) pcmCache.get(recalKey) else null
        val pass2Entry = if (cachedRecal != null) {
            cachedRecal
        } else {
            val recalPcm = synthesize(speechText, recalibratedSpeed)
            if (recalPcm != null && recalPcm.isNotEmpty()) {
                CachedPcmEntry(recalPcm, recalibratedSpeed).also { pcmCache.put(recalKey, it) }
            } else {
                null
            }
        }
        if (pass2Entry != null) {
            return CalibratedPcmResult(
                pcm = pass2Entry.samples,
                usedSpeed = pass2Entry.speed,
                speechMs = computeSpeechDurationMs(pass2Entry.samples.size, sampleRate),
            )
        }
    }

    return CalibratedPcmResult(
        pcm = pass1.samples,
        usedSpeed = pass1.speed,
        speechMs = pass1SpeechMs,
    )
}

/**
 * Prepares spoken clean text and punctuated speech text for synthesis, applying
 * English-to-Vietnamese phonetic transliteration when enabled for Vietnamese TTS targets.
 */
internal fun prepareTtsSpeechText(
    cleanText: String,
    settings: SubtitleTtsSettings,
    availableVoices: List<SystemVoiceInfo> = SystemTtsEngine.availableVoices.value,
    overrideVoiceName: String? = null,
    usePiper: Boolean = settings.engineType == SubtitleTtsEngineType.PIPER_ONNX,
    transliterator: EnglishToVietnameseTransliterator = EnglishToVietnameseTransliterator.Default,
): Pair<String, String> {
    val shouldTransliterate = settings.transliterateEnglishNames && isVietnameseTtsTarget(
        settings = settings,
        availableVoices = availableVoices,
        overrideVoiceName = overrideVoiceName,
        usePiper = usePiper,
    )
    val spokenCleanText = if (shouldTransliterate) {
        transliterator.transliterate(cleanText)
    } else {
        cleanText
    }
    val finalSpeechText = SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation(spokenCleanText)
    return spokenCleanText to finalSpeechText
}

object SubtitleTtsCoordinator {
    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    private val player = SubtitleTtsPlayer()
    private val piperMutex = Mutex()
    private val queueState = SubtitleTtsCueQueueState()
    private val pcmCache = BoundedLruPcmCache(MAX_PRE_SYNTH_CACHE_SIZE)
    private val preSynthLock = SynchronizedObject()
    private val activeSynthesisCount = atomic(0)

    private val _settings = MutableStateFlow(SubtitleTtsSettings())
    val settings: StateFlow<SubtitleTtsSettings> = _settings.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var isLoaded = false
    private var speakJob: Job? = null
    private var lookaheadJob: Job? = null
    private var carryOverTimeoutJob: Job? = null
    private var inFlightPreSynthKey: PcmCacheKey? = null
    private var inFlightPreSynthDeferred: CompletableDeferred<FloatArray?>? = null

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun reconcileInstalledModels(base: SubtitleTtsSettings): SubtitleTtsSettings {
        if (!PiperOnnxEngine.isSupported) return base
        val validSaved = base.installedPiperModels.mapNotNull { saved ->
            if (PiperModelManager.isModelInstalled(saved.id)) {
                PiperModelManager.getInstalledModel(saved.id)?.copy(name = saved.name) ?: saved
            } else {
                null
            }
        }
        val merged = validSaved.toMutableList()
        val seenIds = merged.map { it.id }.toHashSet()
        for (preset in PiperModelManager.presets) {
            if (preset.id !in seenIds && PiperModelManager.isModelInstalled(preset.id)) {
                PiperModelManager.getInstalledModel(preset.id)?.let { diskModel ->
                    merged.add(diskModel)
                    seenIds.add(preset.id)
                }
            }
        }
        val resolvedActiveId = when {
            base.activePiperModelId != null && merged.any { it.id == base.activePiperModelId } -> base.activePiperModelId
            else -> merged.firstOrNull()?.id
        }
        return base.copy(
            installedPiperModels = merged,
            activePiperModelId = resolvedActiveId,
        )
    }

    fun ensureLoaded() {
        if (isLoaded) return
        isLoaded = true
        runCatching {
            val loaded = SubtitleTtsStorage.loadSettings()
            val reconciled = reconcileInstalledModels(loaded)
            _settings.value = reconciled
            if (reconciled != loaded) {
                SubtitleTtsStorage.saveSettings(reconciled)
            }
            SystemTtsEngine.initialize(reconciled.systemEnginePackage)
            TtsPhoneticDictionary.preloadAsync(scope)
        }
    }

    fun refreshInstalledPiperModels() {
        ensureLoaded()
        val current = _settings.value
        val reconciled = reconcileInstalledModels(current)
        if (reconciled != current) {
            _settings.value = reconciled
            runCatching { SubtitleTtsStorage.saveSettings(reconciled) }
        }
    }

    fun updateSettings(transform: (SubtitleTtsSettings) -> SubtitleTtsSettings) {
        ensureLoaded()
        val previous = _settings.value
        val transformed = transform(previous)
        val updated = if (transformed.engineType == SubtitleTtsEngineType.PIPER_ONNX) {
            reconcileInstalledModels(transformed)
        } else {
            transformed
        }
        _settings.value = updated
        runCatching {
            SubtitleTtsStorage.saveSettings(updated)
        }

        if (updated.ttsVolume != previous.ttsVolume) {
            player.setVolume(updated.ttsVolume)
        }

        if (previous.systemEnginePackage != updated.systemEnginePackage) {
            if (updated.systemEnginePackage != null) {
                SystemTtsEngine.setEngine(updated.systemEnginePackage)
            } else {
                SystemTtsEngine.initialize(null)
            }
        }

        if (previous.activePiperModelId != updated.activePiperModelId) {
            pcmCache.clear()
        }

        if (previous.transliterateEnglishNames != updated.transliterateEnglishNames) {
            cancelLookaheadJob()
            pcmCache.clear()
        }

        if (previous.waitForCueCompletion && !updated.waitForCueCompletion) {
            carryOverTimeoutJob?.cancel()
            carryOverTimeoutJob = null
            queueState.clearPendingQueue()
        }

        val engineChanged = previous.engineType != updated.engineType
        val piperModelChanged = updated.engineType == SubtitleTtsEngineType.PIPER_ONNX &&
            previous.activePiperModelId != updated.activePiperModelId

        if (engineChanged || piperModelChanged) {
            _statusMessage.value = null
            stopSpeaking()
        }

        if (previous.engineType == SubtitleTtsEngineType.PIPER_ONNX &&
            updated.engineType == SubtitleTtsEngineType.SYSTEM
        ) {
            scope.launch(Dispatchers.Default) {
                piperMutex.withLock {
                    if (_settings.value.engineType == SubtitleTtsEngineType.SYSTEM) {
                        PiperOnnxEngine.release()
                    }
                }
            }
        }

        if (!updated.enabled && previous.enabled) {
            stopSpeaking()
        } else if (updated.enabled && (!previous.enabled || engineChanged || piperModelChanged)) {
            val (activeRaw, activeContext) = queueState.clearSpokenHistoryAndSnapshotActive()
            if (activeRaw.isNotBlank()) {
                onSubtitleCueChanged(activeRaw, activeContext)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
    }

    private fun resolvePiperModel(
        settings: SubtitleTtsSettings,
        overrideModel: InstalledPiperModel?,
    ): InstalledPiperModel? {
        if (overrideModel != null) {
            return if (PiperModelManager.isModelInstalled(overrideModel.id)) {
                PiperModelManager.getInstalledModel(overrideModel.id)?.copy(name = overrideModel.name) ?: overrideModel
            } else {
                overrideModel
            }
        }

        val fromSettings = settings.installedPiperModels.firstOrNull { it.id == settings.activePiperModelId }
            ?: settings.activePiperModelId?.let { PiperModelManager.getInstalledModel(it) }
            ?: settings.installedPiperModels.firstOrNull()
        if (fromSettings != null && PiperModelManager.isModelInstalled(fromSettings.id)) {
            return fromSettings
        }

        val diskPreset = PiperModelManager.presets.firstNotNullOfOrNull { preset ->
            PiperModelManager.getInstalledModel(preset.id)
        }
        if (diskPreset != null) {
            val synced = reconcileInstalledModels(settings)
            if (synced != _settings.value) {
                _settings.value = synced
                runCatching { SubtitleTtsStorage.saveSettings(synced) }
            }
            return diskPreset
        }

        return fromSettings
    }

    private fun buildPiperFailureMessage(model: InstalledPiperModel): String = when {
        !PiperOnnxEngine.isSupported -> "Thiết bị không hỗ trợ Piper ONNX, đang phát tạm bằng giọng hệ thống."
        !PiperModelManager.isModelInstalled(model.id) -> "Không tìm thấy tệp model ${model.name} trên máy, đang dùng giọng hệ thống."
        else -> "Không thể khởi tạo Piper ONNX (${model.name}), đã chuyển tạm về giọng hệ thống."
    }

    private fun cancelLookaheadJob() {
        lookaheadJob?.cancel()
        lookaheadJob = null
    }

    private fun stopActiveAudioOnly() {
        speakJob?.cancel()
        speakJob = null
        player.stop()
        SystemTtsEngine.stop()
    }

    private fun dispatchPlaybackCompleted(gen: Long) {
        scope.launch {
            handlePlaybackCompleted(gen)
        }
    }

    private fun handlePlaybackCompleted(gen: Long) {
        when (val decision = queueState.onPlaybackCompleted(gen)) {
            CompletionAdvanceDecision.Idle -> Unit
            CompletionAdvanceDecision.DroppedStale -> {
                carryOverTimeoutJob?.cancel()
                carryOverTimeoutJob = null
            }
            is CompletionAdvanceDecision.SpeakQueued -> {
                carryOverTimeoutJob?.cancel()
                carryOverTimeoutJob = null
                val currentSettings = _settings.value
                if (!currentSettings.enabled) return
                startSpeakingCue(
                    cleanText = decision.request.cleanText,
                    timingContext = decision.request.timingContext,
                    budgetMs = decision.adjustedBudgetMs,
                    settings = currentSettings,
                )
            }
        }
    }

    private fun startCarryOverTimer(
        expectedGen: Long,
        delayMs: Long = MAX_CARRY_OVER_MS,
    ) {
        carryOverTimeoutJob?.cancel()
        val effectiveDelayMs = delayMs.coerceIn(MIN_CARRY_OVER_MS, MAX_CARRY_OVER_MS)
        carryOverTimeoutJob = scope.launch {
            delay(effectiveDelayMs)
            carryOverTimeoutJob = null
            when (val decision = queueState.onCarryOverTimeout(expectedGen)) {
                CompletionAdvanceDecision.Idle -> Unit
                CompletionAdvanceDecision.DroppedStale -> {
                    stopActiveAudioOnly()
                }
                is CompletionAdvanceDecision.SpeakQueued -> {
                    stopActiveAudioOnly()
                    val currentSettings = _settings.value
                    if (!currentSettings.enabled) return@launch
                    startSpeakingCue(
                        cleanText = decision.request.cleanText,
                        timingContext = decision.request.timingContext,
                        budgetMs = decision.adjustedBudgetMs,
                        settings = currentSettings,
                    )
                }
            }
        }
    }

    private fun startLookaheadPreSynthesis(
        model: InstalledPiperModel,
        upcomingCues: List<TimedSpeechCue>,
        activeSpeechText: String,
        playbackSpeed: Float,
        settingsSnapshot: SubtitleTtsSettings,
    ) {
        cancelLookaheadJob()
        if (upcomingCues.isEmpty()) return
        val candidates = upcomingCues.take(MAX_LOOKAHEAD_CUES)

        lookaheadJob = scope.launch(Dispatchers.Default) {
            for (cue in candidates) {
                if (!isActive || activeSynthesisCount.value > 0) break
                val latestSettings = _settings.value
                if (!latestSettings.enabled || latestSettings.engineType != SubtitleTtsEngineType.PIPER_ONNX) break
                if (latestSettings.activePiperModelId != null && latestSettings.activePiperModelId != model.id) break

                val uCleanText = SubtitleTtsTextCleaner.clean(cue.rawText)
                if (uCleanText.isBlank()) continue
                val (uSpokenCleanText, uSpeechText) = prepareTtsSpeechText(
                    cleanText = uCleanText,
                    settings = settingsSnapshot,
                    usePiper = true,
                )
                if (uSpeechText.isBlank() || uSpeechText == activeSpeechText) continue

                val uContext = SubtitleCuePlaybackContext(
                    currentCue = cue,
                    upcomingCues = emptyList(),
                    playbackSpeed = playbackSpeed,
                    positionMs = cue.startMs,
                )
                val uBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(uContext)
                val uSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
                    cleanText = uSpokenCleanText,
                    budgetMs = uBudgetMs,
                    baseSpeed = settingsSnapshot.baseSpeed,
                    autoSpeedEnabled = settingsSnapshot.autoSpeedEnabled,
                    maxAutoSpeed = settingsSnapshot.maxAutoSpeed,
                )
                val key = PcmCacheKey(
                    modelId = model.id,
                    speechText = uSpeechText,
                    quantizedSpeedBucket = quantizeSpeedBucket(uSpeed),
                )
                if (pcmCache.containsKey(key)) continue

                val deferred = CompletableDeferred<FloatArray?>()
                synchronized(preSynthLock) {
                    inFlightPreSynthKey = key
                    inFlightPreSynthDeferred = deferred
                }

                var generated: FloatArray? = null
                try {
                    generated = piperMutex.withLock {
                        if (!isActive) return@withLock null
                        val currentLatest = _settings.value
                        if (!currentLatest.enabled || currentLatest.engineType != SubtitleTtsEngineType.PIPER_ONNX) {
                            return@withLock null
                        }
                        if (currentLatest.activePiperModelId != null && currentLatest.activePiperModelId != model.id) {
                            return@withLock null
                        }
                        val loaded = if (!PiperOnnxEngine.isModelLoaded || PiperOnnxEngine.currentLoadedModelId != model.id) {
                            PiperOnnxEngine.loadModel(model)
                        } else {
                            true
                        }
                        if (!loaded || !isActive) return@withLock null
                        PiperOnnxEngine.generatePcm(uSpeechText, uSpeed)
                    }
                    if (generated != null && generated.isNotEmpty()) {
                        pcmCache.put(key, CachedPcmEntry(generated, uSpeed))
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } finally {
                    // Complete deferred and populate cache BEFORE clearing preSynthLock so active cues never miss.
                    if (!deferred.isCompleted) {
                        deferred.complete(generated)
                    }
                    synchronized(preSynthLock) {
                        if (inFlightPreSynthDeferred === deferred) {
                            inFlightPreSynthKey = null
                            inFlightPreSynthDeferred = null
                        }
                    }
                }
            }
        }
    }

    private fun speakWithPiperOrFallback(
        cleanText: String,
        speechText: String,
        model: InstalledPiperModel?,
        budgetMs: Long?,
        baseSpeed: Float,
        autoSpeedEnabled: Boolean,
        maxAutoSpeed: Float,
        volume: Float,
        fallbackVoiceName: String?,
        fallbackPitch: Float,
        requireEnabledCheck: Boolean,
        playbackGen: Long,
        upcomingCues: List<TimedSpeechCue>,
        playbackSpeed: Float,
        settingsSnapshot: SubtitleTtsSettings,
    ) {
        val fallbackSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = cleanText,
            budgetMs = budgetMs,
            baseSpeed = baseSpeed,
            autoSpeedEnabled = autoSpeedEnabled,
            maxAutoSpeed = maxAutoSpeed,
        )

        if (model == null) {
            cancelLookaheadJob()
            _statusMessage.value = "Chưa có giọng Piper AI nào được tải. Đang dùng giọng hệ thống dự phòng."
            val estimatedFallbackMs = (
                SubtitleTtsTimingCalculator.estimateNaturalDurationMs(cleanText).toDouble() /
                    fallbackSpeed.coerceAtLeast(SubtitleTtsTimingCalculator.MIN_BASE_SPEED).toDouble()
                ).toLong()
            queueState.updateActiveExpectedDuration(
                playbackGen,
                (estimatedFallbackMs + SYSTEM_TTS_COMPLETION_GRACE_MS).coerceAtLeast(MIN_EXPECTED_SPEECH_MS),
            )
            SystemTtsEngine.speak(
                text = speechText,
                voiceName = fallbackVoiceName,
                pitch = fallbackPitch,
                speed = fallbackSpeed,
                volume = volume,
                onCompleted = { dispatchPlaybackCompleted(playbackGen) },
            )
            return
        }

        speakJob = scope.launch {
            activeSynthesisCount.incrementAndGet()
            var synthesisCountReleased = false
            var playbackStarted = false
            try {
                val result = synthesizePiperWithCalibration(
                    modelId = model.id,
                    sampleRate = model.sampleRate,
                    cleanText = cleanText,
                    speechText = speechText,
                    budgetMs = budgetMs,
                    baseSpeed = baseSpeed,
                    autoSpeedEnabled = autoSpeedEnabled,
                    maxAutoSpeed = maxAutoSpeed,
                    pcmCache = pcmCache,
                    awaitInFlight = { key ->
                        val matchingDeferred = synchronized(preSynthLock) {
                            if (inFlightPreSynthKey == key) inFlightPreSynthDeferred else null
                        }
                        matchingDeferred?.await()
                    },
                    onCacheMissPrepare = {
                        cancelLookaheadJob()
                    },
                    synthesize = { textToSynth, speed ->
                        withContext(Dispatchers.Default) {
                            piperMutex.withLock {
                                if (!isActive || queueState.currentGen() != playbackGen) {
                                    return@withLock null
                                }
                                val loaded = if (!PiperOnnxEngine.isModelLoaded ||
                                    PiperOnnxEngine.currentLoadedModelId != model.id
                                ) {
                                    PiperOnnxEngine.loadModel(model)
                                } else {
                                    true
                                }
                                if (!loaded || !isActive || queueState.currentGen() != playbackGen) {
                                    return@withLock null
                                }
                                PiperOnnxEngine.generatePcm(textToSynth, speed)
                            }
                        }
                    },
                )

                if (!isActive || queueState.currentGen() != playbackGen) return@launch
                if (requireEnabledCheck && !_settings.value.enabled) return@launch

                if (result != null && result.pcm.isNotEmpty()) {
                    _statusMessage.value = null
                    val tailMs = SubtitleTtsTimingCalculator.computeAdaptiveTailMs(
                        actualSpeechMs = result.speechMs,
                        budgetMs = budgetMs,
                    )
                    queueState.updateActiveExpectedDuration(
                        playbackGen,
                        (result.speechMs + PIPER_PLAYBACK_COMPLETION_GRACE_MS).coerceAtLeast(MIN_EXPECTED_SPEECH_MS),
                    )
                    playbackStarted = true
                    player.playPcm(
                        samples = result.pcm,
                        sampleRate = model.sampleRate,
                        volume = volume,
                        tailMs = tailMs,
                        onCompleted = { dispatchPlaybackCompleted(playbackGen) },
                    )
                    activeSynthesisCount.decrementAndGet()
                    synthesisCountReleased = true

                    if (upcomingCues.isNotEmpty() &&
                        _settings.value.enabled &&
                        _settings.value.engineType == SubtitleTtsEngineType.PIPER_ONNX
                    ) {
                        startLookaheadPreSynthesis(
                            model = model,
                            upcomingCues = upcomingCues,
                            activeSpeechText = speechText,
                            playbackSpeed = playbackSpeed,
                            settingsSnapshot = settingsSnapshot,
                        )
                    }
                } else {
                    cancelLookaheadJob()
                    _statusMessage.value = buildPiperFailureMessage(model)
                    val estimatedFallbackMs = (
                        SubtitleTtsTimingCalculator.estimateNaturalDurationMs(cleanText).toDouble() /
                            fallbackSpeed.coerceAtLeast(SubtitleTtsTimingCalculator.MIN_BASE_SPEED).toDouble()
                        ).toLong()
                    queueState.updateActiveExpectedDuration(
                        playbackGen,
                        (estimatedFallbackMs + SYSTEM_TTS_COMPLETION_GRACE_MS).coerceAtLeast(MIN_EXPECTED_SPEECH_MS),
                    )
                    playbackStarted = true
                    SystemTtsEngine.speak(
                        text = speechText,
                        voiceName = fallbackVoiceName,
                        pitch = fallbackPitch,
                        speed = fallbackSpeed,
                        volume = volume,
                        onCompleted = { dispatchPlaybackCompleted(playbackGen) },
                    )
                }
            } finally {
                if (!synthesisCountReleased) {
                    activeSynthesisCount.decrementAndGet()
                }
                if (!playbackStarted) {
                    queueState.clearActiveIfGen(playbackGen)
                }
            }
        }
    }

    private fun startSpeakingCue(
        cleanText: String,
        timingContext: SubtitleCuePlaybackContext?,
        budgetMs: Long?,
        settings: SubtitleTtsSettings,
    ) {
        val (spokenCleanText, finalSpeechText) = prepareTtsSpeechText(
            cleanText = cleanText,
            settings = settings,
        )
        if (finalSpeechText.isBlank()) return

        val targetSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = spokenCleanText,
            budgetMs = budgetMs,
            baseSpeed = settings.baseSpeed,
            autoSpeedEnabled = settings.autoSpeedEnabled,
            maxAutoSpeed = settings.maxAutoSpeed,
        )
        val estimatedSpeechMs = (
            SubtitleTtsTimingCalculator.estimateNaturalDurationMs(spokenCleanText).toDouble() /
                targetSpeed.coerceAtLeast(SubtitleTtsTimingCalculator.MIN_BASE_SPEED).toDouble()
            ).toLong()
        val initialExpectedDurationMs = when (settings.engineType) {
            SubtitleTtsEngineType.SYSTEM ->
                (estimatedSpeechMs + SYSTEM_TTS_COMPLETION_GRACE_MS).coerceAtLeast(MIN_EXPECTED_SPEECH_MS)
            SubtitleTtsEngineType.PIPER_ONNX ->
                (estimatedSpeechMs + PIPER_SYNTH_AND_PLAY_GRACE_MS).coerceAtLeast(MIN_EXPECTED_SPEECH_MS)
        }
        val playbackGen = queueState.beginPlayback(expectedDurationMs = initialExpectedDurationMs)

        when (settings.engineType) {
            SubtitleTtsEngineType.SYSTEM -> {
                cancelLookaheadJob()
                SystemTtsEngine.speak(
                    text = finalSpeechText,
                    voiceName = settings.systemVoiceName,
                    pitch = settings.systemPitch,
                    speed = targetSpeed,
                    volume = settings.ttsVolume,
                    onCompleted = { dispatchPlaybackCompleted(playbackGen) },
                )
            }
            SubtitleTtsEngineType.PIPER_ONNX -> {
                val model = resolvePiperModel(settings, overrideModel = null)
                speakWithPiperOrFallback(
                    cleanText = spokenCleanText,
                    speechText = finalSpeechText,
                    model = model,
                    budgetMs = budgetMs,
                    baseSpeed = settings.baseSpeed,
                    autoSpeedEnabled = settings.autoSpeedEnabled,
                    maxAutoSpeed = settings.maxAutoSpeed,
                    volume = settings.ttsVolume,
                    fallbackVoiceName = settings.systemVoiceName,
                    fallbackPitch = settings.systemPitch,
                    requireEnabledCheck = true,
                    playbackGen = playbackGen,
                    upcomingCues = timingContext?.upcomingCues.orEmpty(),
                    playbackSpeed = timingContext?.playbackSpeed ?: SubtitleTtsTimingCalculator.DEFAULT_PLAYBACK_SPEED,
                    settingsSnapshot = settings,
                )
            }
        }
    }

    fun onSubtitleCueTextChanged(rawText: String) {
        onSubtitleCueChanged(rawText, timingContext = null)
    }

    fun onSubtitleCueChanged(
        rawText: String,
        timingContext: SubtitleCuePlaybackContext? = null,
    ) {
        ensureLoaded()
        val currentSettings = _settings.value
        if (!currentSettings.enabled) {
            queueState.recordRawCueWhenDisabled(rawText, timingContext)
            return
        }

        when (val decision = queueState.onCueArrived(rawText, timingContext, currentSettings.waitForCueCompletion)) {
            CueDispatchDecision.Ignored -> return
            is CueDispatchDecision.QueuedPending -> {
                if (decision.startCarryOverTimer) {
                    startCarryOverTimer(
                        expectedGen = decision.expectedActiveGen,
                        delayMs = decision.carryOverDelayMs,
                    )
                }
            }
            is CueDispatchDecision.SpeakNow -> {
                carryOverTimeoutJob?.cancel()
                carryOverTimeoutJob = null
                stopActiveAudioOnly()
                startSpeakingCue(
                    cleanText = decision.cleanText,
                    timingContext = decision.timingContext,
                    budgetMs = decision.budgetMs,
                    settings = currentSettings,
                )
            }
        }
    }

    fun onPlaybackPaused() {
        stopSpeaking()
    }

    fun onSeek() {
        carryOverTimeoutJob?.cancel()
        carryOverTimeoutJob = null
        cancelLookaheadJob()
        pcmCache.clear()
        queueState.resetAllHistory()
        stopActiveAudioOnly()
    }

    fun stopSpeaking() {
        carryOverTimeoutJob?.cancel()
        carryOverTimeoutJob = null
        cancelLookaheadJob()
        pcmCache.clear()
        queueState.resetActivePlayback()
        stopActiveAudioOnly()
    }

    const val SAMPLE_TEST_SENTENCE = "chào mừng bạn đến với áp nuvio 9 triệu đây là giọng của tôi"

    fun previewVoice(
        text: String = SAMPLE_TEST_SENTENCE,
        overrideModel: InstalledPiperModel? = null,
        overrideVoiceName: String? = null,
        overridePitch: Float? = null,
    ) {
        ensureLoaded()
        stopSpeaking()

        val current = _settings.value
        val volume = if (current.ttsVolume <= 0.05f) 0.6f else current.ttsVolume.coerceIn(0f, 1f)
        val speed = current.baseSpeed
        val targetVoiceName = overrideVoiceName ?: current.systemVoiceName
        val targetPitch = overridePitch ?: current.systemPitch

        val usePiper = overrideModel != null ||
            (current.engineType == SubtitleTtsEngineType.PIPER_ONNX && overrideVoiceName == null)

        val cleanText = SubtitleTtsTextCleaner.clean(text).ifBlank { text.trim() }
        val (spokenCleanText, speechText) = prepareTtsSpeechText(
            cleanText = cleanText,
            settings = current,
            overrideVoiceName = targetVoiceName,
            usePiper = usePiper,
        )
        if (speechText.isBlank()) return
        val playbackGen = queueState.beginPlayback()

        if (usePiper) {
            val model = resolvePiperModel(current, overrideModel)
            speakWithPiperOrFallback(
                cleanText = spokenCleanText,
                speechText = speechText,
                model = model,
                budgetMs = null,
                baseSpeed = speed,
                autoSpeedEnabled = false,
                maxAutoSpeed = current.maxAutoSpeed,
                volume = volume,
                fallbackVoiceName = targetVoiceName,
                fallbackPitch = targetPitch,
                requireEnabledCheck = false,
                playbackGen = playbackGen,
                upcomingCues = emptyList(),
                playbackSpeed = SubtitleTtsTimingCalculator.DEFAULT_PLAYBACK_SPEED,
                settingsSnapshot = current,
            )
        } else {
            _statusMessage.value = null
            SystemTtsEngine.speak(
                text = speechText,
                voiceName = targetVoiceName,
                pitch = targetPitch,
                speed = speed,
                volume = volume,
                onCompleted = { dispatchPlaybackCompleted(playbackGen) },
            )
        }
    }

    fun releaseSession() {
        carryOverTimeoutJob?.cancel()
        carryOverTimeoutJob = null
        cancelLookaheadJob()
        pcmCache.clear()
        queueState.resetAllHistory()
        speakJob?.cancel()
        speakJob = null
        player.release()
        SystemTtsEngine.stop()
        scope.launch(Dispatchers.Default) {
            piperMutex.withLock {
                PiperOnnxEngine.release()
            }
        }
    }
}
