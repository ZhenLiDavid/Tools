package com.zhentech.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSpeechPresetTest {
    @Test
    fun commonFiveBandEqualizerGetsPodcastSpeechCurve() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 230, 910, 3_600, 14_000),
            minimumLevelMillibels = (-1_500).toShort(),
            maximumLevelMillibels = 1_500.toShort(),
        )

        assertEquals(listOf<Short>(-600, -300, 0, 250, 0), levels)
    }

    @Test
    fun sparseEqualizerStillLiftsTheNearestSpeechPresenceBand() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 1_000, 14_000),
            minimumLevelMillibels = (-1_500).toShort(),
            maximumLevelMillibels = 1_500.toShort(),
        )

        assertEquals(listOf<Short>(-600, 250, 0), levels)
    }

    @Test
    fun presetClampsEveryBandToDeviceLimits() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 230, 3_600),
            minimumLevelMillibels = (-200).toShort(),
            maximumLevelMillibels = 100.toShort(),
        )

        assertEquals(listOf<Short>(-200, -200, 100), levels)
    }
}
