package com.nuvio.app.features.trakt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraktPkceTest {

    @Test
    fun `generateTraktPkceMaterial produces valid verifier and S256 challenge`() {
        val material = generateTraktPkceMaterial()
        assertTrue(material.verifier.length in 43..128)
        assertTrue(material.challenge.isNotBlank())
        assertTrue(material.state.isNotBlank())

        val recomputedChallenge = computeTraktChallenge(material.verifier)
        assertEquals(material.challenge, recomputedChallenge)
    }

    @Test
    fun `createTraktPkceMaterial produces deterministic challenge from verifier`() {
        val verifierBytes = ByteArray(32) { it.toByte() }
        val stateBytes = ByteArray(16) { (it + 5).toByte() }

        val material = createTraktPkceMaterial(verifierBytes, stateBytes)
        assertEquals(computeTraktChallenge(material.verifier), material.challenge)
    }
}
