package net.kigawa.kalender.data

import kotlinx.coroutines.flow.Flow
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

interface CalendarLocalSource {
    fun observeCalendars(): Flow<List<UserCalendar>>
    fun observeVisibleCalendars(): Flow<List<UserCalendar>>
    fun observeEvents(startMs: Long, endMs: Long): Flow<List<CalendarEvent>>
    fun observeEventById(id: Long): Flow<CalendarEvent?>
    suspend fun upsertCalendars(calendars: List<UserCalendar>)
    suspend fun upsertEvents(events: List<CalendarEvent>, startMs: Long, endMs: Long)
    suspend fun upsertEventsForCalendars(events: List<CalendarEvent>, startMs: Long, endMs: Long, calendarIds: List<Long>)
    suspend fun upsertEvent(event: CalendarEvent)
    suspend fun deleteEventById(id: Long)
    suspend fun updateCalendarVisibility(id: Long, isVisible: Boolean)
    suspend fun deleteCalendarsByOwnerEmail(ownerEmail: String)
}