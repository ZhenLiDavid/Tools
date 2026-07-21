package com.zhentech.tools

import android.annotation.SuppressLint
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastSpeechEqualizerInstrumentedTest {
    @Test
    fun clarityPresetOwnsEnablesAndAppliesToTheOutputMixEqualizer() {
        assumeTrue(
            AudioEffect.queryEffects().orEmpty().any {
                it.type == AudioEffect.EFFECT_TYPE_EQUALIZER
            },
        )
        val equalizer = PodcastSpeechEqualizer.create()

        try {
            assertTrue(equalizer.isEnabled)
            assertTrue(equalizer.hasControl)
            val expectedLevels = PodcastSpeechPreset.bandLevelsMillibels(
                centerFrequenciesHz = equalizer.bandSettings.map { it.centerFrequencyHz },
                minimumLevelMillibels = equalizer.bandSettings.minOf { it.levelMillibels },
                maximumLevelMillibels = equalizer.bandSettings.maxOf { it.levelMillibels }
                    .coerceAtLeast(0),
            )
            assertEquals(expectedLevels, equalizer.bandSettings.map { it.levelMillibels })
        } finally {
            equalizer.close()
        }
    }

    @SuppressLint("AudioEffectSession")
    @Test
    fun closingClarityPresetRestoresThePreviousOutputMixEqualizer() {
        assumeTrue(
            AudioEffect.queryEffects().orEmpty().any {
                it.type == AudioEffect.EFFECT_TYPE_EQUALIZER
            },
        )
        val baseline = Equalizer(0, 0)
        assumeTrue(baseline.hasControl())
        val previousSettings = baseline.properties
        val previouslyEnabled = baseline.enabled
        val equalizer = PodcastSpeechEqualizer.create()
        var observer: Equalizer? = null

        try {
            equalizer.close()
            val restoringObserver = Equalizer(2_000, 0)
            observer = restoringObserver

            assertTrue(restoringObserver.hasControl())
            assertEquals(previouslyEnabled, restoringObserver.enabled)
            assertEquals(previousSettings.curPreset, restoringObserver.properties.curPreset)
            assertArrayEquals(previousSettings.bandLevels, restoringObserver.properties.bandLevels)
        } finally {
            equalizer.close()
            observer?.release()
            baseline.release()
        }
    }
}
