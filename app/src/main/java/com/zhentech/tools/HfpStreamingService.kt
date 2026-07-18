package com.zhentech.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class HfpStreamingService : Service() {
    private val streamingExecutor = Executors.newSingleThreadExecutor()
    private val resourceLock = Any()
    private val sessionId = AtomicLong(0)
    private val stopping = AtomicBoolean(false)
    private val callInterrupted = AtomicBoolean(false)

    private lateinit var audioManager: AudioManager
    private lateinit var projectionManager: MediaProjectionManager

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var mediaProjection: MediaProjection? = null
    private var selectedDevice: AudioDeviceInfo? = null
    private var previousAudioMode: Int? = null
    private var audioMonitoringRegistered = false
    private var serviceIsForeground = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopStreaming()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            val selectedId = synchronized(resourceLock) { selectedDevice?.id } ?: return
            if (removedDevices.any { it.id == selectedId }) {
                stopStreaming()
            }
        }
    }

    private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
        if (mode == AudioManager.MODE_IN_CALL && isSessionActive()) {
            callInterrupted.set(true)
            stopStreaming()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val projectionData = intent.projectionData() ?: run {
                    stopStreaming()
                    return START_NOT_STICKY
                }
                val projectionResultCode = intent.getIntExtra(
                    EXTRA_PROJECTION_RESULT_CODE,
                    android.app.Activity.RESULT_CANCELED,
                )
                val newSessionId = beginNewSession()
                startAsForegroundService()
                streamingExecutor.execute {
                    runSession(newSessionId, projectionResultCode, projectionData)
                }
            }

            ACTION_STOP -> stopStreaming()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopStreaming(stopSelfAfterCleanup = false)
        streamingExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginNewSession(): Long {
        stopStreaming(stopSelfAfterCleanup = false)
        callInterrupted.set(false)
        return sessionId.incrementAndGet()
    }

    private fun runSession(id: Long, projectionResultCode: Int, projectionData: Intent) {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } ?: return
            if (!isCurrentSession(id)) return

            synchronized(resourceLock) {
                selectedDevice = device
                previousAudioMode = audioManager.mode
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (!audioManager.setCommunicationDevice(device) || !awaitSelectedDevice(device, id)) return

            val projection = projectionManager.getMediaProjection(projectionResultCode, projectionData)
                ?: return
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            synchronized(resourceLock) {
                mediaProjection = projection
            }

            val captureConfiguration = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val outputFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val inputBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val outputBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(inputBufferSize > 0 && outputBufferSize > 0)
            val inputBufferBytes = max(inputBufferSize, outputBufferSize * 2)
            val outputBufferBytes = max(outputBufferSize, inputBufferBytes / 2)
            val record = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(inputBufferBytes)
                .setAudioPlaybackCaptureConfig(captureConfiguration)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(outputFormat)
                .setBufferSizeInBytes(outputBufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(record.state == AudioRecord.STATE_INITIALIZED)
            check(track.state == AudioTrack.STATE_INITIALIZED)
            if (!isCurrentSession(id)) {
                record.release()
                track.release()
                return
            }

            synchronized(resourceLock) {
                audioRecord = record
                audioTrack = track
            }
            registerAudioMonitoring()
            record.startRecording()
            track.play()
            if (!isCurrentSession(id)) return

            AppStreamingState.store.markStarted()
            copyAudioUntilStopped(id, record, track, inputBufferBytes)
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to start HFP streaming", exception)
        } finally {
            if (isCurrentSession(id)) stopStreaming()
        }
    }

    private fun copyAudioUntilStopped(
        id: Long,
        record: AudioRecord,
        track: AudioTrack,
        bufferSize: Int,
    ) {
        val inputBuffer = ByteArray(bufferSize)
        val outputBuffer = ByteArray(bufferSize / 2)
        val processor = SpeechClarityProcessor()
        while (isCurrentSession(id)) {
            val read = record.read(inputBuffer, 0, inputBuffer.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) return
            val processedBytes = processor.processStereoPcm16(inputBuffer, read, outputBuffer)
            val written = track.write(outputBuffer, 0, processedBytes, AudioTrack.WRITE_BLOCKING)
            if (written < 0) return
        }
    }

    private fun awaitSelectedDevice(device: AudioDeviceInfo, id: Long): Boolean {
        if (audioManager.communicationDevice?.id == device.id) return true

        val routeSelected = CountDownLatch(1)
        val listener = AudioManager.OnCommunicationDeviceChangedListener { current ->
            if (isCurrentSession(id) && current?.id == device.id) routeSelected.countDown()
        }
        audioManager.addOnCommunicationDeviceChangedListener(mainExecutor, listener)
        return try {
            audioManager.communicationDevice?.id == device.id ||
                routeSelected.await(ROUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            audioManager.removeOnCommunicationDeviceChangedListener(listener)
        }
    }

    private fun registerAudioMonitoring() {
        if (audioMonitoringRegistered) return
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        audioManager.addOnModeChangedListener(mainExecutor, modeChangedListener)
        audioMonitoringRegistered = true
    }

    private fun stopStreaming(stopSelfAfterCleanup: Boolean = true) {
        if (!stopping.compareAndSet(false, true)) return
        try {
            sessionId.incrementAndGet()
            AppStreamingState.store.markStopped()

            val resources = synchronized(resourceLock) {
                StreamingResources(
                    record = audioRecord,
                    track = audioTrack,
                    projection = mediaProjection,
                    previousMode = previousAudioMode,
                ).also {
                    audioRecord = null
                    audioTrack = null
                    mediaProjection = null
                    selectedDevice = null
                    previousAudioMode = null
                }
            }
            unregisterAudioMonitoring()
            runCatching { resources.record?.stop() }
            runCatching { resources.track?.pause() }
            runCatching { resources.track?.flush() }
            runCatching { resources.record?.release() }
            runCatching { resources.track?.release() }
            runCatching { resources.projection?.unregisterCallback(projectionCallback) }
            runCatching { resources.projection?.stop() }
            runCatching { audioManager.clearCommunicationDevice() }
            if (!callInterrupted.get() && audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                resources.previousMode?.let { audioManager.mode = it }
            }

            if (serviceIsForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                serviceIsForeground = false
            }
            if (stopSelfAfterCleanup) stopSelf()
        } finally {
            stopping.set(false)
        }
    }

    private fun unregisterAudioMonitoring() {
        if (!audioMonitoringRegistered) return
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.removeOnModeChangedListener(modeChangedListener)
        audioMonitoringRegistered = false
    }

    private fun isCurrentSession(id: Long): Boolean = id == sessionId.get()

    private fun isSessionActive(): Boolean = synchronized(resourceLock) {
        selectedDevice != null
    }

    private fun startAsForegroundService() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        serviceIsForeground = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.streaming_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bluetooth)
        .setContentTitle(getString(R.string.streaming_notification_title))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .addAction(
            R.drawable.ic_bluetooth,
            getString(R.string.streaming_notification_stop),
            PendingIntent.getService(
                this,
                0,
                Intent(this, HfpStreamingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private data class StreamingResources(
        val record: AudioRecord?,
        val track: AudioTrack?,
        val projection: MediaProjection?,
        val previousMode: Int?,
    )

    @Suppress("DEPRECATION")
    private fun Intent.projectionData(): Intent? = getParcelableExtra(EXTRA_PROJECTION_DATA)

    companion object {
        private const val ACTION_START = "com.zhentech.tools.action.START_HFP_STREAMING"
        private const val ACTION_STOP = "com.zhentech.tools.action.STOP_HFP_STREAMING"
        private const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
        private const val NOTIFICATION_CHANNEL_ID = "hfp_streaming"
        private const val NOTIFICATION_ID = 1
        private const val ROUTE_TIMEOUT_SECONDS = 10L
        private const val SAMPLE_RATE_HZ = 16_000
        private const val TAG = "HfpStreamingService"

        fun start(context: Context, resultCode: Int, projectionData: Intent) {
            val startIntent = Intent(context, HfpStreamingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode)
                .putExtra(EXTRA_PROJECTION_DATA, projectionData)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HfpStreamingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
