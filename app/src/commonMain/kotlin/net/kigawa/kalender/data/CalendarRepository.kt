package net.kigawa.kalender.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import net.kigawa.kalender.util.startOfDayMs
import net.kigawa.kalender.util.logErrorWithException
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

private const val CACHE_TTL_MS = 1_800_000L // 30分

private fun logError(tag: String, message: String, e: Throwable? = null) =
    logErrorWithException(tag, message, e)

class CalendarRepository(
    private val dataSources: List<CalendarDataSource>,
    private val localStore: LocalCalendarStore,
    private val scope: CoroutineScope,
) {
    val calendars: Flow<List<UserCalendar>> = localStore.observeCalendars()

    private val activeJobs = mutableMapOf<Long, Job>()

    fun eventsForWeek(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> = eventsForWeek(startMs, endMs, forceRefresh = false)

    fun eventsForWeek(startMs: Long, endMs: Long, forceRefresh: Boolean): Flow<List<CalendarEvent>> {
        if (!activeJobs.containsKey(startMs)) {
            activeJobs[startMs] = scope.launch {
                try {
                    val isFresh = if (forceRefresh) false else localStore.isWeekCacheFresh(startMs, CACHE_TTL_MS)
                    if (!isFresh) {
                        var allSucceeded = true
                        dataSources.forEach { dataSource ->
                            runCatching {
                                val events = dataSource.fetchEvents(startMs, endMs)
                                val calendarIds = dataSource.fetchCalendars().map { it.id }
                                localStore.upsertEventsForCalendars(events, startMs, endMs, calendarIds)
                            }.onFailure { e ->
                                logError("CalendarRepository", "Failed to fetch events from ${dataSource::class.simpleName}", e)
                                allSucceeded = false
                            }
                        }
                        if (allSucceeded) {
                            localStore.markWeekFetched(startMs)
                        }
                    }
                } finally {
                    activeJobs.remove(startMs)
                }
            }
        }
        return localStore.observeEvents(startMs, endMs)
    }

    fun eventById(id: Long): Flow<CalendarEvent?> = localStore.observeEventById(id)

    fun syncCalendars() {
        scope.launch {
            dataSources.forEach { dataSource ->
                runCatching {
                    val calendars = dataSource.fetchCalendars()
                    localStore.upsertCalendars(calendars)
                }.onFailure { e ->
                    logError("CalendarRepository", "Failed to sync calendars from ${dataSource::class.simpleName}", e)
                }
            }
        }
    }

    /** 指定週を強制的に再取得する（TTLを無視） */
    fun refreshWeek(startMs: Long, endMs: Long) {
        eventsForWeek(startMs, endMs, forceRefresh = true)
    }

    /** 複数週を一括で強制再取得する（周辺週も含めて更新） */
    fun refreshWeeks(weekStarts: List<LocalDate>) {
        weekStarts.forEach { weekStart ->
            val startMs = weekStart.startOfDayMs()
            val endMs = startMs + 7 * 24 * 60 * 60 * 1000L
            eventsForWeek(startMs, endMs, forceRefresh = true)
        }
    }
}