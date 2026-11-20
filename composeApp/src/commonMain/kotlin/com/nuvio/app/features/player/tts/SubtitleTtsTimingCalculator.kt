package com.nuvio.app.features.player.tts

import kotlin.math.roundToInt

/**
 * Pure timing budget, speech duration estimation, and adaptive speed calculation utilities
 * for synchronizing subtitle TTS output with video subtitle timestamps.
 */
object SubtitleTtsTimingCalculator {
    const val MIN_MAX_AUTO_SPEED = 1.4f
    const val MAX_MAX_AUTO_SPEED = 2.2f
    const val DEFAULT_MAX_AUTO_SPEED = 2.1f

    const val MIN_BASE_SPEED = 0.7f
    const val MAX_BASE_SPEED = 2.0f
    const val DEFAULT_BASE_SPEED = 1.0f

    const val MIN_PLAYBACK_SPEED = 0.25f
    const val MAX_PLAYBACK_SPEED = 4.0f
    const val DEFAULT_PLAYBACK_SPEED = 1.0f

    const val MIN_SPEECH_BUDGET_MS = 250L
    const val SAFETY_BREATH_MARGIN_MS = 80L

    const val DEFAULT_TAIL_MS = 450
    const val MIN_ADAPTIVE_TAIL_MS = 45
    const val COMFORTABLE_SLACK_MS = 420L

    const val RECALIBRATION_TOLERANCE_MS = 80L
    const val MIN_RECALIBRATION_HEADROOM = 0.02f
    const val MIN_RECALIBRATION_STEP = 0.05f
    const val MIN_RECALIBRATION_DELTA = 0.04f

    private const val BASE_UTTERANCE_OVERHEAD_MS = 140L
    private const val MS_PER_WORD = 220L
    private const val BASE_CHARS_PER_WORD = 4
    private const val MS_PER_EXTRA_CHAR = 18L
    private const val CLAUSE_PAUSE_MS = 120L
    private const val TERMINAL_PAUSE_MS = 140L
    private const val SPEED_ROUNDING_SCALE = 100f

    private val whitespaceRegex = Regex("\\s+")
    private val clausePauseChars = setOf(',', ';', ':')
    private val terminalPauseChars = setOf('.', '!', '?', '…')
    private val dashPauseChars = setOf('–', '—')

    /**
     * Resolves the available real-time speech budget in milliseconds for [context].
     *
     * Prioritizes `nextCueStartMs` over `endMs` so speech can continue naturally through any
     * trailing silence before the next subtitle appears, adjusts for elapsed cue time and
     * [SubtitleCuePlaybackContext.playbackSpeed], and returns `null` when no valid slot end exists.
     */
    fun resolveSpeechBudgetMs(context: SubtitleCuePlaybackContext?): Long? {
        if (context == null) return null
        val currentCue = context.currentCue
        val effectiveStartMs = maxOf(currentCue.startMs, context.positionMs)
        val slotEndMs = currentCue.nextCueStartMs?.takeIf { it > effectiveStartMs }
            ?: currentCue.endMs?.takeIf { it > effectiveStartMs }
            ?: return null

        val rawWindowMs = slotEndMs - effectiveStartMs
        val safePlaybackSpeed = clampFinite(
            value = context.playbackSpeed,
            fallback = DEFAULT_PLAYBACK_SPEED,
            min = MIN_PLAYBACK_SPEED,
            max = MAX_PLAYBACK_SPEED,
        )
        val realTimeWindowMs = (rawWindowMs.toDouble() / safePlaybackSpeed.toDouble()).toLong()
        return (realTimeWindowMs - SAFETY_BREATH_MARGIN_MS).coerceAtLeast(MIN_SPEECH_BUDGET_MS)
    }

    /**
     * Estimates the natural spoken duration in milliseconds at `1.0f` speed for [cleanText].
     *
     * Uses whitespace-separated word/syllable counts, extra character duration for longer words,
     * punctuation pause bonuses, and base utterance overhead.
     */
    fun estimateNaturalDurationMs(cleanText: String): Long {
        val trimmed = cleanText.trim()
        if (trimmed.isEmpty()) return 0L

        val words = trimmed.split(whitespaceRegex)
        var wordsDurationMs = 0L
        for (word in words) {
            val coreLength = word.count { it.isLetterOrDigit() }
            val effectiveLength = if (coreLength > 0) coreLength else word.length
            val extraChars = (effectiveLength - BASE_CHARS_PER_WORD).coerceAtLeast(0)
            wordsDurationMs += MS_PER_WORD + (extraChars * MS_PER_EXTRA_CHAR)
        }

        return BASE_UTTERANCE_OVERHEAD_MS + wordsDurationMs + countPunctuationPauseMs(trimmed)
    }

    /**
     * Calculates the target speech speed to fit [cleanText] within [budgetMs].
     *
     * Keeps [baseSpeed] untouched when auto-speed is disabled or when estimated duration already
     * fits within [budgetMs], and falls back to length-based scaling when [budgetMs] is `null`.
     */
    fun calculateTimingAwareSpeed(
        cleanText: String,
        budgetMs: Long?,
        baseSpeed: Float,
        autoSpeedEnabled: Boolean,
        maxAutoSpeed: Float,
    ): Float {
        val safeBaseSpeed = clampFinite(
            value = baseSpeed,
            fallback = DEFAULT_BASE_SPEED,
            min = MIN_BASE_SPEED,
            max = MAX_BASE_SPEED,
        )
        if (!autoSpeedEnabled) {
            return safeBaseSpeed
        }

        val safeMaxSpeed = resolveSafeMaxSpeed(safeBaseSpeed, maxAutoSpeed)
        if (budgetMs == null) {
            return SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = cleanText,
                baseSpeed = safeBaseSpeed,
                autoSpeedEnabled = true,
                maxAutoSpeed = safeMaxSpeed,
            )
        }

