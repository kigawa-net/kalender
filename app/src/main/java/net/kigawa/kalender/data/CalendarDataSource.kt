package net.kigawa.kalender.data

import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

interface CalendarDataSource {
    suspend fun fetchCalendars(): List<UserCalendar>
    suspend fun fetchEvents(startMs: Long, endMs: Long): List<CalendarEvent>
}
