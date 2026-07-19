package com.zhentech.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class WorkSimAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread(
            {
                try {
                    WorkSimAutomation(context).reconcile()
                } finally {
                    pendingResult.finish()
                }
            },
            "work-sim-alarm",
        ).start()
    }
}
