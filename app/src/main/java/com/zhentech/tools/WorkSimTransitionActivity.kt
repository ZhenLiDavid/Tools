package com.zhentech.tools

import android.app.Activity
import android.os.Bundle

class WorkSimTransitionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_OPEN_SETTINGS) {
            SimSettingsNavigator.open(this)
        }
        finish()
    }

    internal companion object {
        const val EXTRA_TRANSITION_EPOCH_MILLIS = "transition_epoch_millis"
        const val EXTRA_TURN_ON = "turn_on"
        const val ACTION_OPEN_SETTINGS = "com.zhentech.tools.OPEN_SIM_SETTINGS"
    }
}
