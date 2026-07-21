package com.zhentech.tools

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.ZonedDateTime

internal class WorkSimAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    val canSchedulePrecisely: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.SCHEDULE_EXACT_ALARM) ==
            PackageManager.PERMISSION_GRANTED || alarmManager.canScheduleExactAlarms()

    fun scheduleNext(schedule: WorkSimSchedule?, now: ZonedDateTime = ZonedDateTime.now()) {
        alarmManager.cancel(transitionPendingIntent())
        alarmManager.cancel(settingsPendingIntent())

        val transition = schedule?.nextTransitionAfter(now) ?: return
        val triggerAtMillis = transition.at.toInstant().toEpochMilli()
        val scheduledTransition = transitionPendingIntent(transition)
        if (canSchedulePrecisely) {
            setExact(triggerAtMillis, scheduledTransition)
            setExact(triggerAtMillis, settingsPendingIntent())
        } else {
            setInexact(triggerAtMillis, scheduledTransition)
            setInexact(triggerAtMillis, settingsPendingIntent())
        }
    }

    private fun setExact(triggerAtMillis: Long, operation: PendingIntent) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    }

    private fun setInexact(triggerAtMillis: Long, operation: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    }

    private fun transitionPendingIntent(
        transition: WorkSimTransition? = null,
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        TRANSITION_REQUEST_CODE,
        Intent(appContext, WorkSimAlarmReceiver::class.java)
            .setAction(ACTION_TRANSITION)
            .apply {
                transition?.let {
                    putExtra(
                        WorkSimTransitionActivity.EXTRA_TRANSITION_EPOCH_MILLIS,
                        it.at.toInstant().toEpochMilli(),
                    )
                    putExtra(WorkSimTransitionActivity.EXTRA_TURN_ON, it.powersOn)
                }
            },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun settingsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        SETTINGS_REQUEST_CODE,
        Intent(appContext, WorkSimTransitionActivity::class.java)
            .setAction(WorkSimTransitionActivity.ACTION_OPEN_SETTINGS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        backgroundActivityPendingIntentOptions(),
    )

    companion object {
        const val ACTION_TRANSITION = "com.zhentech.tools.WORK_SIM_TRANSITION"
        private const val TRANSITION_REQUEST_CODE = 2
        private const val SETTINGS_REQUEST_CODE = 3
    }
}
