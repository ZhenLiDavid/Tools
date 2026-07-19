package com.zhentech.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/** Silent playback that keeps this UID active as Android's communication-mode owner. */
internal class CommunicationRouteAnchor private constructor(
    private val audioTrack: AudioTrack,
    private val silence: ByteArray,
) : AutoCloseable {
    val isPlaying: Boolean
        get() = audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING

    fun start() {
        val primedBytes = audioTrack.write(
            silence,
            0,
            silence.size,
            AudioTrack.WRITE_BLOCKING,
        )
        check(primedBytes == silence.size)
        audioTrack.play()
        check(isPlaying)
    }

    fun pumpWhile(shouldContinue: () -> Boolean) {
        while (shouldContinue()) {
            val written = audioTrack.write(
                silence,
                0,
                silence.size,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written < 0) return
        }
    }

    override fun close() {
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { audioTrack.release() }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 8_000
        private const val PCM_16_MONO_FRAME_BYTES = 2
        private const val MIN_SILENCE_FRAMES = 320

        fun create(): CommunicationRouteAnchor {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minimumBufferBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBufferBytes > 0)
            val bufferBytes = max(
                minimumBufferBytes,
                MIN_SILENCE_FRAMES * PCM_16_MONO_FRAME_BYTES,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                        .build(),
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED)
            return CommunicationRouteAnchor(track, ByteArray(bufferBytes))
        }
    }
}
