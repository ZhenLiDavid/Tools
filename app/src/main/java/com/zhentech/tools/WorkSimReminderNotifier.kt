package com.zhentech.tools

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal class WorkSimReminderNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun show(schedule: WorkSimSchedule, turnOn: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel()
        val action = if (turnOn) "Turn on" else "Turn off"
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sim_card)
            .setContentTitle("$action ${schedule.simLabel}")
            .setContentText("Open SIM settings to make the change.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(settingsPendingIntent())
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SIM schedule",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders to change a scheduled SIM state"
            },
        )
    }

    private fun settingsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        SETTINGS_REQUEST_CODE,
        android.content.Intent(appContext, WorkSimTransitionActivity::class.java)
            .setAction(WorkSimTransitionActivity.ACTION_OPEN_SETTINGS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        backgroundActivityPendingIntentOptions(),
    )

    private companion object {
        const val CHANNEL_ID = "work_sim_schedule"
        const val NOTIFICATION_ID = 2202
        const val SETTINGS_REQUEST_CODE = 4
    }
}
