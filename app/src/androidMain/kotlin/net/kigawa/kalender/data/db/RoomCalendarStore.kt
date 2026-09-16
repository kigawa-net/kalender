package net.kigawa.kalender.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

class RoomCalendarStore(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
    private val cacheMetaDao: CacheMetaDao,
) : LocalCalendarStore {

    companion object {
        fun fromContext(context: Context): RoomCalendarStore {
            val db = KalenderDatabase.getInstance(context)
            return RoomCalendarStore(db.calendarDao(), db.eventDao(), db.cacheMetaDao())
        }
    }

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

    override suspend fun upsertEventsForCalendars(
        events: List<CalendarEvent>,
        startMs: Long,
        endMs: Long,
        calendarIds: List<Long>,
    ) {
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

    override suspend fun isWeekCacheFresh(weekStartMs: Long, ttlMs: Long): Boolean {
        val meta = cacheMetaDao.getByWeekStart(weekStartMs)
        val age = System.currentTimeMillis() - (meta?.lastFetchedMs ?: 0L)
        return age < ttlMs
    }

    override suspend fun markWeekFetched(weekStartMs: Long) {
        cacheMetaDao.upsert(CacheMetaEntity(weekStartMs, System.currentTimeMillis()))
    }

    override suspend fun clearWeekCache() {
        cacheMetaDao.deleteAll()
    }

    private fun CalendarEntity.toModel() = UserCalendar(id, name, color, accountName, isVisible, ownerEmail)
    private fun EventEntity.toModel() =
        CalendarEvent(id, calendarId, title, startMs, endMs, allDay, color, timeZone, description, location, remoteId)
    private fun UserCalendar.toEntity() = CalendarEntity(id, name, color, accountName, isVisible, ownerEmail)
    private fun CalendarEvent.toEntity() =
        EventEntity(id, calendarId, title, startMs, endMs, allDay, color, timeZone, description, location, remoteId)
}
