package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveTvRepositoryTest {

    @Test
    fun parseM3uWithKodiPropDrmAndExtvlcHeaders() {
        val playlist = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #EXTVLCOPT:http-user-agent=TestUserAgent/1.0
            #EXTVLCOPT:http-referrer=https://referrer.example.com
            #EXTINF:-1 tvg-id="vtv1" tvg-name="VTV1 HD" tvg-logo="https://logo.png" group-title="Vietnam",VTV1 HD
            https://stream.example.com/live/vtv1.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("VTV1 HD", channel.name)
        assertEquals("Vietnam", channel.group)
        assertEquals("https://stream.example.com/live/vtv1.mpd", channel.streamUrl)
        assertEquals("clearkey", channel.drmType)
        assertEquals("0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210", channel.drmKey)
        assertEquals("mpd", channel.streamType)
        assertEquals("TestUserAgent/1.0", channel.headers["User-Agent"])
        assertEquals("https://referrer.example.com", channel.headers["Referer"])
    }

    @Test
    fun parseM3uWithPipedHeadersInUrl() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="HBO HD" group-title="Movies",HBO HD
            https://stream.example.com/hbo.m3u8|User-Agent=CustomAgent&Referer=https://hbo.com&X-Token=secret123
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("HBO HD", channel.name)
        assertEquals("https://stream.example.com/hbo.m3u8", channel.streamUrl)
        assertEquals("CustomAgent", channel.headers["User-Agent"])
        assertEquals("https://hbo.com", channel.headers["Referer"])
        assertEquals("secret123", channel.headers["X-Token"])
    }

    @Test
    fun parseM3uWithPlaylistMetadata() {
        val playlistSource = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Discovery Channel" group-title="Documentary",Discovery
            https://stream.example.com/discovery.m3u8
        """.trimIndent()

        val playlist = LiveTvPlaylist(
            id = "playlist-123",
            name = "My IPTV List",
            type = LiveTvPlaylistType.Url,
            source = "https://example.com/list.m3u",
            isEnabled = true,
        )

        val channels = parseM3uPlaylist(playlistSource, playlist)
        assertEquals(1, channels.size)

        val channel = channels.first()
        assertEquals("Discovery", channel.name)
        assertEquals("playlist-123", channel.playlistId)
        assertEquals("My IPTV List", channel.playlistName)
    }

    @Test
    fun parseM3uWithPlaylistLevelHeadersInherited() {
        val playlist = """
            #EXTM3U http-user-agent="GlobalAgent/1.0" http-referrer="https://global.tv"
            #EXTINF:-1 tvg-name="Channel 1",Channel 1
            https://stream.example.com/ch1.m3u8
            #EXTVLCOPT:user-agent=ChannelOverrideAgent/2.0
            #EXTINF:-1 tvg-name="Channel 2",Channel 2
            https://stream.example.com/ch2.m3u8
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(2, channels.size)

        val ch1 = channels[0]
        assertEquals("GlobalAgent/1.0", ch1.headers["User-Agent"])
        assertEquals("https://global.tv", ch1.headers["Referer"])

        val ch2 = channels[1]
        assertEquals("ChannelOverrideAgent/2.0", ch2.headers["User-Agent"])
        assertEquals("https://global.tv", ch2.headers["Referer"])
    }

    @Test
    fun parseM3uDrmUrlKeyExtractionAndDefaultIptvUserAgent() {
        val playlist = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=https://tv.vietanhtv.top/sex/cleankey.php?id=e7b9e0780287a38fe4c42faabfb6dc64:a38f4d4ba389ca038166c43fe11cf4e3
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #EXTINF:-1 tvg-name="VTV3 Backup",VTV3 Backup
            https://stream.example.com/vtv3/manifest.mpd
        """.trimIndent()

        val channels = parseM3uPlaylist(playlist)
        assertEquals(1, channels.size)

        val ch = channels.first()
        assertEquals(IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT, ch.headers["User-Agent"])
        assertNotNull(ch.drmKey)
        assertTrue(ch.drmKey!!.contains("\"keys\":["), "Direct URL id parameter should be extracted to JSON")
    }

    @Test
    fun testFavoritesRetainsAllChannelsAcrossPlaylists() {
        val ch1 = LiveTvChannel(
            id = "ch-1",
            name = "VTV1",
            streamUrl = "https://vtv1.stream",
            playlistId = "playlist-a",
            group = "News",
        )
        val ch2 = LiveTvChannel(
            id = "ch-2",
            name = "HBO",
            streamUrl = "https://hbo.stream",
            playlistId = "playlist-b",
            group = "Movies",
        )
        val ch3 = LiveTvChannel(
            id = "ch-3",
            name = "Discovery",
            streamUrl = "https://discovery.stream",
            playlistId = "playlist-b",
            group = "Documentary",
        )

        val allChannels = listOf(ch1, ch2, ch3)
        val playlistAChannels = listOf(ch1)
        val favoriteIds = setOf("ch-1", "ch-2")

        // When in Favorites mode, even if playlist A is selected, all favorites from all playlists are retained
        val favResult = filterLiveTvChannels(
            channels = playlistAChannels,
            allChannels = allChannels,
            favoriteChannelIds = favoriteIds,
            filterMode = LiveTvChannelFilterMode.Favorites,
            selectedCategoryName = null,
            searchQuery = "",
            uncategorizedGroupName = "Uncategorized",
        )
        assertEquals(2, favResult.size)
        assertTrue(favResult.any { it.id == "ch-1" })
        assertTrue(favResult.any { it.id == "ch-2" })

        // When in All mode, only playlist A channels are displayed
        val allResult = filterLiveTvChannels(
            channels = playlistAChannels,
            allChannels = allChannels,
            favoriteChannelIds = favoriteIds,
            filterMode = LiveTvChannelFilterMode.All,
            selectedCategoryName = null,
            searchQuery = "",
            uncategorizedGroupName = "Uncategorized",
        )
        assertEquals(1, allResult.size)
        assertEquals("ch-1", allResult.first().id)
    }
}

