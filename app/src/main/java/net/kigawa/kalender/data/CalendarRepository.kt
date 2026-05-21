package net.kigawa.kalender.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

class CalendarRepository(
    private val dataSources: List<CalendarDataSource>,
    private val localSource: CalendarLocalSource,
    private val scope: CoroutineScope,
) {
    val calendars: Flow<List<UserCalendar>> = localSource.observeCalendars()

    fun eventsForWeek(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> {
        scope.launch {
            dataSources.forEach { dataSource ->
                runCatching {
                    val events = dataSource.fetchEvents(startMs, endMs)
                    localSource.upsertEvents(events, startMs, endMs)
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
