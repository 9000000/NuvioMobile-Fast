package com.nuvio.app.features.player.tts

import android.app.Application
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubtitleTtsStorageTest {

    @BeforeTest
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_subtitle_tts", Context.MODE_PRIVATE).edit().clear().commit()
        SubtitleTtsStorage.initialize(context)
    }

    @Test
    fun saveAndLoadSettingsRoundTripsTimingSyncFieldsAndClampsBounds() {
        val defaults = SubtitleTtsStorage.loadSettings()
        assertEquals(SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED, defaults.maxAutoSpeed, 0.001f)
        assertFalse(defaults.waitForCueCompletion)
        assertTrue(defaults.autoSpeedEnabled)

        val custom = defaults.copy(
            enabled = true,
            baseSpeed = 1.25f,
            autoSpeedEnabled = false,
            maxAutoSpeed = 1.85f,
            waitForCueCompletion = true,
            hideSubtitlesWhenEnabled = true,
        )
        SubtitleTtsStorage.saveSettings(custom)

        val loaded = SubtitleTtsStorage.loadSettings()
        assertTrue(loaded.enabled)
        assertEquals(1.25f, loaded.baseSpeed, 0.001f)
        assertFalse(loaded.autoSpeedEnabled)
        assertEquals(1.85f, loaded.maxAutoSpeed, 0.001f)
        assertTrue(loaded.waitForCueCompletion)
        assertTrue(loaded.hideSubtitlesWhenEnabled)

        // Out-of-range and non-finite speeds must clamp cleanly on save/load.
        SubtitleTtsStorage.saveSettings(
            custom.copy(
                baseSpeed = 3.5f,
                maxAutoSpeed = 3.0f,
            ),
        )
        val clampedHigh = SubtitleTtsStorage.loadSettings()
        assertEquals(SubtitleTtsTimingCalculator.MAX_BASE_SPEED, clampedHigh.baseSpeed, 0.001f)
        assertEquals(SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED, clampedHigh.maxAutoSpeed, 0.001f)

        SubtitleTtsStorage.saveSettings(
            custom.copy(
                baseSpeed = Float.NaN,
                maxAutoSpeed = 0.5f,
            ),
        )
        val clampedLowAndNan = SubtitleTtsStorage.loadSettings()
        assertEquals(SubtitleTtsTimingCalculator.DEFAULT_BASE_SPEED, clampedLowAndNan.baseSpeed, 0.001f)
        assertEquals(SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED, clampedLowAndNan.maxAutoSpeed, 0.001f)
    }
}
