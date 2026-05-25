package net.kigawa.kalender.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.db.CacheMetaDao
import net.kigawa.kalender.data.db.CacheMetaEntity
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

private const val CACHE_TTL_MS = 30 * 60 * 1000L

class CalendarRepository(
    private val dataSources: List<CalendarDataSource>,
    private val localSource: CalendarLocalSource,
    private val cacheMetaDao: CacheMetaDao,
    private val scope: CoroutineScope,
) {
    val calendars: Flow<List<UserCalendar>> = localSource.observeCalendars()

    fun eventsForWeek(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> {
        scope.launch {
            val meta = cacheMetaDao.getByWeekStart(startMs)
            val cacheAge = System.currentTimeMillis() - (meta?.lastFetchedMs ?: 0L)
            if (cacheAge >= CACHE_TTL_MS) {
                val fetchResults = dataSources.map { dataSource ->
                    runCatching { dataSource.fetchEvents(startMs, endMs) }
                }
                if (fetchResults.all { it.isSuccess }) {
                    val events = fetchResults.flatMap { it.getOrThrow() }
                    localSource.upsertEvents(events, startMs, endMs)
                    cacheMetaDao.upsert(CacheMetaEntity(startMs, System.currentTimeMillis()))
                }
            }
        }
        return localSource.observeEvents(startMs, endMs)
    }

    fun eventById(id: Long): Flow<CalendarEvent?> = localSource.observeEventById(id)

    fun syncCalendars() {
        scope.launch {
            dataSources.forEach { dataSource ->
                runCatching {
                    val calendars = dataSource.fetchCalendars()
                    localSource.upsertCalendars(calendars)
                }
            }
        }
    }
}
