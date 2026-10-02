package com.nuvio.app.features.home

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object HomeHeroStorage {
    private const val preferencesName = "nuvio_home_hero_cache"
    private const val payloadKey = "hero_items_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }
}
