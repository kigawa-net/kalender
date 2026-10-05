package net.kigawa.kalender.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.kigawa.kalender.data.db.CalendarDao
import net.kigawa.kalender.data.db.CalendarEntity
import net.kigawa.kalender.data.db.EventDao
import net.kigawa.kalender.data.db.EventEntity
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

class CalendarLocalSourceImpl(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
) : CalendarLocalSource {
    override fun observeCalendars(): Flow<List<UserCalendar>> =
        calendarDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeVisibleCalendars(): Flow<List<UserCalendar>> =
        calendarDao.observeVisible().map { list -> list.map { it.toModel() } }

    override fun observeEvents(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> =
        eventDao.observeByRange(startMs, endMs).map { list -> list.map { it.toModel() } }

    override fun observeEventById(id: Long): Flow<CalendarEvent?> =
        eventDao.observeById(id).map { it?.toModel() }

    override suspend fun upsertCalendars(calendars: List<UserCalendar>) {
        calendarDao.upsertAll(calendars.map { it.toEntity() })
    }

    override suspend fun upsertEvents(events: List<CalendarEvent>, startMs: Long, endMs: Long) {
        eventDao.replaceByRange(startMs, endMs, events.map { it.toEntity() })
    }

    override suspend fun upsertEventsForCalendars(events: List<CalendarEvent>, startMs: Long, endMs: Long, calendarIds: List<Long>) {
        eventDao.replaceByRangeAndCalendars(startMs, endMs, events.map { it.toEntity() }, calendarIds)
    }

    override suspend fun upsertEvent(event: CalendarEvent) {
        eventDao.upsertOne(event.toEntity())
    }

    override suspend fun deleteEventById(id: Long) {
        eventDao.deleteById(id)
    }

    override suspend fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        calendarDao.updateVisibility(id, isVisible)
    }

    override suspend fun deleteCalendarsByOwnerEmail(ownerEmail: String) {
        calendarDao.deleteByOwnerEmail(ownerEmail)
    }

    private fun CalendarEntity.toModel() = UserCalendar(id, name, color, accountName, isVisible, ownerEmail)
    private fun EventEntity.toModel() = CalendarEvent(id, calendarId, title, startMs, endMs, allDay, color, timeZone, description, location, remoteId)
    private fun UserCalendar.toEntity() = CalendarEntity(id, name, color, accountName, isVisible, ownerEmail)
    private fun CalendarEvent.toEntity() = EventEntity(id, calendarId, title, startMs, endMs, allDay, color, timeZone, description, location, remoteId)
}