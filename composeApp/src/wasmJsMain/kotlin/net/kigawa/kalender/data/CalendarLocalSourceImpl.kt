package net.kigawa.kalender.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.kigawa.kalender.data.db.CalendarDatabase
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import org.jetbrains.kotlinx.coroutines.jsMainDispatcher

class CalendarLocalSourceImpl(private val db: CalendarDatabase) : CalendarLocalSource {
    private val calendarQueries = db.calendarQueries
    private val eventQueries = db.eventQueries
    private val cacheMetaQueries = db.cacheMetaQueries

    override fun observeCalendars(): Flow<List<UserCalendar>> =
        calendarQueries.selectAll().map { list -> list.map { it.toModel() } }

    override fun observeVisibleCalendars(): Flow<List<UserCalendar>> =
        calendarQueries.selectVisible().map { list -> list.map { it.toModel() } }

    override fun observeEvents(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> =
        eventQueries.selectByRange(startMs, endMs).map { list -> list.map { it.toModel() } }

    override fun observeEventById(id: Long): Flow<CalendarEvent?> =
        eventQueries.selectById(id).map { it?.toModel() }

    override suspend fun upsertCalendars(calendars: List<UserCalendar>) {
        calendars.forEach { calendarQueries.upsert(it.toEntity()) }
    }

    override suspend fun upsertEvents(events: List<CalendarEvent>, startMs: Long, endMs: Long) {
        eventQueries.deleteByRange(startMs, endMs)
        events.forEach { eventQueries.upsert(it.toEntity()) }
    }

    override suspend fun upsertEventsForCalendars(events: List<CalendarEvent>, startMs: Long, endMs: Long, calendarIds: List<Long>) {
        if (calendarIds.isNotEmpty()) eventQueries.deleteByRangeAndCalendars(startMs, endMs, calendarIds)
        events.forEach { eventQueries.upsert(it.toEntity()) }
    }

    override suspend fun upsertEvent(event: CalendarEvent) {
        eventQueries.upsert(event.toEntity())
    }

    override suspend fun deleteEventById(id: Long) {
        eventQueries.deleteById(id)
    }

    override suspend fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        calendarQueries.updateVisibility(id, isVisible)
    }

    override suspend fun deleteCalendarsByOwnerEmail(ownerEmail: String) {
        calendarQueries.deleteByOwnerEmail(ownerEmail)
    }
}