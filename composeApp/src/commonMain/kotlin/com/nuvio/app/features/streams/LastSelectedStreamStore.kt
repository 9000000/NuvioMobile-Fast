package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global in-memory store that retains the most recently selected stream for each video/episode.
 * Survives navigation transitions (such as pushing PlayerRoute and popping back to StreamRoute).
 */
object LastSelectedStreamStore {
    private val _lastSelectedMap = MutableStateFlow<Map<String, StreamItem>>(emptyMap())
    val lastSelectedMap: StateFlow<Map<String, StreamItem>> = _lastSelectedMap.asStateFlow()

    private val _lastSelectedGlobalStream = MutableStateFlow<StreamItem?>(null)
    val lastSelectedGlobalStream: StateFlow<StreamItem?> = _lastSelectedGlobalStream.asStateFlow()

    private val _scrollTrigger = MutableStateFlow(0)
    val scrollTrigger: StateFlow<Int> = _scrollTrigger.asStateFlow()

    fun requestAutoScroll() {
        _scrollTrigger.value++
    }

    fun contentKey(
        videoId: String,
        parentMetaId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): String {
        return if (!parentMetaId.isNullOrBlank() && seasonNumber != null && episodeNumber != null) {
            "${parentMetaId.trim()}:s$seasonNumber:e$episodeNumber"
        } else {
            videoId.trim()
        }
    }

    fun recordSelection(
        videoId: String,
        parentMetaId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        stream: StreamItem,
    ) {
        val key = contentKey(videoId, parentMetaId, seasonNumber, episodeNumber)
        val normalizedVideoId = videoId.trim()
        _lastSelectedMap.value = _lastSelectedMap.value + (key to stream) + (normalizedVideoId to stream)
        _lastSelectedGlobalStream.value = stream
        requestAutoScroll()
    }

    fun get(
        videoId: String,
        parentMetaId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): StreamItem? {
        val key = contentKey(videoId, parentMetaId, seasonNumber, episodeNumber)
        val map = _lastSelectedMap.value
        return map[key] ?: map[videoId.trim()]
    }

    fun clear(
        videoId: String,
        parentMetaId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        val key = contentKey(videoId, parentMetaId, seasonNumber, episodeNumber)
        val normalizedVideoId = videoId.trim()
        val current = get(videoId, parentMetaId, seasonNumber, episodeNumber)
        _lastSelectedMap.value = _lastSelectedMap.value - key - normalizedVideoId
        if (current != null && _lastSelectedGlobalStream.value?.matchesPlayback(current, null) == true) {
            _lastSelectedGlobalStream.value = null
        }
    }
}
