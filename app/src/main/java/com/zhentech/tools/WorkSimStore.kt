package com.zhentech.tools

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalTime

internal class WorkSimStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun readSchedule(): WorkSimSchedule? {
        if (!preferences.getBoolean(KEY_CONFIGURED, false)) return null
        return WorkSimSchedule(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            slotIndex = preferences.getInt(KEY_SLOT, 1),
            simLabel = preferences.getString(KEY_LABEL, "Work SIM") ?: "Work SIM",
            isEmbedded = preferences.getBoolean(KEY_EMBEDDED, true),
            days = decodeDays(preferences.getInt(KEY_DAYS, DEFAULT_WEEKDAYS)),
            start = LocalTime.ofSecondOfDay(
                preferences.getInt(KEY_START_MINUTES, 9 * 60).toLong() * 60,
            ),
            end = LocalTime.ofSecondOfDay(
                preferences.getInt(KEY_END_MINUTES, 17 * 60).toLong() * 60,
            ),
        )
    }

    fun writeSchedule(schedule: WorkSimSchedule) {
        preferences.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putInt(KEY_SLOT, schedule.slotIndex)
            .putString(KEY_LABEL, schedule.simLabel)
            .putBoolean(KEY_EMBEDDED, schedule.isEmbedded)
            .putInt(KEY_DAYS, encodeDays(schedule.days))
            .putInt(KEY_START_MINUTES, schedule.start.hour * 60 + schedule.start.minute)
            .putInt(KEY_END_MINUTES, schedule.end.hour * 60 + schedule.end.minute)
            .apply()
    }

    fun readLastPowerState(): Boolean? = when {
        !preferences.contains(KEY_LAST_POWER_STATE) -> null
        else -> preferences.getBoolean(KEY_LAST_POWER_STATE, false)
    }

    fun writeLastPowerState(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_LAST_POWER_STATE, enabled).apply()
    }

    fun readBackendStatus(default: WorkSimBackendStatus): WorkSimBackendStatus =
        preferences.getString(KEY_BACKEND_STATUS, null)
            ?.let { stored -> WorkSimBackendStatus.entries.firstOrNull { it.name == stored } }
            ?: default

    fun writeBackendStatus(status: WorkSimBackendStatus) {
        preferences.edit().putString(KEY_BACKEND_STATUS, status.name).apply()
    }

    private fun encodeDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { result, day -> result or (1 shl (day.value - 1)) }

    private fun decodeDays(encoded: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(linkedSetOf()) { day ->
            encoded and (1 shl (day.value - 1)) != 0
        }

    private companion object {
        const val PREFERENCES_NAME = "work_sim"
        const val KEY_CONFIGURED = "configured"
        const val KEY_ENABLED = "enabled"
        const val KEY_SLOT = "slot"
        const val KEY_LABEL = "label"
        const val KEY_EMBEDDED = "embedded"
        const val KEY_DAYS = "days"
        const val KEY_START_MINUTES = "start_minutes"
        const val KEY_END_MINUTES = "end_minutes"
        const val KEY_LAST_POWER_STATE = "last_power_state"
        const val KEY_BACKEND_STATUS = "backend_status"
        const val DEFAULT_WEEKDAYS = 0b0011111
    }
}
