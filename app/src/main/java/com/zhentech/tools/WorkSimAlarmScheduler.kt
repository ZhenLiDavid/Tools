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
        val pendingIntent = transitionPendingIntent()
        alarmManager.cancel(pendingIntent)

        val transition = schedule?.nextTransitionAfter(now) ?: return
        val triggerAtMillis = transition.at.toInstant().toEpochMilli()
        if (canSchedulePrecisely) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun transitionPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        TRANSITION_REQUEST_CODE,
        Intent(appContext, WorkSimAlarmReceiver::class.java).setAction(ACTION_TRANSITION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_TRANSITION = "com.zhentech.tools.WORK_SIM_TRANSITION"
        private const val TRANSITION_REQUEST_CODE = 2
    }
}