        val naturalDurationMs = estimateNaturalDurationMs(cleanText)
        if (naturalDurationMs <= 0L) {
            return safeBaseSpeed
        }
        if (budgetMs <= 0L) {
            return safeMaxSpeed
        }

        val estimatedAtBaseMs = naturalDurationMs.toFloat() / safeBaseSpeed
        if (estimatedAtBaseMs <= budgetMs.toFloat()) {
            return safeBaseSpeed
        }

        val requiredSpeed = safeBaseSpeed * (estimatedAtBaseMs / budgetMs.toFloat())
        return requiredSpeed.coerceIn(safeBaseSpeed, safeMaxSpeed)
    }

    /**
     * Computes a recalibrated synthesis speed after measuring [actualSpeechMs] against [budgetMs].
     *
     * Returns `null` if the speech fits within tolerance, inputs are invalid, or insufficient
     * speed headroom remains below [maxAutoSpeed].
     */
    fun computeRecalibratedSpeed(
        usedSpeed: Float,
        actualSpeechMs: Long,
        budgetMs: Long,
        maxAutoSpeed: Float,
    ): Float? {
        if (budgetMs <= 0L || actualSpeechMs <= 0L || !usedSpeed.isFinite()) {
            return null
        }

        val safeMaxSpeed = clampFinite(
            value = maxAutoSpeed,
            fallback = DEFAULT_MAX_AUTO_SPEED,
            min = MIN_MAX_AUTO_SPEED,
            max = MAX_MAX_AUTO_SPEED,
        )
        if (actualSpeechMs <= budgetMs + RECALIBRATION_TOLERANCE_MS) {
            return null
        }
        if (usedSpeed >= safeMaxSpeed - MIN_RECALIBRATION_HEADROOM) {
            return null
        }

        val minTargetSpeed = minOf(usedSpeed + MIN_RECALIBRATION_STEP, safeMaxSpeed)
        val rawTargetSpeed = (usedSpeed * (actualSpeechMs.toFloat() / budgetMs.toFloat()))
            .coerceIn(minTargetSpeed, safeMaxSpeed)
        val roundedTargetSpeed = (rawTargetSpeed * SPEED_ROUNDING_SCALE).roundToInt() / SPEED_ROUNDING_SCALE
        val finalTargetSpeed = roundedTargetSpeed.coerceIn(minTargetSpeed, safeMaxSpeed)

        if (finalTargetSpeed - usedSpeed < MIN_RECALIBRATION_DELTA) {
            return null
        }
        return finalTargetSpeed
    }

    /**
     * Computes the trailing silence tail duration in milliseconds to append after synthesized PCM.
     *
     * Preserves [DEFAULT_TAIL_MS] when [budgetMs] is unknown or ample slack remains, and scales
     * down cleanly into `[MIN_ADAPTIVE_TAIL_MS..DEFAULT_TAIL_MS]` when cues sit close together.
     */
    fun computeAdaptiveTailMs(actualSpeechMs: Long, budgetMs: Long?): Int {
        if (budgetMs == null) return DEFAULT_TAIL_MS
        val slackMs = budgetMs - actualSpeechMs
        if (slackMs >= COMFORTABLE_SLACK_MS) return DEFAULT_TAIL_MS
        return slackMs
            .coerceIn(MIN_ADAPTIVE_TAIL_MS.toLong(), DEFAULT_TAIL_MS.toLong())
            .toInt()
    }

    internal fun clampFinite(value: Float, fallback: Float, min: Float, max: Float): Float =
        if (value.isFinite()) value.coerceIn(min, max) else fallback

    internal fun resolveSafeMaxSpeed(safeBaseSpeed: Float, maxAutoSpeed: Float): Float {
        val clampedMaxAuto = clampFinite(
            value = maxAutoSpeed,
            fallback = DEFAULT_MAX_AUTO_SPEED,
            min = MIN_MAX_AUTO_SPEED,
            max = MAX_MAX_AUTO_SPEED,
        )
        return maxOf(safeBaseSpeed, clampedMaxAuto)
    }

    private fun countPunctuationPauseMs(text: String): Long {
        var pauseMs = 0L
        var previousWasTerminal = false
        for (index in text.indices) {
            val ch = text[index]
            when {
                ch in terminalPauseChars -> {
                    if (!previousWasTerminal) {
                        pauseMs += TERMINAL_PAUSE_MS
                        previousWasTerminal = true
                    }
                }
                ch in dashPauseChars || (ch == '-' && isStandaloneDash(text, index)) -> {
                    pauseMs += TERMINAL_PAUSE_MS
                    previousWasTerminal = false
                }
                ch in clausePauseChars -> {
                    pauseMs += CLAUSE_PAUSE_MS
                    previousWasTerminal = false
                }
                else -> {
                    previousWasTerminal = false
                }
            }
        }
        return pauseMs
    }

    private fun isStandaloneDash(text: String, index: Int): Boolean {
        val prevIsSpace = index == 0 || text[index - 1].isWhitespace()
        val nextIsSpace = index == text.lastIndex || text[index + 1].isWhitespace()
        return prevIsSpace || nextIsSpace
    }
}
