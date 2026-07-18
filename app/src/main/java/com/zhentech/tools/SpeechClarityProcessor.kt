package com.zhentech.tools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A fixed mono preset tuned for spoken-word clarity over HFP voice bandwidth. */
internal class SpeechClarityProcessor(
    sampleRateHz: Int = SAMPLE_RATE_HZ,
) {
    private val highPass = Biquad.highPass(sampleRateHz, HIGH_PASS_HZ, FILTER_Q)
    private val lowMidCut = Biquad.peaking(sampleRateHz, LOW_MID_HZ, LOW_MID_GAIN_DB, FILTER_Q)
    private val presenceBoost = Biquad.peaking(sampleRateHz, PRESENCE_HZ, PRESENCE_GAIN_DB, FILTER_Q)

    /** Converts interleaved little-endian PCM16 stereo to filtered little-endian PCM16 mono. */
    fun processStereoPcm16(input: ByteArray, inputByteCount: Int, output: ByteArray): Int {
        val usableBytes = inputByteCount - (inputByteCount % STEREO_FRAME_BYTES)
        require(output.size >= usableBytes / 2)

        var inputIndex = 0
        var outputIndex = 0
        while (inputIndex < usableBytes) {
            val left = readPcm16(input, inputIndex)
            val right = readPcm16(input, inputIndex + PCM16_BYTES)
            val mono = ((left.toFloat() + right.toFloat()) * 0.5f) / PCM16_SCALE
            val filtered = presenceBoost.process(lowMidCut.process(highPass.process(mono)))
            writePcm16(output, outputIndex, softLimit(filtered))
            inputIndex += STEREO_FRAME_BYTES
            outputIndex += PCM16_BYTES
        }
        return outputIndex
    }

    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample

        val limitedMagnitude = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) *
            (1f - exp(-(magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)))
        return if (sample < 0f) -limitedMagnitude else limitedMagnitude
    }

    private fun readPcm16(buffer: ByteArray, index: Int): Short {
        val low = buffer[index].toInt() and 0xFF
        val high = buffer[index + 1].toInt()
        return ((high shl 8) or low).toShort()
    }

    private fun writePcm16(buffer: ByteArray, index: Int, sample: Float) {
        val pcm = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        buffer[index] = (pcm and 0xFF).toByte()
        buffer[index + 1] = (pcm shr 8).toByte()
    }

    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        companion object {
            fun highPass(sampleRateHz: Int, frequencyHz: Float, quality: Float): Biquad {
                val omega = 2.0 * PI * frequencyHz / sampleRateHz
                val cosine = cos(omega)
                val alpha = sin(omega) / (2.0 * quality)
                return fromCoefficients(
                    b0 = (1.0 + cosine) / 2.0,
                    b1 = -(1.0 + cosine),
                    b2 = (1.0 + cosine) / 2.0,
                    a0 = 1.0 + alpha,
                    a1 = -2.0 * cosine,
                    a2 = 1.0 - alpha,
                )
            }

            fun peaking(
                sampleRateHz: Int,
                frequencyHz: Float,
                gainDb: Float,
                quality: Float,
            ): Biquad {
                val omega = 2.0 * PI * frequencyHz / sampleRateHz
                val cosine = cos(omega)
                val alpha = sin(omega) / (2.0 * quality)
                val amplitude = 10.0.pow(gainDb / 40.0)
                return fromCoefficients(
                    b0 = 1.0 + alpha * amplitude,
                    b1 = -2.0 * cosine,
                    b2 = 1.0 - alpha * amplitude,
                    a0 = 1.0 + alpha / amplitude,
                    a1 = -2.0 * cosine,
                    a2 = 1.0 - alpha / amplitude,
                )
            }

            private fun fromCoefficients(
                b0: Double,
                b1: Double,
                b2: Double,
                a0: Double,
                a1: Double,
                a2: Double,
            ): Biquad = Biquad(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat(),
            )
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val PCM16_BYTES = 2
        const val STEREO_FRAME_BYTES = PCM16_BYTES * 2
        const val PCM16_SCALE = 32_768f
        const val HIGH_PASS_HZ = 120f
        const val LOW_MID_HZ = 300f
        const val LOW_MID_GAIN_DB = -3f
        const val PRESENCE_HZ = 2_800f
        const val PRESENCE_GAIN_DB = 2.5f
        const val FILTER_Q = 0.707f
        const val LIMITER_THRESHOLD = 0.85f
    }
}
