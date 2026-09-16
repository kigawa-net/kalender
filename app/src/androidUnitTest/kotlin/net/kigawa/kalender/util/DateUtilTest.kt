@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilTest {

    @Test
    fun when_formatIsoDateAtMidnight_given_localMidnightInPositiveOffsetZone_then_returnsSameCalendarDateAtMidnight() {
        val zone = TimeZone.of("Asia/Tokyo")
        val date = LocalDate(2026, 9, 20)
        val startMs = date.atStartOfDayIn(zone).toEpochMilliseconds()

        val result = formatIsoDateAtMidnight(startMs, zone)

        assertEquals("2026-09-20T00:00:00.0000000", result)
    }

    @Test
    fun when_formatIsoDateAtMidnight_given_localMidnightInNegativeOffsetZone_then_returnsSameCalendarDateAtMidnight() {
        val zone = TimeZone.of("America/Los_Angeles")
        val date = LocalDate(2026, 9, 20)
        val startMs = date.atStartOfDayIn(zone).toEpochMilliseconds()

        val result = formatIsoDateAtMidnight(startMs, zone)

        assertEquals("2026-09-20T00:00:00.0000000", result)
    }
}
