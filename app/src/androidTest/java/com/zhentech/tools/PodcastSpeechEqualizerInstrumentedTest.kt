package com.zhentech.tools

import android.media.audiofx.AudioEffect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastSpeechEqualizerInstrumentedTest {
    @Test
    fun fixedPresetCanOwnAndEnableTheOutputMixEqualizer() {
        assumeTrue(
            AudioEffect.queryEffects().orEmpty().any {
                it.type == AudioEffect.EFFECT_TYPE_EQUALIZER
            },
        )
        val equalizer = PodcastSpeechEqualizer.create()

        try {
            assertTrue(equalizer.isEnabled)
        } finally {
            equalizer.close()
        }
    }
}
