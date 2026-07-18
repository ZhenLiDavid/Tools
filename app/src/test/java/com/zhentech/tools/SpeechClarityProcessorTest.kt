package com.zhentech.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechClarityProcessorTest {
    @Test
    fun oppositeStereoSamplesCancelToMonoSilence() {
        val input = pcmStereo(12_000, -12_000)
        val output = ByteArray(2)

        val processedBytes = SpeechClarityProcessor().processStereoPcm16(input, input.size, output)

        assertEquals(2, processedBytes)
        assertEquals(0, readPcm16(output, 0).toInt())
    }

    @Test
    fun loudSpeechFramesAreSoftLimitedWithoutPcmOverflow() {
        val input = ByteArray(4 * 400)
        repeat(400) { frame ->
            writePcm16(input, frame * 4, Short.MAX_VALUE)
            writePcm16(input, frame * 4 + 2, Short.MAX_VALUE)
        }
        val output = ByteArray(input.size / 2)

        val processedBytes = SpeechClarityProcessor().processStereoPcm16(input, input.size, output)

        assertEquals(output.size, processedBytes)
        assertTrue((0 until processedBytes step 2).all { index ->
            readPcm16(output, index).toInt() in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()
        })
    }

    private fun pcmStereo(left: Short, right: Short): ByteArray = ByteArray(4).also {
        writePcm16(it, 0, left)
        writePcm16(it, 2, right)
    }

    private fun writePcm16(buffer: ByteArray, index: Int, sample: Short) {
        buffer[index] = (sample.toInt() and 0xFF).toByte()
        buffer[index + 1] = (sample.toInt() shr 8).toByte()
    }

    private fun readPcm16(buffer: ByteArray, index: Int): Short {
        val low = buffer[index].toInt() and 0xFF
        val high = buffer[index + 1].toInt()
        return ((high shl 8) or low).toShort()
    }
}
