package com.zhentech.tools

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.StateFlow

internal interface HfpStreamingGateway {
    fun hasAvailableHfpDevice(): Boolean
    fun start()
    fun stop()
}

internal class HfpStreamingController(
    private val gateway: HfpStreamingGateway,
    private val stateStore: StreamingStateStore = AppStreamingState.store,
) {
    val state: StateFlow<StreamingState> = stateStore.state

    fun hasAvailableHfpDevice(): Boolean = gateway.hasAvailableHfpDevice()

    fun start() {
        if (!gateway.hasAvailableHfpDevice() || !stateStore.requestStart()) return
        try {
            gateway.start()
        } catch (_: SecurityException) {
            stateStore.markStopped()
        }
    }

    fun stop() {
        stateStore.markStopped()
        gateway.stop()
    }
}

internal class AndroidHfpStreamingGateway(context: Context) : HfpStreamingGateway {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    override fun hasAvailableHfpDevice(): Boolean = try {
        audioManager.availableCommunicationDevices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    } catch (_: SecurityException) {
        false
    }

    override fun start() {
        HfpStreamingService.start(appContext)
    }

    override fun stop() {
        HfpStreamingService.stop(appContext)
    }
}
