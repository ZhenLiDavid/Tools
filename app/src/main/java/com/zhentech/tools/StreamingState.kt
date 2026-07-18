package com.zhentech.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class StreamingState {
    Off,
    On,
}

internal class StreamingStateStore {
    private val mutableState = MutableStateFlow(StreamingState.Off)
    private var startRequested = false

    val state: StateFlow<StreamingState> = mutableState.asStateFlow()

    @Synchronized
    fun requestStart(): Boolean {
        if (startRequested || mutableState.value == StreamingState.On) return false
        startRequested = true
        return true
    }

    @Synchronized
    fun markStarted() {
        startRequested = false
        mutableState.value = StreamingState.On
    }

    @Synchronized
    fun markStopped() {
        startRequested = false
        mutableState.value = StreamingState.Off
    }
}

internal object AppStreamingState {
    val store = StreamingStateStore()
}
