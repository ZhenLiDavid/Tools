package com.zhentech.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HfpStreamingService : Service() {
    private val routingExecutor = Executors.newSingleThreadExecutor()
    private val resourceLock = Any()
    private val sessionId = AtomicLong(0)
    private val stopping = AtomicBoolean(false)
    private val callInterrupted = AtomicBoolean(false)

    private lateinit var audioManager: AudioManager

    private var selectedDevice: AudioDeviceInfo? = null
    private var previousAudioMode: Int? = null
    private var routeAnchor: CommunicationRouteAnchor? = null
    private var speechEqualizer: PodcastSpeechEqualizer? = null
    private var ownsAudioMode = false
    private var communicationDeviceRequested = false
    private var routeConfirmed = false
    private var audioMonitoringRegistered = false
    private var serviceIsForeground = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            val selectedId = synchronized(resourceLock) { selectedDevice?.id } ?: return
            if (removedDevices.any { it.id == selectedId }) {
                stopStreaming()
            }
        }
    }

    private val communicationDeviceChangedListener =
        AudioManager.OnCommunicationDeviceChangedListener { current ->
            val expectedDeviceId = synchronized(resourceLock) {
                selectedDevice?.id?.takeIf { routeConfirmed }
            } ?: return@OnCommunicationDeviceChangedListener
            if (current?.id != expectedDeviceId) {
                stopStreaming()
            }
        }

    private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
        val routingState = synchronized(resourceLock) {
            Triple(selectedDevice != null, routeConfirmed, ownsAudioMode)
        }
        if (!routingState.first) return@OnModeChangedListener

        if (HfpRoutingPolicy.isPhoneCallMode(mode)) {
            callInterrupted.set(true)
            stopStreaming()
        } else if (
            HfpRoutingPolicy.shouldStopForModeChange(
                routeConfirmed = routingState.second,
                ownsAudioMode = routingState.third,
                mode = mode,
            )
        ) {
            stopStreaming()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                setStreamingRequested(true)
                launchRoutingSession()
            }

            ACTION_STOP -> stopStreaming(clearRequest = true)

            null -> {
                if (isStreamingRequested()) {
                    launchRoutingSession()
                } else {
                    stopSelf()
                }
            }
        }
        return if (isStreamingRequested()) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        stopStreaming(stopSelfAfterCleanup = false, clearRequest = false)
        routingExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchRoutingSession() {
        val newSessionId = beginNewSession()
        startAsForegroundService()
        routingExecutor.execute { startRoute(newSessionId) }
    }

    private fun beginNewSession(): Long {
        stopStreaming(stopSelfAfterCleanup = false, clearRequest = false)
        callInterrupted.set(false)
        return sessionId.incrementAndGet()
    }

    private fun startRoute(id: Long) {
        var started = false
        try {
            if (!HfpRoutingPolicy.canStart(audioManager.mode)) return

            val device = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } ?: return
            if (!isCurrentSession(id)) return

            synchronized(resourceLock) {
                selectedDevice = device
                previousAudioMode = audioManager.mode
            }
            if (!registerAudioMonitoring(id)) return

            val anchor = CommunicationRouteAnchor.create()
            synchronized(resourceLock) {
                routeAnchor = anchor
            }
            anchor.start()
            if (!isCurrentSession(id)) return

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            synchronized(resourceLock) {
                ownsAudioMode = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
            }
            if (!synchronized(resourceLock) { ownsAudioMode } || !isCurrentSession(id)) return

            if (!audioManager.setCommunicationDevice(device)) return
            synchronized(resourceLock) {
                communicationDeviceRequested = true
            }
            if (!awaitSelectedDevice(device, id) || !isCurrentSession(id)) return

            val equalizer = runCatching { PodcastSpeechEqualizer.create() }
                .onFailure { exception ->
                    Log.w(TAG, "HFP clarity equalizer unavailable; using direct HFP route", exception)
                }
                .getOrNull()
            val equalizerAccepted = synchronized(resourceLock) {
                if (isCurrentSession(id)) {
                    speechEqualizer = equalizer
                    true
                } else {
                    false
                }
            }
            if (!equalizerAccepted) {
                equalizer?.close()
                return
            }
            equalizer?.let {
                Log.i(
                    TAG,
                    "HFP clarity EQ active: " + it.bandSettings.joinToString { band ->
                        "${band.centerFrequencyHz}Hz=${band.levelMillibels / 100f}dB"
                    },
                )
            }

            started = synchronized(resourceLock) {
                if (!isCurrentSession(id)) {
                    false
                } else {
                    routeConfirmed = true
                    AppStreamingState.store.markStarted()
                    true
                }
            }
            if (started) {
                HfpStreamingWidget.updateAll(this, StreamingState.On)
                anchor.pumpWhile { isCurrentSession(id) }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to start direct HFP routing", exception)
        } finally {
            if (isCurrentSession(id)) stopStreaming()
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

    private fun registerAudioMonitoring(id: Long): Boolean = synchronized(resourceLock) {
        if (!isCurrentSession(id)) return@synchronized false
        if (audioMonitoringRegistered) return@synchronized true

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        audioManager.addOnCommunicationDeviceChangedListener(
            mainExecutor,
            communicationDeviceChangedListener,
        )
        audioManager.addOnModeChangedListener(mainExecutor, modeChangedListener)
        audioMonitoringRegistered = true
        true
    }

    private fun stopStreaming(
        stopSelfAfterCleanup: Boolean = true,
        clearRequest: Boolean = true,
    ) {
        if (!stopping.compareAndSet(false, true)) return
        try {
            if (clearRequest) setStreamingRequested(false)
            sessionId.incrementAndGet()
            AppStreamingState.store.markStopped()
            HfpStreamingWidget.updateAll(this, StreamingState.Off)

            val resources = synchronized(resourceLock) {
                RoutingResources(
                    previousMode = previousAudioMode,
                    anchor = routeAnchor,
                    speechEqualizer = speechEqualizer,
                    ownsAudioMode = ownsAudioMode,
                    communicationDeviceRequested = communicationDeviceRequested,
                ).also {
                    selectedDevice = null
                    previousAudioMode = null
                    routeAnchor = null
                    speechEqualizer = null
                    ownsAudioMode = false
                    communicationDeviceRequested = false
                    routeConfirmed = false
                }
            }
            unregisterAudioMonitoring()
            resources.speechEqualizer?.close()
            resources.anchor?.close()
            if (resources.communicationDeviceRequested) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            if (HfpRoutingPolicy.shouldRestorePreviousMode(
                    ownsAudioMode = resources.ownsAudioMode,
                    callInterrupted = callInterrupted.get(),
                    currentMode = audioManager.mode,
                )
            ) {
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
        val wasRegistered = synchronized(resourceLock) {
            audioMonitoringRegistered.also { audioMonitoringRegistered = false }
        }
        if (!wasRegistered) return

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.removeOnCommunicationDeviceChangedListener(communicationDeviceChangedListener)
        audioManager.removeOnModeChangedListener(modeChangedListener)
    }

    private fun isCurrentSession(id: Long): Boolean = id == sessionId.get()

    private fun startAsForegroundService() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
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

    private data class RoutingResources(
        val previousMode: Int?,
        val anchor: CommunicationRouteAnchor?,
        val speechEqualizer: PodcastSpeechEqualizer?,
        val ownsAudioMode: Boolean,
        val communicationDeviceRequested: Boolean,
    )

    private fun setStreamingRequested(requested: Boolean) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit {
            putBoolean(KEY_STREAMING_REQUESTED, requested)
        }
    }

    private fun isStreamingRequested(): Boolean =
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(KEY_STREAMING_REQUESTED, false)

    companion object {
        private const val ACTION_START = "com.zhentech.tools.action.START_HFP_STREAMING"
        private const val ACTION_STOP = "com.zhentech.tools.action.STOP_HFP_STREAMING"
        private const val NOTIFICATION_CHANNEL_ID = "hfp_streaming"
        private const val NOTIFICATION_ID = 1
        private const val PREFERENCES_NAME = "hfp_streaming_service"
        private const val KEY_STREAMING_REQUESTED = "streaming_requested"
        private const val ROUTE_TIMEOUT_SECONDS = 10L
        private const val TAG = "HfpStreamingService"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HfpStreamingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HfpStreamingService::class.java).setAction(ACTION_STOP),
            )
        }

        internal fun startPendingIntent(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                1,
                Intent(context, HfpStreamingService::class.java).setAction(ACTION_START),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        internal fun stopPendingIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, HfpStreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
