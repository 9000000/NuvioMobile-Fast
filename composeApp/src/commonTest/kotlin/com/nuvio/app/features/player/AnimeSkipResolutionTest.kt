package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.ArmIdResolver
import com.nuvio.app.features.player.skip.SimklIdResolver
import com.nuvio.app.features.player.skip.SkipIntroRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnimeSkipResolutionTest {

    @AfterTest
    fun tearDown() {
        SkipIntroRepository.clearCache()
        SimklIdResolver.clearCache()
        ArmIdResolver.clearCache()
    }

    @Test
    fun armResolvesOnePieceImdbToMalId() = runTest {
        // One Piece IMDb is tt0388629, MAL ID is 21
        val entries = ArmIdResolver.resolveImdb("tt0388629")
        assertTrue(entries.isNotEmpty())
        assertEquals("21", entries.first().mal)
    }

    @Test
    fun armResolvesKitsuToMalId() = runTest {
        // One Piece Kitsu ID 12 resolves to MAL 21
        val entry = ArmIdResolver.resolveBySource("kitsu", "12")
        assertNotNull(entry)
        assertEquals("21", entry.mal)
    }

    @Test
    fun simklResolverFallsBackToArmForOnePiece() = runTest {
        val resolved = SimklIdResolver.resolveIdsForImdbEpisode("tt0388629", season = 1, episode = 1)
        assertNotNull(resolved)
        assertEquals("21", resolved.mal)
        assertEquals("anime", resolved.type)
    }

    @Test
    fun simklResolverFallsBackToArmForAttackOnTitanSeason2() = runTest {
        // Attack on Titan IMDb tt2560140 Season 2 is MAL 18397
        val resolved = SimklIdResolver.resolveIdsForImdbEpisode("tt2560140", season = 2, episode = 1)
        assertNotNull(resolved)
        assertEquals("18397", resolved.mal)
    }

    @Test
    fun skipIntroRepositoryReturnsOnePieceOpeningInterval() = runTest {
        // One Piece Episode 1 OP interval via AniSkip / IntroDb
        val intervals = SkipIntroRepository.getSkipIntervals(
            imdbId = "tt0388629",
            season = 1,
            episode = 1,
            requireSkipIntroEnabled = false,
        )
        assertTrue(intervals.isNotEmpty(), "Intervals should not be empty for One Piece episode 1")
        val op = intervals.firstOrNull { it.type in listOf("op", "mixed-op", "intro") }
        assertNotNull(op, "Should contain opening interval")
        assertTrue(op.startTime > 0, "OP start time should be positive")
        assertTrue(op.endTime > op.startTime, "OP end time should be after start time")
    }

    @Test
    fun skipIntroRepositoryReturnsAnimeMovieInterval() = runTest {
        // Demon Slayer Mugen Train movie (tt11032374) has AniSkip times
        val intervals = SkipIntroRepository.getMovieSkipIntervals(
            contentId = "tt11032374",
            videoId = "tt11032374",
            requireSkipIntroEnabled = false,
        )
        assertTrue(intervals.isNotEmpty(), "Intervals should not be empty for anime movie")
    }
}
