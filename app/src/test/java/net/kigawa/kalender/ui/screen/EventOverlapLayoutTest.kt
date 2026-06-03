package net.kigawa.kalender.ui.screen

import net.kigawa.kalender.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class EventOverlapLayoutTest {

    private val zone = ZoneOffset.UTC
    private val base = java.time.LocalDate.of(2024, 1, 1)

    private fun minutesToMs(hour: Int, minute: Int = 0): Long =
        base.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private fun makeEvent(id: Long, startHour: Int, endHour: Int, startMin: Int = 0, endMin: Int = 0) =
        CalendarEvent(
            id = id, calendarId = 1L, title = "event$id",
            startMs = minutesToMs(startHour, startMin),
            endMs = minutesToMs(endHour, endMin),
            allDay = false, color = 0, timeZone = "UTC",
            description = "", location = "", remoteId = "",
        )

    @Test
    fun `when_no_events_then_returns_empty`() {
        assertEquals(emptyList<EventColumnLayout>(), layoutDayEvents(emptyList(), zone))
    }

    @Test
    fun `when_single_event_then_column_0_of_1`() {
        val event = makeEvent(1, 9, 10)
        val result = layoutDayEvents(listOf(event), zone)
        assertEquals(1, result.size)
        assertEquals(0, result[0].columnIndex)
        assertEquals(1, result[0].totalColumns)
    }

    @Test
    fun `when_non_overlapping_events_then_each_gets_column_0_of_1`() {
        val e1 = makeEvent(1, 9, 10)
        val e2 = makeEvent(2, 11, 12)
        val result = layoutDayEvents(listOf(e1, e2), zone)
        assertEquals(2, result.size)
        result.forEach {
            assertEquals(0, it.columnIndex)
            assertEquals(1, it.totalColumns)
        }
    }

    @Test
    fun `when_two_overlapping_events_then_placed_in_separate_columns`() {
        val e1 = makeEvent(1, 9, 11)
        val e2 = makeEvent(2, 10, 12)
        val result = layoutDayEvents(listOf(e1, e2), zone)
        assertEquals(2, result.size)
        val cols = result.map { it.columnIndex }.toSet()
        assertEquals(setOf(0, 1), cols)
        result.forEach { assertEquals(2, it.totalColumns) }
    }

    @Test
    fun `when_three_overlapping_events_then_each_in_distinct_column`() {
        val e1 = makeEvent(1, 9, 12)
        val e2 = makeEvent(2, 9, 12)
        val e3 = makeEvent(3, 9, 12)
        val result = layoutDayEvents(listOf(e1, e2, e3), zone)
        assertEquals(3, result.size)
        assertEquals(setOf(0, 1, 2), result.map { it.columnIndex }.toSet())
        result.forEach { assertEquals(3, it.totalColumns) }
    }

    @Test
    fun `when_partial_overlap_then_reuses_freed_column`() {
        val e1 = makeEvent(1, 9, 11)
        val e2 = makeEvent(2, 9, 11)
        val e3 = makeEvent(3, 11, 13)
        val result = layoutDayEvents(listOf(e1, e2, e3), zone)
        val e3Layout = result.first { it.event.id == 3L }
        assertEquals(0, e3Layout.columnIndex)
        assertEquals(1, e3Layout.totalColumns)
    }
}
