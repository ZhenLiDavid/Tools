package com.zhentech.tools

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal data class WorkSimSchedule(
    val enabled: Boolean,
    val slotIndex: Int,
    val simLabel: String,
    val isEmbedded: Boolean,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
) {
    val isValid: Boolean
        get() = slotIndex >= 0 && days.isNotEmpty() && start != end

    fun isActiveAt(moment: ZonedDateTime): Boolean {
        if (!enabled || !isValid) return false

        val time = moment.toLocalTime()
        return if (start < end) {
            moment.dayOfWeek in days && time >= start && time < end
        } else {
            (moment.dayOfWeek in days && time >= start) ||
                (moment.dayOfWeek.minusOne() in days && time < end)
        }
    }

    fun nextTransitionAfter(moment: ZonedDateTime): WorkSimTransition? {
        if (!enabled || !isValid) return null

        return buildList {
            for (offset in -1L..8L) {
                val date = moment.toLocalDate().plusDays(offset)
                if (date.dayOfWeek !in days) continue
                add(date.at(start, moment))
                val endDate = if (start < end) date else date.plusDays(1)
                add(endDate.at(end, moment))
            }
        }
            .asSequence()
            .filter { it.isAfter(moment) }
            .distinctBy { it.toInstant() }
            .sortedBy { it.toInstant() }
            .firstNotNullOfOrNull { candidate ->
                val before = isActiveAt(candidate.minusSeconds(1))
                val after = isActiveAt(candidate.plusSeconds(1))
                if (before == after) null else WorkSimTransition(candidate, after)
            }
    }

    companion object {
        val Default = WorkSimSchedule(
            enabled = true,
            slotIndex = 1,
            simLabel = "Work eSIM",
            isEmbedded = true,
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
        )
    }
}

internal data class WorkSimTransition(
    val at: ZonedDateTime,
    val powersOn: Boolean,
)

private fun DayOfWeek.minusOne(): DayOfWeek =
    DayOfWeek.of(if (value == DayOfWeek.MONDAY.value) DayOfWeek.SUNDAY.value else value - 1)

private fun LocalDate.at(time: LocalTime, reference: ZonedDateTime): ZonedDateTime =
    ZonedDateTime.of(this, time, reference.zone)

internal fun WorkSimTransition.isSoonAfter(moment: ZonedDateTime): Boolean =
    Duration.between(moment, at).toHours() < 24
