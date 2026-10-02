package com.nuvio.app.features.home

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

actual object HomeHeroStorage {
    private const val payloadKey = "hero_items_payload"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }
}
