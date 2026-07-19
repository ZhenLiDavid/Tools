package com.zhentech.tools

import java.time.ZonedDateTime

internal data class SimDescriptor(
    val slotIndex: Int,
    val label: String,
    val isEmbedded: Boolean,
)

internal enum class WorkSimBackendStatus {
    Simulated,
    Ready,
    Applying,
    AdbUnavailable,
    Failed,
}

internal data class WorkSimUiState(
    val schedule: WorkSimSchedule? = null,
    val availableSims: List<SimDescriptor> = emptyList(),
    val isPoweredOn: Boolean? = null,
    val nextTransition: WorkSimTransition? = null,
    val backendStatus: WorkSimBackendStatus = WorkSimBackendStatus.Ready,
    val preciseSchedulingAvailable: Boolean = true,
) {
    val isConfigured: Boolean
        get() = schedule != null

    companion object {
        fun preview(
            now: ZonedDateTime = ZonedDateTime.now(),
            schedule: WorkSimSchedule = WorkSimSchedule.Default,
        ): WorkSimUiState = WorkSimUiState(
            schedule = schedule,
            availableSims = listOf(
                SimDescriptor(0, "Personal SIM", false),
                SimDescriptor(1, "Work eSIM", true),
            ),
            isPoweredOn = schedule.isActiveAt(now),
            nextTransition = schedule.nextTransitionAfter(now),
            backendStatus = WorkSimBackendStatus.Simulated,
        )
    }
}
