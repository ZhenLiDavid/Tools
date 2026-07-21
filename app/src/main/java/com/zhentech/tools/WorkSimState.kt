package com.zhentech.tools

import java.time.ZonedDateTime

internal data class SimDescriptor(
    val slotIndex: Int,
    val label: String,
    val isEmbedded: Boolean,
)

internal data class WorkSimUiState(
    val schedule: WorkSimSchedule? = null,
    val availableSims: List<SimDescriptor> = emptyList(),
    val nextTransition: WorkSimTransition? = null,
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
            nextTransition = schedule.nextTransitionAfter(now),
        )
    }
}
