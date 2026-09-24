package com.nuvio.app.features.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import com.nuvio.app.features.livetv.IptvHeaderProvider
import java.util.UUID

@OptIn(UnstableApi::class)
internal object AndroidPlayerDrmHelper {

    fun resolveDrmSchemeUuid(drmType: String?): UUID {
        val type = drmType?.trim()?.lowercase() ?: return C.CLEARKEY_UUID
        return when {
            type.contains("widevine") -> C.WIDEVINE_UUID
            type.contains("playready") -> C.PLAYREADY_UUID
            else -> C.CLEARKEY_UUID
        }
    }

    fun isRemoteLicenseKey(drmKey: String?): Boolean {
        val key = drmKey?.trim() ?: return false
        return key.startsWith("http://", ignoreCase = true) || key.startsWith("https://", ignoreCase = true)
    }

    fun configureDrm(
        mediaItemBuilder: MediaItem.Builder,
        drmType: String?,
        drmKey: String?,
        requestHeaders: Map<String, String> = emptyMap(),
    ): MediaItem.Builder {
        val key = drmKey?.trim()?.takeIf { it.isNotBlank() } ?: return mediaItemBuilder
        val uuid = resolveDrmSchemeUuid(drmType)

        val effectiveHeaders = requestHeaders.toMutableMap()
        if (effectiveHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            effectiveHeaders["User-Agent"] = IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT
        }

        if (isRemoteLicenseKey(key)) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(uuid)
                .setLicenseUri(key)
                .apply {
                    if (effectiveHeaders.isNotEmpty()) {
                        setLicenseRequestHeaders(effectiveHeaders)
                    }
                }
                .setMultiSession(true)
                .build()
            return mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        val json = ClearKeyDrmUtil.buildClearKeyJson(key)
        if (json != null) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                .setMultiSession(true)
                .build()
            return mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        return mediaItemBuilder
    }

    fun createDrmSessionManagerProvider(
        drmType: String?,
        drmKey: String?,
        licenseHeaders: Map<String, String> = emptyMap(),
    ): DrmSessionManagerProvider? {
        val key = drmKey?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val uuid = resolveDrmSchemeUuid(drmType)

        val effectiveHeaders = licenseHeaders.toMutableMap()
        if (effectiveHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            effectiveHeaders["User-Agent"] = IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT
        }

        if (isRemoteLicenseKey(key)) {
            return DrmSessionManagerProvider { _ ->
                val httpCallback = HttpMediaDrmCallback(
                    key,
                    PlayerPlaybackNetworking.createHttpDataSourceFactory(effectiveHeaders),
                ).apply {
                    effectiveHeaders.forEach { (hKey, hValue) ->
                        setKeyRequestProperty(hKey, hValue)
                    }
                }
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .setPlayClearSamplesWithoutKeys(true)
                    .build(httpCallback)
            }
        }

        val json = ClearKeyDrmUtil.buildClearKeyJson(key) ?: return null
        return DrmSessionManagerProvider { _ ->
            val callback = LocalMediaDrmCallback(json.encodeToByteArray())
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .setPlayClearSamplesWithoutKeys(true)
                .build(callback)
        }
    }
}
