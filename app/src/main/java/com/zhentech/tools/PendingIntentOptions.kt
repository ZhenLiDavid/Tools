package com.zhentech.tools

import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle

@Suppress("DEPRECATION")
internal fun backgroundActivityPendingIntentOptions(): Bundle? =
    if (Build.VERSION.SDK_INT >= 35) {
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(mode)
            .toBundle()
    } else {
        null
    }
