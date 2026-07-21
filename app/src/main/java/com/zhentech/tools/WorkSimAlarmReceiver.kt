package com.zhentech.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZonedDateTime

internal class WorkSimAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WorkSimAlarmScheduler.ACTION_TRANSITION) {
            handleTransition(context, intent)
            return
        }
        if (intent.action !in SYSTEM_ACTIONS) return
        WorkSimAlarmScheduler(context).scheduleNext(WorkSimStore(context).readSchedule())
    }

    private fun handleTransition(context: Context, intent: Intent) {
        val schedule = WorkSimStore(context).readSchedule()
        if (schedule == null || !schedule.enabled || !schedule.isValid) return

        val now = ZonedDateTime.now()
        val scheduledInstant = intent.getLongExtra(
            WorkSimTransitionActivity.EXTRA_TRANSITION_EPOCH_MILLIS,
            0L,
        )
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
        val turnOn = intent.getBooleanExtra(
            WorkSimTransitionActivity.EXTRA_TURN_ON,
            schedule.isActiveAt(now),
        )
        WorkSimReminderNotifier(context).show(schedule, turnOn)

        val nextScheduleReference = scheduledInstant
            ?.plusSeconds(1)
            ?.takeIf { it.isAfter(now.toInstant()) }
            ?.let { instant -> ZonedDateTime.ofInstant(instant, now.zone) }
            ?: now
        WorkSimAlarmScheduler(context).scheduleNext(schedule, nextScheduleReference)
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
