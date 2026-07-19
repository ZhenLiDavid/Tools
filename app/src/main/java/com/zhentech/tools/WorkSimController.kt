package com.zhentech.tools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class WorkSimController(
    context: Context,
    private val store: WorkSimStore = WorkSimStore(context),
    private val gateway: SimPowerGateway = createSimPowerGateway(context),
    private val alarmScheduler: WorkSimAlarmScheduler = WorkSimAlarmScheduler(context),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
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
        mutableState.value = buildState()
        reconcileAsync()
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun reconcileAsync() {
        val schedule = store.readSchedule() ?: return
        if (!schedule.enabled) {
            alarmScheduler.scheduleNext(null)
            mutableState.value = buildState()
            return
        }

        mutableState.value = buildState(backendOverride = WorkSimBackendStatus.Applying)
        executor.execute {
            WorkSimAutomation(
                store = store,
                gateway = gateway,
                alarmScheduler = alarmScheduler,
            ).reconcile()
            mutableState.value = buildState()
        }
    }

    private fun buildState(
        now: ZonedDateTime = ZonedDateTime.now(),
        backendOverride: WorkSimBackendStatus? = null,
    ): WorkSimUiState {
        val schedule = store.readSchedule()
        val sims = gateway.availableSims().toMutableList()
        if (schedule != null && sims.none { it.slotIndex == schedule.slotIndex }) {
            sims += SimDescriptor(schedule.slotIndex, schedule.simLabel, schedule.isEmbedded)
        }
        return WorkSimUiState(
            schedule = schedule,
            availableSims = sims.sortedBy(SimDescriptor::slotIndex),
            isPoweredOn = store.readLastPowerState()
                ?: schedule?.isActiveAt(now),
            nextTransition = schedule?.nextTransitionAfter(now),
            backendStatus = backendOverride ?: store.readBackendStatus(gateway.defaultStatus),
            preciseSchedulingAvailable = alarmScheduler.canSchedulePrecisely,
        )
    }
}

internal class WorkSimAutomation(
    private val store: WorkSimStore,
    private val gateway: SimPowerGateway,
    private val alarmScheduler: WorkSimAlarmScheduler,
) {
    constructor(context: Context) : this(
        store = WorkSimStore(context),
        gateway = createSimPowerGateway(context),
        alarmScheduler = WorkSimAlarmScheduler(context),
    )

    fun reconcile(now: ZonedDateTime = ZonedDateTime.now()): SimPowerResult? {
        val schedule = store.readSchedule()
        alarmScheduler.scheduleNext(schedule, now)
        if (schedule == null || !schedule.enabled || !schedule.isValid) return null

        val shouldBeOn = schedule.isActiveAt(now)
        val result = gateway.setPower(schedule.slotIndex, shouldBeOn)
        if (result.success) store.writeLastPowerState(shouldBeOn)
        store.writeBackendStatus(result.backendStatus)
        return result
    }
}
