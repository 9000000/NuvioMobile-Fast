package com.nuvio.app.features.home

internal expect object HomeHeroStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
