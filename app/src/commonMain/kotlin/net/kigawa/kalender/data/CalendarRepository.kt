package net.kigawa.kalender.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

private const val CACHE_TTL_MS = 1_800_000L // 30分

class CalendarRepository(
    private val dataSources: List<CalendarDataSource>,
    private val localStore: LocalCalendarStore,
    private val scope: CoroutineScope,
) {
    val calendars: Flow<List<UserCalendar>> = localStore.observeCalendars()

    private val activeJobs = mutableMapOf<Long, Job>()

    fun eventsForWeek(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> {
        if (!activeJobs.containsKey(startMs)) {
            activeJobs[startMs] = scope.launch {
                try {
                    val isFresh = localStore.isWeekCacheFresh(startMs, CACHE_TTL_MS)
                    if (!isFresh) {
                        var allSucceeded = true
                        dataSources.forEach { dataSource ->
                            runCatching {
                                val events = dataSource.fetchEvents(startMs, endMs)
                                val calendarIds = dataSource.fetchCalendars().map { it.id }
                                localStore.upsertEventsForCalendars(events, startMs, endMs, calendarIds)
                            }.onFailure {
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
                }
            }
        }
    }
}
