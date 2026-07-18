package com.zhentech.tools

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.StateFlow

internal interface HfpStreamingGateway {
    fun hasAvailableHfpDevice(): Boolean
    fun start(resultCode: Int, projectionData: Intent)
    fun stop()
}

internal class HfpStreamingController(
    private val gateway: HfpStreamingGateway,
    private val stateStore: StreamingStateStore = AppStreamingState.store,
) {
    val state: StateFlow<StreamingState> = stateStore.state

    fun hasAvailableHfpDevice(): Boolean = gateway.hasAvailableHfpDevice()

    fun start(resultCode: Int, projectionData: Intent) {
        if (!gateway.hasAvailableHfpDevice() || !stateStore.requestStart()) return
        try {
            gateway.start(resultCode, projectionData)
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

    override fun start(resultCode: Int, projectionData: Intent) {
        HfpStreamingService.start(appContext, resultCode, projectionData)
    }

    override fun stop() {
        HfpStreamingService.stop(appContext)
    }
}
