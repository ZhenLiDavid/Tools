package com.zhentech.tools

import android.annotation.SuppressLint
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Fixed speech preset applied to the output mix while the direct HFP route is active. */
internal class PodcastSpeechEqualizer private constructor(
    private val equalizer: Equalizer,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val isEnabled: Boolean
        get() = !closed.get() && equalizer.enabled

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { equalizer.setEnabled(false) }
        runCatching { equalizer.release() }
    }

    companion object {
        private const val EFFECT_PRIORITY = 0
        private const val OUTPUT_MIX_AUDIO_SESSION = 0

        /**
         * Session 0 is the only app-level effect target that includes media owned by other apps.
         * It is intentionally scoped to the foreground HFP session and released during cleanup.
         */
        @SuppressLint("AudioEffectSession")
        fun create(): PodcastSpeechEqualizer {
            val equalizer = Equalizer(EFFECT_PRIORITY, OUTPUT_MIX_AUDIO_SESSION)
            try {
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
                return PodcastSpeechEqualizer(equalizer)
            } catch (exception: Exception) {
                runCatching { equalizer.release() }
                throw exception
            }
        }

        private const val MILLIHERTZ_PER_HERTZ = 1_000
    }
}

internal object PodcastSpeechPreset {
    private const val HIGH_PASS_EDGE_HZ = 180
    private const val HIGH_PASS_GAIN_MILLIBELS = -600
    private const val MUD_TARGET_HZ = 300
    private const val MUD_MIN_HZ = 180
    private const val MUD_MAX_HZ = 800
    private const val MUD_GAIN_MILLIBELS = -300
    private const val PRESENCE_TARGET_HZ = 2_800
    private const val PRESENCE_MIN_HZ = 900
    private const val PRESENCE_MAX_HZ = 6_000
    private const val PRESENCE_GAIN_MILLIBELS = 250

    fun bandLevelsMillibels(
        centerFrequenciesHz: List<Int>,
        minimumLevelMillibels: Short,
        maximumLevelMillibels: Short,
    ): List<Short> {
        require(minimumLevelMillibels <= maximumLevelMillibels)
        val levels = MutableList(centerFrequenciesHz.size) { 0 }

        centerFrequenciesHz.forEachIndexed { index, frequencyHz ->
            if (frequencyHz < HIGH_PASS_EDGE_HZ) levels[index] = HIGH_PASS_GAIN_MILLIBELS
        }
        nearestBand(
            centerFrequenciesHz = centerFrequenciesHz,
            targetHz = MUD_TARGET_HZ,
            minimumHz = MUD_MIN_HZ,
            maximumHz = MUD_MAX_HZ,
        )?.let { levels[it] = MUD_GAIN_MILLIBELS }
        nearestBand(
            centerFrequenciesHz = centerFrequenciesHz,
            targetHz = PRESENCE_TARGET_HZ,
            minimumHz = PRESENCE_MIN_HZ,
            maximumHz = PRESENCE_MAX_HZ,
        )?.let { levels[it] = PRESENCE_GAIN_MILLIBELS }

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
