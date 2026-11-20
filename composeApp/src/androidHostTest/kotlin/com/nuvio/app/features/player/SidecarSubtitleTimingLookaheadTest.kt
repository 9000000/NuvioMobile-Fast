@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import com.nuvio.app.features.player.tts.SubtitleTtsTimingCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SidecarSubtitleTimingLookaheadTest {

    @Test
    fun extractsStartEndAndNextCueStartFromSortedSidecarCues() {
        val first = timedCue(text = "First line", startMs = 1_000L, durationMs = 2_000L)
        val second = timedCue(text = "Second line", startMs = 4_500L, durationMs = 1_500L)
        val cues = listOf(first, second)

        val context = extractSidecarTimingContext(
            cues = cues,
            activeEntries = listOf(first),
            nextIndex = 1,
            positionUs = 1_250_000L,
            playbackSpeed = 1.0f,
            isSorted = true,
        )

        assertNotNull(context)
        assertEquals("First line", context.currentCue.rawText)
        assertEquals(1_000L, context.currentCue.startMs)
        assertEquals(3_000L, context.currentCue.endMs)
        assertEquals(4_500L, context.currentCue.nextCueStartMs)
        assertEquals(1_250L, context.positionMs)
        assertEquals(1.0f, context.playbackSpeed, 0.001f)
        assertEquals(1, context.upcomingCues.size)
        assertEquals("Second line", context.upcomingCues[0].rawText)
        assertEquals(4_500L, context.upcomingCues[0].startMs)
        assertEquals(6_000L, context.upcomingCues[0].endMs)
        assertNull(context.upcomingCues[0].nextCueStartMs)
    }

    @Test
    fun skipsEmptySdhOnlyAndDuplicateCuesBetweenDialogueLines() {
        val active = timedCue(text = "First line", startMs = 1_000L, durationMs = 1_500L)
        val cues = listOf(
            active,
            timedCue(text = "", startMs = 2_600L, durationMs = 400L),
            timedCue(text = "   ", startMs = 3_100L, durationMs = 400L),
            timedCue(text = "[music]", startMs = 3_600L, durationMs = 800L),
            timedCue(text = "♪ ♪", startMs = 4_500L, durationMs = 700L),
            timedCue(text = "(sighs)", startMs = 5_300L, durationMs = 500L),
            timedCue(text = "<i>First line</i>", startMs = 5_900L, durationMs = 500L),
            timedCue(text = "Second line", startMs = 7_000L, durationMs = 1_500L),
            timedCue(text = "[applause]", startMs = 8_600L, durationMs = 500L),
            timedCue(text = "Second line", startMs = 9_200L, durationMs = 500L),
            timedCue(text = "Third line", startMs = 10_500L, durationMs = 2_000L),
        )

        val context = extractSidecarTimingContext(
            cues = cues,
            activeEntries = listOf(active),
            nextIndex = 1,
            positionUs = 1_100_000L,
            playbackSpeed = 1.25f,
            isSorted = true,
        )

        assertNotNull(context)
        assertEquals(7_000L, context.currentCue.nextCueStartMs)
        assertEquals(2, context.upcomingCues.size)
        assertEquals("Second line", context.upcomingCues[0].rawText)
        assertEquals(7_000L, context.upcomingCues[0].startMs)
        assertEquals(8_500L, context.upcomingCues[0].endMs)
        assertEquals(10_500L, context.upcomingCues[0].nextCueStartMs)
        assertEquals("Third line", context.upcomingCues[1].rawText)
        assertEquals(10_500L, context.upcomingCues[1].startMs)
        assertEquals(12_500L, context.upcomingCues[1].endMs)
        assertNull(context.upcomingCues[1].nextCueStartMs)
    }

    @Test
    fun extractsUpToTwoChainedUpcomingCuesAndPropagatesPlaybackSpeed() {
        val active = timedCue(text = "Line 0", startMs = 500L, durationMs = 1_000L)
        val cues = listOf(
            active,
            timedCue(text = "Line 1", startMs = 2_000L, durationMs = 1_000L),
            timedCue(text = "Line 2", startMs = 4_000L, durationMs = 1_200L),
            timedCue(text = "Line 3", startMs = 6_500L, durationMs = 1_500L),
            timedCue(text = "Line 4", startMs = 9_000L, durationMs = 1_000L),
        )

        val context = extractSidecarTimingContext(
            cues = cues,
            activeEntries = listOf(active),
            nextIndex = 1,
            positionUs = 700_000L,
            playbackSpeed = 1.5f,
            isSorted = true,
        )

        assertNotNull(context)
        assertEquals(1.5f, context.playbackSpeed, 0.001f)
        assertEquals(2_000L, context.currentCue.nextCueStartMs)
        assertEquals(2, context.upcomingCues.size)
        assertEquals(4_000L, context.upcomingCues[0].nextCueStartMs)
        assertEquals(6_500L, context.upcomingCues[1].nextCueStartMs)

        val nonFiniteSpeedContext = extractSidecarTimingContext(
            cues = cues,
            activeEntries = listOf(active),
            nextIndex = 1,
            positionUs = 700_000L,
            playbackSpeed = Float.NaN,
            isSorted = true,
        )
        assertNotNull(nonFiniteSpeedContext)
        assertEquals(
            SubtitleTtsTimingCalculator.DEFAULT_PLAYBACK_SPEED,
            nonFiniteSpeedContext.playbackSpeed,
            0.001f,
        )
    }

    @Test
    fun handlesConcurrentActiveEntriesAndUnknownDuration() {
        val sdhBackground = timedCue(text = "[Music playing]", startMs = 0L, durationMs = 10_000L)
        val dialogueA = timedCue(text = "Hello", startMs = 2_000L, durationMs = 1_500L)
        val dialogueB = timedCue(text = "World", startMs = 2_500L, durationMs = 2_500L)
        val next = timedCue(text = "Next", startMs = 6_000L, durationMs = null)
        val cues = listOf(sdhBackground, dialogueA, dialogueB, next)

        val context = extractSidecarTimingContext(
            cues = cues,
            activeEntries = listOf(sdhBackground, dialogueA, dialogueB),
            nextIndex = 3,
            positionUs = 2_600_000L,
            playbackSpeed = 1.0f,
            isSorted = true,
        )

        assertNotNull(context)
        assertEquals(2_000L, context.currentCue.startMs)
        assertEquals(5_000L, context.currentCue.endMs)
        assertEquals(6_000L, context.currentCue.nextCueStartMs)
        assertEquals(1, context.upcomingCues.size)
        assertNull(context.upcomingCues[0].endMs)

        val unknownDurationActive = timedCue(text = "Live cue", startMs = 1_000L, durationMs = null)
        val unknownContext = extractSidecarTimingContext(
            cues = listOf(unknownDurationActive),
            activeEntries = listOf(unknownDurationActive),
            nextIndex = 1,
            positionUs = 1_000_000L,
            playbackSpeed = 1.0f,
            isSorted = true,
        )
        assertNotNull(unknownContext)
        assertNull(unknownContext.currentCue.endMs)
    }

    @Test
    fun returnsNullWhenActiveEntriesAreEmptyOrSdhOnly() {
        val sdhCue = timedCue(text = "[music]", startMs = 1_000L, durationMs = 2_000L)
        assertNull(
            extractSidecarTimingContext(
                cues = listOf(sdhCue),
                activeEntries = emptyList(),
                nextIndex = 0,
                positionUs = 1_000_000L,
                playbackSpeed = 1.0f,
            ),
        )
        assertNull(
            extractSidecarTimingContext(
                cues = listOf(sdhCue),
                activeEntries = listOf(sdhCue),
                nextIndex = 1,
                positionUs = 1_200_000L,
                playbackSpeed = 1.0f,
            ),
        )
    }

    @Test
    fun extractsChainedLookaheadFromSyncCueTimelineAndSupportsTextlessBoundaries() {
        val timeline = listOf(
            SubtitleSyncCue(startTimeMs = 1_000L, endTimeMs = 2_800L, text = "First cue"),
            SubtitleSyncCue(startTimeMs = 3_000L, endTimeMs = 3_500L, text = "♪"),
            SubtitleSyncCue(startTimeMs = 3_800L, endTimeMs = 4_200L, text = "First cue"),
            SubtitleSyncCue(startTimeMs = 5_000L, endTimeMs = 6_500L, text = "Second cue"),
            SubtitleSyncCue(startTimeMs = 7_500L, endTimeMs = 9_000L, text = "Third cue"),
            SubtitleSyncCue(startTimeMs = 10_000L, endTimeMs = 11_500L, text = "Fourth cue"),
        )

        val context = extractTimingContextFromSyncCues(
            timeline = timeline,
            currentRawText = "First cue",
            playbackSpeed = 1.4f,
            positionMs = 1_100L,
        )

        assertNotNull(context)
        assertEquals(1_000L, context.currentCue.startMs)
        assertEquals(2_800L, context.currentCue.endMs)
        assertEquals(5_000L, context.currentCue.nextCueStartMs)
        assertEquals(1.4f, context.playbackSpeed, 0.001f)
        assertEquals(2, context.upcomingCues.size)
        assertEquals("Second cue", context.upcomingCues[0].rawText)
        assertEquals(7_500L, context.upcomingCues[0].nextCueStartMs)
        assertEquals("Third cue", context.upcomingCues[1].rawText)
        assertEquals(10_000L, context.upcomingCues[1].nextCueStartMs)

        val textlessTimeline = listOf(
            SubtitleSyncCue(startTimeMs = 1_000L, endTimeMs = 2_500L, text = ""),
            SubtitleSyncCue(startTimeMs = 4_200L, endTimeMs = 6_000L, text = ""),
        )
        val textlessContext = extractTimingContextFromSyncCues(
            timeline = textlessTimeline,
            currentRawText = "Decoded caption",
            playbackSpeed = 1.0f,
            positionMs = 1_200L,
            preferredStartMs = 1_000L,
            allowTextlessTimingBoundaries = true,
        )
        assertNotNull(textlessContext)
        assertEquals(1_000L, textlessContext.currentCue.startMs)
        assertEquals(2_500L, textlessContext.currentCue.endMs)
        assertEquals(4_200L, textlessContext.currentCue.nextCueStartMs)
        assertTrue(textlessContext.upcomingCues.isEmpty())
    }

    @Test
    fun sidecarControllerPreservesBackwardCompatibleAndTimedConstructors() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val legacyController = SidecarSubtitleController(
                scope = scope,
                getPlayer = { null },
                presentCues = { it },
            )
            val timedController = SidecarSubtitleController(
                scope = scope,
                getPlayer = { null },
                presentCues = { it },
                presentTimedCues = { cues, _ -> cues },
            )
            legacyController.stopSidecarAddonSubtitle(clearView = false)
            timedController.stopSidecarAddonSubtitle(clearView = false)
        } finally {
            scope.cancel()
        }
    }

    private fun timedCue(text: String, startMs: Long, durationMs: Long?): CuesWithTiming {
        val cue = Cue.Builder().setText(text).build()
        val durationUs = durationMs?.times(1_000L) ?: C.TIME_UNSET
        return CuesWithTiming(listOf(cue), startMs * 1_000L, durationUs)
    }
}
