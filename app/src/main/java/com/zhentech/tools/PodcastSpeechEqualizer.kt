package com.zhentech.tools

import android.annotation.SuppressLint
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Clipping-safe clarity preset applied to the output mix while the direct HFP route is active. */
internal class PodcastSpeechEqualizer private constructor(
    private val equalizer: Equalizer,
    private val previousSettings: Equalizer.Settings,
    private val previouslyEnabled: Boolean,
    val bandSettings: List<BandSetting>,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val isEnabled: Boolean
        get() = !closed.get() && equalizer.enabled

    val hasControl: Boolean
        get() = !closed.get() && equalizer.hasControl()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { equalizer.setProperties(previousSettings) }
        runCatching { equalizer.setEnabled(previouslyEnabled) }
        runCatching { equalizer.release() }
    }

    data class BandSetting(
        val centerFrequencyHz: Int,
        val levelMillibels: Short,
    )

    companion object {
        // This private, single-purpose app should win control over a stale output-mix EQ instance.
        private const val EFFECT_PRIORITY = 1_000
        private const val OUTPUT_MIX_AUDIO_SESSION = 0

        /**
         * Session 0 is the only app-level effect target that includes media owned by other apps.
         * It is intentionally scoped to the foreground HFP session and released during cleanup.
         */
        @SuppressLint("AudioEffectSession")
        fun create(): PodcastSpeechEqualizer {
            val equalizer = Equalizer(EFFECT_PRIORITY, OUTPUT_MIX_AUDIO_SESSION)
            try {
                check(equalizer.hasControl())
                val previousSettings = equalizer.properties
                val previouslyEnabled = equalizer.enabled
                val bandCount = equalizer.numberOfBands.toInt()
                check(bandCount > 0)
                val levelRange = equalizer.bandLevelRange
                check(levelRange.size == 2)
                val centerFrequenciesHz = List(bandCount) { index ->
                    equalizer.getCenterFreq(index.toShort()) / MILLIHERTZ_PER_HERTZ
                }
                val levels = PodcastSpeechPreset.bandLevelsMillibels(
                    centerFrequenciesHz = centerFrequenciesHz,
                    minimumLevelMillibels = levelRange[0],
                    maximumLevelMillibels = levelRange[1],
                )
                levels.forEachIndexed { index, level ->
                    equalizer.setBandLevel(index.toShort(), level)
                }
                check(equalizer.setEnabled(true) == AudioEffect.SUCCESS)
                check(equalizer.enabled)
                check(equalizer.hasControl())
                val appliedSettings = centerFrequenciesHz.mapIndexed { index, frequencyHz ->
                    BandSetting(
                        centerFrequencyHz = frequencyHz,
                        levelMillibels = equalizer.getBandLevel(index.toShort()),
                    )
                }
                return PodcastSpeechEqualizer(
                    equalizer = equalizer,
                    previousSettings = previousSettings,
                    previouslyEnabled = previouslyEnabled,
                    bandSettings = appliedSettings,
                )
            } catch (exception: Exception) {
                runCatching { equalizer.release() }
                throw exception
            }
        }

        private const val MILLIHERTZ_PER_HERTZ = 1_000
    }
}

internal object PodcastSpeechPreset {
    private const val SUB_BASS_EDGE_HZ = 120
    private const val SUB_BASS_GAIN_MILLIBELS = -1_200
    private const val BASS_EDGE_HZ = 350
    private const val BASS_GAIN_MILLIBELS = -700
    private const val LOW_MID_EDGE_HZ = 800
    private const val LOW_MID_GAIN_MILLIBELS = -400
    private const val MID_EDGE_HZ = 1_500
    private const val MID_GAIN_MILLIBELS = -200
    private const val PRESENCE_EDGE_HZ = 5_000
    private const val PRESENCE_SHOULDER_GAIN_MILLIBELS = -100
    private const val SIBILANCE_EDGE_HZ = 8_000
    private const val SIBILANCE_GAIN_MILLIBELS = -350
    private const val AIR_GAIN_MILLIBELS = -900
    private const val PRESENCE_TARGET_HZ = 2_600
    private const val PRESENCE_MIN_HZ = 900
    private const val PRESENCE_MAX_HZ = 5_000
    private const val REFERENCE_GAIN_MILLIBELS = 0

    fun bandLevelsMillibels(
        centerFrequenciesHz: List<Int>,
        minimumLevelMillibels: Short,
        maximumLevelMillibels: Short,
    ): List<Short> {
        require(minimumLevelMillibels <= maximumLevelMillibels)
        require(minimumLevelMillibels <= 0 && maximumLevelMillibels >= 0)
        val levels = centerFrequenciesHz.mapTo(mutableListOf()) { frequencyHz ->
            when {
                frequencyHz < SUB_BASS_EDGE_HZ -> SUB_BASS_GAIN_MILLIBELS
                frequencyHz < BASS_EDGE_HZ -> BASS_GAIN_MILLIBELS
                frequencyHz < LOW_MID_EDGE_HZ -> LOW_MID_GAIN_MILLIBELS
                frequencyHz < MID_EDGE_HZ -> MID_GAIN_MILLIBELS
                frequencyHz <= PRESENCE_EDGE_HZ -> PRESENCE_SHOULDER_GAIN_MILLIBELS
                frequencyHz <= SIBILANCE_EDGE_HZ -> SIBILANCE_GAIN_MILLIBELS
                else -> AIR_GAIN_MILLIBELS
            }
        }

        nearestBand(
            centerFrequenciesHz = centerFrequenciesHz,
            targetHz = PRESENCE_TARGET_HZ,
            minimumHz = PRESENCE_MIN_HZ,
            maximumHz = PRESENCE_MAX_HZ,
        )?.let { levels[it] = REFERENCE_GAIN_MILLIBELS }

        return levels.map { level ->
            level.coerceIn(
                minimumLevelMillibels.toInt(),
                maximumLevelMillibels.toInt(),
            ).toShort()
        }
    }

    private fun nearestBand(
        centerFrequenciesHz: List<Int>,
        targetHz: Int,
        minimumHz: Int,
        maximumHz: Int,
    ): Int? = centerFrequenciesHz.indices
        .filter { centerFrequenciesHz[it] in minimumHz..maximumHz }
        .minByOrNull { abs(centerFrequenciesHz[it] - targetHz) }
}
