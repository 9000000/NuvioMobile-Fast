package com.nuvio.app.features.trakt

import com.nuvio.app.features.simkl.SimklPkceCrypto
import com.nuvio.app.features.simkl.base64UrlWithoutPadding

internal data class TraktPkceMaterial(
    val verifier: String,
    val challenge: String,
    val state: String,
)

internal fun generateTraktPkceMaterial(): TraktPkceMaterial =
    createTraktPkceMaterial(
        verifierEntropy = SimklPkceCrypto.secureRandomBytes(32),
        stateEntropy = SimklPkceCrypto.secureRandomBytes(32),
    )

internal fun createTraktPkceMaterial(
    verifierEntropy: ByteArray,
    stateEntropy: ByteArray,
): TraktPkceMaterial {
    require(verifierEntropy.size >= 32) { "PKCE verifier entropy must be at least 32 bytes" }
    require(stateEntropy.size >= 16) { "OAuth state entropy must be at least 16 bytes" }
    val verifier = verifierEntropy.base64UrlWithoutPadding()
    require(verifier.length in 43..128) { "PKCE verifier must be 43-128 characters" }
    return TraktPkceMaterial(
        verifier = verifier,
        challenge = computeTraktChallenge(verifier),
        state = stateEntropy.base64UrlWithoutPadding(),
    )
}

internal fun computeTraktChallenge(verifier: String): String =
    SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding()
