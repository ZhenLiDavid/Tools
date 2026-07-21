package com.zhentech.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSpeechPresetTest {
    @Test
    fun commonFiveBandEqualizerGetsClippingSafeAuxLikeCurve() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 230, 910, 3_600, 14_000),
            minimumLevelMillibels = (-1_500).toShort(),
            maximumLevelMillibels = 1_500.toShort(),
        )

        assertEquals(listOf<Short>(-1_200, -700, -200, 0, -900), levels)
    }

    @Test
    fun sparseEqualizerKeepsTheNearestSpeechPresenceBandAsItsZeroDbReference() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 1_000, 14_000),
            minimumLevelMillibels = (-1_500).toShort(),
            maximumLevelMillibels = 1_500.toShort(),
        )

        assertEquals(listOf<Short>(-1_200, 0, -900), levels)
    }

    @Test
    fun presetClampsEveryBandToDeviceLimits() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(60, 230, 3_600),
            minimumLevelMillibels = (-200).toShort(),
            maximumLevelMillibels = 100.toShort(),
        )

        assertEquals(listOf<Short>(-200, -200, 0), levels)
    }

    @Test
    fun presetNeverBoostsAnyBandIntoScoClipping() {
        val levels = PodcastSpeechPreset.bandLevelsMillibels(
            centerFrequenciesHz = listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000),
            minimumLevelMillibels = (-1_500).toShort(),
            maximumLevelMillibels = 1_500.toShort(),
        )

        assertEquals(listOf<Short>(-1_200, -1_200, -700, -700, -400, -200, 0, -100, -350, -900), levels)
    }
}
