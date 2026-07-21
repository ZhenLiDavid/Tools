package com.zhentech.tools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime

internal class WorkSimController(
    context: Context,
    private val store: WorkSimStore = WorkSimStore(context),
    private val simCatalog: SimCatalog = SimCatalog(context),
    private val alarmScheduler: WorkSimAlarmScheduler = WorkSimAlarmScheduler(context),
) {
    private val mutableState = MutableStateFlow(buildState())
    val state: StateFlow<WorkSimUiState> = mutableState.asStateFlow()

    fun refresh() {
        alarmScheduler.scheduleNext(store.readSchedule())
        mutableState.value = buildState()
    }

    fun save(schedule: WorkSimSchedule) {
        require(schedule.isValid) { "Work SIM schedule is incomplete" }
        store.writeSchedule(schedule)
        alarmScheduler.scheduleNext(schedule)
        mutableState.value = buildState()
    }

    private fun buildState(
        now: ZonedDateTime = ZonedDateTime.now(),
    ): WorkSimUiState {
        val schedule = store.readSchedule()
        val sims = simCatalog.availableSims().toMutableList()
        if (schedule != null && sims.none { it.slotIndex == schedule.slotIndex }) {
            sims += SimDescriptor(schedule.slotIndex, schedule.simLabel, schedule.isEmbedded)
        }
        return WorkSimUiState(
            schedule = schedule,
            availableSims = sims.sortedBy(SimDescriptor::slotIndex),
            nextTransition = schedule?.nextTransitionAfter(now),
            preciseSchedulingAvailable = alarmScheduler.canSchedulePrecisely,
        )
    }
}
