package com.nuvio.app.features.player.tts

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTtsPlayerPcmTest {

    @Test
    fun padPcmWithSilenceAddsLeadTailAndFadesEndSmoothly() {
        val sampleRate = 1000
        val speech = FloatArray(100) { 0.8f }

        val padded = padPcmWithSilence(
            samples = speech,
            sampleRate = sampleRate,
            leadMs = 40,
            tailMs = 450,
            fadeOutMs = 10,
        )

        // 40ms lead (40) + 100 speech + 450ms tail (450) = 590 samples
        assertEquals(590, padded.size)

        // Lead silence must be 0f
        for (i in 0 until 40) {
            assertEquals(0f, padded[i], 0.0001f)
        }

        // Body before fade window remains untouched
        assertEquals(0.8f, padded[40], 0.0001f)
        assertEquals(0.8f, padded[129], 0.0001f)

        // Fade-out window tapers down to 0f at end of speech (index 139)
        assertEquals(0.4f, padded[134], 0.0001f)
        assertEquals(0f, padded[139], 0.0001f)

        // Trailing silence must be 0f
        for (i in 140 until padded.size) {
            assertEquals(0f, padded[i], 0.0001f)
        }
    }
}
