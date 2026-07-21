package com.zhentech.tools

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings

internal object SimSettingsNavigator {
    fun open(context: Context): Boolean {
        val actions = listOf(
            Settings.ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS,
            ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
        for (action in actions) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next system settings surface.
            }
        }
        return false
    }

    private const val ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS =
        "android.telephony.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS"
}
