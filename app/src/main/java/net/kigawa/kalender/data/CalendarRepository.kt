package net.kigawa.kalender.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.db.CacheMetaDao
import net.kigawa.kalender.data.db.CacheMetaEntity
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.util.concurrent.ConcurrentHashMap

private const val CACHE_TTL_MS = 30 * 60 * 1000L

class CalendarRepository(
    private val dataSources: List<CalendarDataSource>,
    private val localSource: CalendarLocalSource,
    private val cacheMetaDao: CacheMetaDao,
    private val scope: CoroutineScope,
) {
    val calendars: Flow<List<UserCalendar>> = localSource.observeCalendars()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun eventsForWeek(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> {
        activeJobs.computeIfAbsent(startMs) {
            scope.launch {
                try {
                    val meta = cacheMetaDao.getByWeekStart(startMs)
                    val cacheAge = System.currentTimeMillis() - (meta?.lastFetchedMs ?: 0L)
                    if (cacheAge >= CACHE_TTL_MS) {
                        var allSucceeded = true
                        dataSources.forEach { dataSource ->
                            runCatching {
                                val events = dataSource.fetchEvents(startMs, endMs)
                                val calendarIds = dataSource.fetchCalendars().map { it.id }
                                localSource.upsertEventsForCalendars(events, startMs, endMs, calendarIds)
                            }.onFailure {
                                allSucceeded = false
                            }
                        }
                        if (allSucceeded) {
                            cacheMetaDao.upsert(CacheMetaEntity(startMs, System.currentTimeMillis()))
                        }
                    }
                } finally {
                    activeJobs.remove(startMs)
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
