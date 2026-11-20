package com.nuvio.app.features.player.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleTtsTimingCalculatorTest {

    @Test
    fun settingsAndPlaybackContextExposeExpectedDefaults() {
        val settings = SubtitleTtsSettings()
        assertFalse(settings.enabled)
        assertEquals(1.0f, settings.baseSpeed, 0.001f)
        assertTrue(settings.autoSpeedEnabled)
        assertEquals(2.1f, settings.maxAutoSpeed, 0.001f)
        assertFalse(settings.waitForCueCompletion)

        val cue = TimedSpeechCue(rawText = "Xin chào", startMs = 1_500L, endMs = 3_000L, nextCueStartMs = 3_500L)
        val context = SubtitleCuePlaybackContext(currentCue = cue)
        assertEquals(1_500L, context.positionMs)
        assertEquals(1.0f, context.playbackSpeed, 0.001f)
        assertTrue(context.upcomingCues.isEmpty())
    }

    @Test
    fun resolveSpeechBudgetMsReturnsNullWhenContextOrSlotEndMissing() {
        assertNull(SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(null))

        val liveCue = TimedSpeechCue(rawText = "Live caption", startMs = 1_000L, endMs = null, nextCueStartMs = null)
        assertNull(
            SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
                SubtitleCuePlaybackContext(currentCue = liveCue),
            ),
        )

        val expiredCue = TimedSpeechCue(rawText = "Expired", startMs = 1_000L, endMs = 2_000L, nextCueStartMs = 2_500L)
        assertNull(
            SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
                SubtitleCuePlaybackContext(currentCue = expiredCue, positionMs = 2_500L),
            ),
        )
    }

    @Test
    fun resolveSpeechBudgetMsPrioritizesNextCueStartOverEndMsAndFallsBackToEndMs() {
        val cueWithGap = TimedSpeechCue(
            rawText = "Câu ngắn nhưng có khoảng nghỉ dài",
            startMs = 1_000L,
            endMs = 2_200L,
            nextCueStartMs = 5_000L,
        )
        val budgetFromNextCue = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = cueWithGap, positionMs = 1_000L),
        )
        assertEquals(3_920L, budgetFromNextCue)

        val lastCueInTrack = TimedSpeechCue(
            rawText = "Câu cuối cùng",
            startMs = 10_000L,
            endMs = 12_500L,
            nextCueStartMs = null,
        )
        val budgetFromEndMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = lastCueInTrack, positionMs = 10_000L),
        )
        assertEquals(2_420L, budgetFromEndMs)
    }

    @Test
    fun resolveSpeechBudgetMsAccountsForLateEntryPlaybackSpeedAndMinimumFloor() {
        val cue = TimedSpeechCue(
            rawText = "Chúng ta phải đi ngay.",
            startMs = 2_000L,
            endMs = 3_600L,
            nextCueStartMs = 4_000L,
        )

        val lateBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(
                currentCue = cue,
                playbackSpeed = 1.25f,
                positionMs = 2_400L,
            ),
        )
        // Remaining media window = 4000 - 2400 = 1600ms; real-time at 1.25x = 1280ms; minus 80ms margin = 1200ms
        assertEquals(1_200L, lateBudget)

        val tinyCue = TimedSpeechCue(
            rawText = "Ồ",
            startMs = 1_000L,
            endMs = 1_200L,
            nextCueStartMs = 1_260L,
        )
        val clampedBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = tinyCue, playbackSpeed = 2.0f),
        )
        assertEquals(SubtitleTtsTimingCalculator.MIN_SPEECH_BUDGET_MS, clampedBudget)

        val nonFiniteSpeedBudget = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(
            SubtitleCuePlaybackContext(currentCue = cue, playbackSpeed = Float.NaN),
        )
        assertEquals(1_920L, nonFiniteSpeedBudget)
    }

    @Test
    fun estimateNaturalDurationMsScalesWithWordsExtraLettersAndPunctuation() {
        assertEquals(0L, SubtitleTtsTimingCalculator.estimateNaturalDurationMs("   "))

        val simple = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Xin chào")
        assertEquals(580L, simple)

        val withPunctuation = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Xin chào, tôi là Minh.")
        // 140 overhead + 5*220 words + 120 comma + 140 period = 1500ms
        assertEquals(1_500L, withPunctuation)

        val ellipsis = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Không...")
        val singleDot = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("Không.")
        assertEquals(singleDot, ellipsis)

        val longEnglish = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("extraordinary")
        val shortEnglish = SubtitleTtsTimingCalculator.estimateNaturalDurationMs("good")
        assertTrue(longEnglish > shortEnglish)
    }

    @Test
    fun calculateTimingAwareSpeedKeepsBaseSpeedWhenPlentyOfTimeExists() {
        val longSentence = "Anh ấy đã quyết định rời khỏi thành phố vào sáng sớm hôm sau để bắt đầu lại."
        val generousContext = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue(
                rawText = longSentence,
                startMs = 0L,
                endMs = 3_500L,
                nextCueStartMs = 6_000L,
            ),
        )
        val budgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(generousContext)
        val speed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = longSentence,
            budgetMs = budgetMs,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(1.0f, speed, 0.001f)
    }

    @Test
    fun calculateTimingAwareSpeedAcceleratesTightCuesAndRespectsBounds() {
        val mediumSentence = "Chúng ta không còn nhiều thời gian nữa đâu!"
        val tightContext = SubtitleCuePlaybackContext(
            currentCue = TimedSpeechCue(
                rawText = mediumSentence,
                startMs = 1_000L,
                endMs = 2_400L,
                nextCueStartMs = 2_600L,
            ),
            playbackSpeed = 1.25f,
        )
        val tightBudgetMs = SubtitleTtsTimingCalculator.resolveSpeechBudgetMs(tightContext)
        val accelerated = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = mediumSentence,
            budgetMs = tightBudgetMs,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertTrue(accelerated in 1.4f..2.1f, "Expected accelerated speed in 1.4..2.1, got $accelerated")

        val veryLongSentence = "Tôi thực sự không thể tin rằng mọi chuyện lại diễn ra nhanh như vậy trong buổi tối hôm nay."
        val capped = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongSentence,
            budgetMs = 900L,
            baseSpeed = 1.0f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(2.1f, capped, 0.001f)

        val disabled = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongSentence,
            budgetMs = 900L,
            baseSpeed = 1.15f,
            autoSpeedEnabled = false,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(1.15f, disabled, 0.001f)
    }

    @Test
    fun calculateTimingAwareSpeedFallsBackToLengthScalingWhenBudgetNull() {
        val veryLongText = "a".repeat(100)
        val fallbackSpeed = SubtitleTtsTimingCalculator.calculateTimingAwareSpeed(
            cleanText = veryLongText,
            budgetMs = null,
            baseSpeed = 1.5f,
            autoSpeedEnabled = true,
            maxAutoSpeed = 1.9f,
        )
        assertEquals(1.9f, fallbackSpeed, 0.001f)
    }

    @Test
    fun computeRecalibratedSpeedRecalibratesOnlyWhenExceedingToleranceWithHeadroom() {
        val recalibrated = SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
            usedSpeed = 1.2f,
            actualSpeechMs = 2_600L,
            budgetMs = 1_800L,
            maxAutoSpeed = 2.1f,
        )
        assertEquals(1.73f, recalibrated!!, 0.01f)

        assertNull(
            SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
                usedSpeed = 1.2f,
                actualSpeechMs = 1_880L,
                budgetMs = 1_800L,
                maxAutoSpeed = 2.1f,
            ),
        )

        assertNull(
            SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
                usedSpeed = 2.1f,
                actualSpeechMs = 2_600L,
                budgetMs = 1_800L,
                maxAutoSpeed = 2.1f,
            ),
        )

        // Near ceiling where remaining headroom < MIN_RECALIBRATION_DELTA (0.04f) must return null without crashing
        assertNull(
            SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
                usedSpeed = 2.07f,
                actualSpeechMs = 2_600L,
                budgetMs = 1_800L,
                maxAutoSpeed = 2.1f,
            ),
        )

        val clampedAtCeiling = SubtitleTtsTimingCalculator.computeRecalibratedSpeed(
            usedSpeed = 1.6f,
            actualSpeechMs = 3_200L,
            budgetMs = 1_200L,
            maxAutoSpeed = 2.2f,
        )
        assertEquals(2.2f, clampedAtCeiling!!, 0.001f)
    }

    @Test
    fun computeAdaptiveTailMsPreservesTailWhenSlackExistsAndShrinksWhenTight() {
        assertEquals(450, SubtitleTtsTimingCalculator.computeAdaptiveTailMs(actualSpeechMs = 1_500L, budgetMs = null))
        assertEquals(450, SubtitleTtsTimingCalculator.computeAdaptiveTailMs(actualSpeechMs = 1_580L, budgetMs = 2_000L))
        assertEquals(250, SubtitleTtsTimingCalculator.computeAdaptiveTailMs(actualSpeechMs = 1_750L, budgetMs = 2_000L))
        assertEquals(45, SubtitleTtsTimingCalculator.computeAdaptiveTailMs(actualSpeechMs = 1_980L, budgetMs = 2_000L))
        assertEquals(45, SubtitleTtsTimingCalculator.computeAdaptiveTailMs(actualSpeechMs = 2_300L, budgetMs = 2_000L))
    }
}
