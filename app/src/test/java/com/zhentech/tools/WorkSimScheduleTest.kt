package com.zhentech.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkSimScheduleTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val schedule = WorkSimSchedule.Default

    @Test
    fun weekdayWindowIsActiveOnlyBetweenItsBoundaries() {
        assertFalse(schedule.isActiveAt(at(2026, 7, 20, 8, 59)))
        assertTrue(schedule.isActiveAt(at(2026, 7, 20, 9, 0)))
        assertTrue(schedule.isActiveAt(at(2026, 7, 20, 16, 59)))
        assertFalse(schedule.isActiveAt(at(2026, 7, 20, 17, 0)))
        assertFalse(schedule.isActiveAt(at(2026, 7, 19, 12, 0)))
    }

    @Test
    fun nextTransitionSkipsWeekend() {
        val transition = schedule.nextTransitionAfter(at(2026, 7, 17, 18, 0))

        assertEquals(at(2026, 7, 20, 9, 0), transition?.at)
        assertEquals(true, transition?.powersOn)
    }

    @Test
    fun overnightWindowCarriesIntoFollowingDay() {
        val overnight = schedule.copy(
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
        )

        assertTrue(overnight.isActiveAt(at(2026, 7, 20, 23, 0)))
        assertTrue(overnight.isActiveAt(at(2026, 7, 21, 5, 59)))
        assertFalse(overnight.isActiveAt(at(2026, 7, 21, 6, 0)))
    }

    @Test
    fun pausedOrInvalidScheduleHasNoTransitions() {
        assertNull(schedule.copy(enabled = false).nextTransitionAfter(at(2026, 7, 20, 10, 0)))
        assertFalse(schedule.copy(start = schedule.end).isValid)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
