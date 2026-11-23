package com.nuvio.app.features.player.tts

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleTtsCoordinatorTimingTest {

    @Test
    fun quantizeSpeedBucketBucketsInFiveHundredthSteps() {
        assertEquals(20, quantizeSpeedBucket(1.00f))
        assertEquals(20, quantizeSpeedBucket(1.02f))
        assertEquals(21, quantizeSpeedBucket(1.05f))
        assertEquals(42, quantizeSpeedBucket(2.10f))
        assertEquals(20, quantizeSpeedBucket(Float.NaN))
    }

    @Test
    fun boundedLruCacheEvictsLeastRecentlyUsedEntryBeyondMaxEntries() {
        val cache = BoundedLruPcmCache(maxEntries = 4)
        val k1 = PcmCacheKey("m1", "Câu một.", 20)
        val k2 = PcmCacheKey("m1", "Câu hai.", 20)
        val k3 = PcmCacheKey("m1", "Câu ba.", 20)
        val k4 = PcmCacheKey("m1", "Câu bốn.", 20)
        val k5 = PcmCacheKey("m1", "Câu năm.", 20)

        cache.put(k1, CachedPcmEntry(floatArrayOf(0.1f), 1.0f))
        cache.put(k2, CachedPcmEntry(floatArrayOf(0.2f), 1.0f))
        cache.put(k3, CachedPcmEntry(floatArrayOf(0.3f), 1.0f))
        cache.put(k4, CachedPcmEntry(floatArrayOf(0.4f), 1.0f))
        assertEquals(4, cache.size())

        // Touch k1 so k2 becomes the least-recently-used entry.
        assertNotNull(cache.get(k1))

        cache.put(k5, CachedPcmEntry(floatArrayOf(0.5f), 1.0f))
        assertEquals(4, cache.size())
        assertTrue(cache.containsKey(k1))
        assertFalse(cache.containsKey(k2))
        assertTrue(cache.containsKey(k3))
        assertTrue(cache.containsKey(k4))
        assertTrue(cache.containsKey(k5))

        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun synthesizePiperWithCalibrationRecalibratesAtMostOnce() = runTest {
        val cache = BoundedLruPcmCache(maxEntries = 4)
        val recordedSpeeds = mutableListOf<Float>()
        var cancelLookaheadCalls = 0

        val result = synthesizePiperWithCalibration(
            modelId = "vi_model",
            sampleRate = 1000,
            cleanText = "Xin chào mọi người",
            speechText = "Xin chào mọi người.",
            budgetMs = 1_500L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
            pcmCache = cache,
            awaitInFlight = { null },
            onCacheMissPrepare = { cancelLookaheadCalls++ },
            synthesize = { _, speed ->
                recordedSpeeds.add(speed)
                // Pass 1 returns 3000ms (triggers recalibration); Pass 2 still exceeds budget (1900ms)
                // to prove at most 1 recalibration pass ever executes.
                if (recordedSpeeds.size == 1) FloatArray(3_000) { 0.5f } else FloatArray(1_900) { 0.25f }
            },
        )

        assertNotNull(result)
        assertEquals(2, recordedSpeeds.size)
        assertEquals(1, cancelLookaheadCalls)
        assertTrue(recordedSpeeds[1] > recordedSpeeds[0])
        assertEquals(1_900L, result.speechMs)
        assertEquals(0.25f, result.pcm[0], 0.001f)
    }

    @Test
    fun synthesizePiperWithCalibrationReusesCacheHitWhenWithinBudgetAndRecalibratesIfOverTightBudget() = runTest {
        val cache = BoundedLruPcmCache(maxEntries = 4)
        val cleanText = "Đi thôi"
        val speechText = "Đi thôi."
        val initialSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = cleanText,
            budgetMs = 1_500L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        val key = PcmCacheKey("vi_model", speechText, quantizeSpeedBucket(initialSpeed))
        cache.put(key, CachedPcmEntry(FloatArray(1_200) { 0.4f }, initialSpeed))

        var synthCalls = 0
        var cancelCalls = 0
        val hitResult = synthesizePiperWithCalibration(
            modelId = "vi_model",
            sampleRate = 1000,
            cleanText = cleanText,
            speechText = speechText,
            budgetMs = 1_500L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
            pcmCache = cache,
            awaitInFlight = { null },
            onCacheMissPrepare = { cancelCalls++ },
            synthesize = { _, _ ->
                synthCalls++
                FloatArray(500)
            },
        )

        assertNotNull(hitResult)
        assertEquals(0, synthCalls)
        assertEquals(0, cancelCalls)
        assertEquals(1_200L, hitResult.speechMs)

        // Now tighten budget so the cached 1200ms entry exceeds budgetMs + tolerance and triggers Pass 2 once.
        val recalResult = synthesizePiperWithCalibration(
            modelId = "vi_model",
            sampleRate = 1000,
            cleanText = cleanText,
            speechText = speechText,
            budgetMs = 600L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
            pcmCache = cache,
            awaitInFlight = { null },
            onCacheMissPrepare = { cancelCalls++ },
            synthesize = { _, _ ->
                synthCalls++
                FloatArray(580) { 0.9f }
            },
        )

        assertNotNull(recalResult)
        assertEquals(1, synthCalls)
        assertEquals(580L, recalResult.speechMs)
    }

    @Test
    fun synthesizePiperWithCalibrationAwaitsMatchingInFlightPreSynthWithoutCancelling() = runTest {
        val cache = BoundedLruPcmCache(maxEntries = 4)
        val cleanText = "Xin chào"
        val speechText = "Xin chào."
        val initialSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = cleanText,
            budgetMs = 2_000L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        val expectedKey = PcmCacheKey("vi_model", speechText, quantizeSpeedBucket(initialSpeed))

        var cancelCalls = 0
        var synthCalls = 0
        val result = synthesizePiperWithCalibration(
            modelId = "vi_model",
            sampleRate = 1000,
            cleanText = cleanText,
            speechText = speechText,
            budgetMs = 2_000L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
            pcmCache = cache,
            awaitInFlight = { key ->
                if (key == expectedKey) FloatArray(900) { 0.7f } else null
            },
            onCacheMissPrepare = { cancelCalls++ },
            synthesize = { _, _ ->
                synthCalls++
                FloatArray(900)
            },
        )

        assertNotNull(result)
        assertEquals(0, cancelCalls)
        assertEquals(0, synthCalls)
        assertEquals(900L, result.speechMs)
        assertTrue(cache.containsKey(expectedKey))
    }

    @Test
    fun cueQueueImmediateModeDeduplicatesSameCueAndAllowsRepeatedTextAtNewStartMs() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val ctx1 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Gì cơ?", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_500L),
        )
        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Gì cơ?", ctx1, waitForCueCompletion = false),
        )
        queue.beginPlayback()

        // Same text and same startMs -> duplicate poll ignored.
        assertIs<CueDispatchDecision.Ignored>(
            queue.onCueArrived("Gì cơ?", ctx1.copy(positionMs = 1_200L), waitForCueCompletion = false),
        )

        // Same dialogue occurring again at a later timestamp -> spoken.
        val ctx2 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Gì cơ?", startMs = 5_000L, endMs = 6_000L, nextCueStartMs = 6_500L),
        )
        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Gì cơ?", ctx2, waitForCueCompletion = false),
        )

        // After resetAllHistory (seek), same cue can be spoken again.
        queue.resetAllHistory()
        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Gì cơ?", ctx2, waitForCueCompletion = false),
        )
    }

    @Test
    fun cueQueueWaitForCompletionOverwritesSinglePendingSlotAndAdjustsBudgetByOverdueMs() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        val cue1 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu một", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_100L),
        )
        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu một", cue1, waitForCueCompletion = true),
        )
        val gen1 = queue.beginPlayback()
        assertTrue(queue.hasActiveSpeech())

        // Cue 2 arrives at t=1500 while Cue 1 is still active -> queued and starts carry-over timer.
        nowMs = 1_500L
        val cue2 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu hai", startMs = 2_100L, endMs = 3_000L, nextCueStartMs = 3_200L),
        )
        val q2 = assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu hai", cue2, waitForCueCompletion = true),
        )
        assertEquals(gen1, q2.expectedActiveGen)
        assertTrue(q2.startCarryOverTimer)

        // Duplicate emission of Cue 2 or active Cue 1 must not reset arrival time or overwrite pending slot.
        nowMs = 1_650L
        assertIs<CueDispatchDecision.Ignored>(
            queue.onCueArrived("Câu hai", cue2, waitForCueCompletion = true),
        )
        assertIs<CueDispatchDecision.Ignored>(
            queue.onCueArrived("Câu một", cue1, waitForCueCompletion = true),
        )

        // Cue 3 arrives at t=1800 -> overwrites Cue 2 cleanly without re-starting carry-over timer.
        nowMs = 1_800L
        val cue3 = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu ba", startMs = 3_200L, endMs = 5_000L, nextCueStartMs = 5_200L),
        )
        val q3 = assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu ba", cue3, waitForCueCompletion = true),
        )
        assertFalse(q3.startCarryOverTimer)

        // Stale completion callback for an older generation is ignored.
        assertIs<CompletionAdvanceDecision.Idle>(queue.onPlaybackCompleted(gen1 - 1))
        assertTrue(queue.hasPendingCue())

        // Active Cue 1 completes at t=2300 (overdueMs = 2300 - 1800 = 500ms).
        // Original budget for Cue 3 = (5200 - 3200) - 80 = 1920ms; adjusted = 1920 - 500 = 1420ms.
        nowMs = 2_300L
        val advance = assertIs<CompletionAdvanceDecision.SpeakQueued>(
            queue.onPlaybackCompleted(gen1),
        )
        assertEquals("Câu ba", advance.request.cleanText)
        assertEquals(1_420L, advance.adjustedBudgetMs)
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
    }

    @Test
    fun cueQueueDropsStaleQueuedCueBeyondMaxQueueStaleMs() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu đầu", null, waitForCueCompletion = true),
        )
        val gen1 = queue.beginPlayback()

        nowMs = 100L
        assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu trễ", null, waitForCueCompletion = true),
        )

        nowMs = 100L + MAX_QUEUE_STALE_MS + 1L
        assertIs<CompletionAdvanceDecision.DroppedStale>(
            queue.onPlaybackCompleted(gen1),
        )
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
    }

    @Test
    fun cueQueueCarryOverTimeoutAdvancesPendingAndInvalidatesStaleCompletionCallback() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu dài", null, waitForCueCompletion = true),
        )
        val gen1 = queue.beginPlayback()

        nowMs = 100L
        val pendingCtx = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue("Câu tiếp", startMs = 2_000L, endMs = 2_300L, nextCueStartMs = 2_400L),
        )
        assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu tiếp", pendingCtx, waitForCueCompletion = true),
        )

        // Carry-over timer fires after 1500ms -> overdueMs = 1500ms; budget clamped to MIN_SPEECH_BUDGET_MS (250ms).
        nowMs = 100L + MAX_CARRY_OVER_MS
        val timeoutAdvance = assertIs<CompletionAdvanceDecision.SpeakQueued>(
            queue.onCarryOverTimeout(gen1),
        )
        assertEquals("Câu tiếp", timeoutAdvance.request.cleanText)
        assertEquals(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS, timeoutAdvance.adjustedBudgetMs)

        // Late onCompleted from cancelled gen1 must be ignored.
        assertIs<CompletionAdvanceDecision.Idle>(queue.onPlaybackCompleted(gen1))
    }

    @Test
    fun cueQueueResetClearsPendingAndInvalidatesActiveGeneration() {
        var nowMs = 0L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu một", null, waitForCueCompletion = true),
        )
        val gen1 = queue.beginPlayback()

        nowMs = 200L
        assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu hai", null, waitForCueCompletion = true),
        )
        assertTrue(queue.hasPendingCue())

        queue.resetActivePlayback()
        assertFalse(queue.hasPendingCue())
        assertFalse(queue.hasActiveSpeech())
        assertIs<CompletionAdvanceDecision.Idle>(queue.onPlaybackCompleted(gen1))
    }

    @Test
    fun cueQueueSpeaksImmediatelyWhenActiveCueAlreadyExceededExpectedDuration() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu ngắn", null, waitForCueCompletion = true),
        )
        queue.beginPlayback(expectedDurationMs = 900L)

        // Next cue arrives well after expected completion (e.g. delayed/dropped onDone callback).
        // Must speak immediately instead of queuing for MAX_CARRY_OVER_MS.
        nowMs = 2_200L
        val decision = assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu tiếp theo", null, waitForCueCompletion = true),
        )
        assertEquals("Câu tiếp theo", decision.cleanText)
        assertFalse(queue.hasPendingCue())
    }

    @Test
    fun cueQueueUsesDynamicCarryOverDelayBoundedByRemainingExpectedDuration() {
        var nowMs = 1_000L
        val queue = SubtitleTtsCueQueueState(nowMs = { nowMs })

        assertIs<CueDispatchDecision.SpeakNow>(
            queue.onCueArrived("Câu một", null, waitForCueCompletion = true),
        )
        val gen1 = queue.beginPlayback(expectedDurationMs = 2_000L)

        // Update with actual synthesized speech duration (800ms) at t=1100 -> expected end at t=1900.
        nowMs = 1_100L
        queue.updateActiveExpectedDuration(gen1, expectedDurationMs = 800L)

        // Next cue arrives at t=1600 -> only 300ms remaining, so carry-over timer should be 300ms (not 1500ms).
        nowMs = 1_600L
        val queued = assertIs<CueDispatchDecision.QueuedPending>(
            queue.onCueArrived("Câu hai", null, waitForCueCompletion = true),
        )
        assertTrue(queued.startCarryOverTimer)
        assertEquals(300L, queued.carryOverDelayMs)
    }

    @Test
    fun coordinatorExposesCrossAgentContractMethodsWhenDisabled() {
        val context = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue(
                rawText = "Xin chào",
                startMs = 1_000L,
                endMs = 2_500L,
                nextCueStartMs = 3_000L,
            ),
            upcomingCues = listOf(
                TimedSpeechCue(rawText = "Hẹn gặp lại", startMs = 3_000L, endMs = 4_500L),
            ),
        )
        // Ensures both signatures match the exact Stage 2 cross-agent contract.
        SubtitleTtsCoordinator.onSubtitleCueTextChanged("Xin chào")
        SubtitleTtsCoordinator.onSubtitleCueChanged("Xin chào", timingContext = context)
        assertNull(SubtitleTtsCoordinator.statusMessage.value)
    }

    @Test
    fun prepareTtsSpeechTextTransliteratesForVietnameseTargetsUpdatesTimingAndAlignsLookaheadCacheKey() {
        val dict = TtsPhoneticDictionary.parseFromText(
            """
            christopher	Crít xtô phơ
            tony stark	Tô ni Xtác
            """.trimIndent(),
        )
        val transliterator = EnglishToVietnameseTransliterator(dict)
        val voices = listOf(
            SystemVoiceInfo("vi-vn-x-gft-local", "Vi", "vi-VN", false),
            SystemVoiceInfo("en-us-x-sfg-local", "En", "en-US", false),
        )

        val enabledPiper = SubtitleTtsSettings(
            engineType = SubtitleTtsEngineType.PIPER_ONNX,
            transliterateEnglishNames = true,
        )
        val (spokenEnabled, finalEnabled) = prepareTtsSpeechText(
            cleanText = "Christopher gặp Tony Stark",
            settings = enabledPiper,
            availableVoices = voices,
            usePiper = true,
            transliterator = transliterator,
        )
        assertEquals("Crít xtô phơ gặp Tô ni Xtác", spokenEnabled)
        assertEquals("Crít xtô phơ gặp Tô ni Xtác.", finalEnabled)

        // Expanded Vietnamese syllables produce a longer natural duration estimate than raw English tokens.
        val rawDurationMs = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Christopher gặp Tony Stark")
        val spokenDurationMs = SubtitleTtsTimingCalculator.estimateNaturalDurationMs(spokenEnabled)
        assertTrue(spokenDurationMs > rawDurationMs, "Expected $spokenDurationMs > $rawDurationMs")

        // Lookahead and Speak compute identical PcmCacheKey values when both pass through prepareTtsSpeechText.
        val budgetMs = 1_500L
        val lookaheadSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = spokenEnabled,
            budgetMs = budgetMs,
            baseSpeed = enabledPiper.baseSpeed,
            autoSpeedEnabled = enabledPiper.autoSpeedEnabled,
            maxAutoSpeed = enabledPiper.maxAutoSpeed,
        )
        val lookaheadKey = PcmCacheKey("ngochuyen5", finalEnabled, quantizeSpeedBucket(lookaheadSpeed))
        val speakKey = PcmCacheKey("ngochuyen5", finalEnabled, quantizeSpeedBucket(lookaheadSpeed))
        assertEquals(lookaheadKey, speakKey)

        // Disabled toggle preserves original text.
        val (_, finalDisabled) = prepareTtsSpeechText(
            cleanText = "Christopher gặp Tony Stark",
            settings = enabledPiper.copy(transliterateEnglishNames = false),
            availableVoices = voices,
            usePiper = true,
            transliterator = transliterator,
        )
        assertEquals("Christopher gặp Tony Stark.", finalDisabled)

        // Non-Vietnamese System voice skips Vietnamese transliteration even when enabled.
        val enSystemSettings = SubtitleTtsSettings(
            engineType = SubtitleTtsEngineType.SYSTEM,
            systemVoiceName = "en-us-x-sfg-local",
            transliterateEnglishNames = true,
        )
        val (_, finalEnglishVoice) = prepareTtsSpeechText(
            cleanText = "Christopher meets Tony Stark",
            settings = enSystemSettings,
            availableVoices = voices,
            usePiper = false,
            transliterator = transliterator,
        )
        assertEquals("Christopher meets Tony Stark.", finalEnglishVoice)
    }
}
