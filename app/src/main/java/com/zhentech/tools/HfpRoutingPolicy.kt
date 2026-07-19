package com.zhentech.tools

import android.media.AudioManager

internal object HfpRoutingPolicy {
    fun canStart(currentMode: Int): Boolean = currentMode == AudioManager.MODE_NORMAL

    fun isPhoneCallMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_CALL_SCREENING

    fun shouldStopForModeChange(
        routeConfirmed: Boolean,
        ownsAudioMode: Boolean,
        mode: Int,
    ): Boolean {
        if (isPhoneCallMode(mode)) return true
        if (routeConfirmed) return mode != AudioManager.MODE_IN_COMMUNICATION
        return !ownsAudioMode &&
            mode != AudioManager.MODE_NORMAL &&
            mode != AudioManager.MODE_IN_COMMUNICATION
    }

    fun shouldRestorePreviousMode(
        ownsAudioMode: Boolean,
        callInterrupted: Boolean,
        currentMode: Int,
    ): Boolean = ownsAudioMode &&
        !callInterrupted &&
        currentMode == AudioManager.MODE_IN_COMMUNICATION
}
