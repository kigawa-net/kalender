package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.CalendarDataSource
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.CalendarRepository
import net.kigawa.kalender.data.CacheMetaSource
import net.kigawa.kalender.data.auth.AuthManager
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

actual class WeeklyCalendarViewModel(
    private val googleAuthManager: GoogleAuthManager,
    private val msAuthManager: MsAuthManager,
    private val calendarLocalSource: CalendarLocalSource,
    private val cacheMetaSource: CacheMetaSource,
    private val scope: kotlinx.coroutines.CoroutineScope = viewModelScope,
) : ViewModel() {

    private val _weekStart = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    private val _refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    actual val uiState: StateFlow<WeeklyCalendarUiState> = combine(
        googleAuthManager.authState,
        msAuthManager.authState,
    ) { googleState, msState ->
        Pair(googleState, msState)
    }.flatMapLatest { (googleState, msState) ->
        val dataSources = mutableListOf<CalendarDataSource>()
        if (googleState is AuthManager.State.SignedIn) {
            dataSources.add(GoogleCalendarDataSource(googleState.accessToken, googleState.email))
        }
        if (msState is MsAuthState.SignedIn) {
            dataSources.add(OutlookCalendarDataSource(msState.accessToken, msState.email))
        }

        if (dataSources.isEmpty()) {
            return@flatMapLatest flowOf(WeeklyCalendarUiState())
        }

        scope.launch { cacheMetaSource.deleteAll() }

        val repository = CalendarRepository(
            dataSources = dataSources,
            localSource = calendarLocalSource,
            cacheMetaSource = cacheMetaSource,
            scope = scope,
        )
        repository.syncCalendars()
        combine(_weekStart, _refreshTrigger) { week, _ -> week }.flatMapLatest { weekStart ->
            val weeks = (-2..2).map { weekStart.plusWeeks(it.toLong()) }
            combine(
                combine(
                    repository.eventsForWeek(weeks[0].startMs(), weeks[0].endMs()),
                    repository.eventsForWeek(weeks[1].startMs(), weeks[1].endMs()),
                    repository.eventsForWeek(weeks[2].startMs(), weeks[2].endMs()),
                    repository.eventsForWeek(weeks[3].startMs(), weeks[3].endMs()),
                    repository.eventsForWeek(weeks[4].startMs(), weeks[4].endMs()),
                ) { w0, w1, w2, w3, w4 -> listOf(w0, w1, w2, w3, w4) },
                repository.calendars,
            ) { weekEvents, calendars ->
                val visibleIds = calendars.filter { it.isVisible }.map { it.id }.toSet()
                WeeklyCalendarUiState(
                    weekStart = weekStart,
                    events = weekEvents[2].filter { it.calendarId in visibleIds },
                    eventsByWeek = weeks.zip(weekEvents.map { w -> w.filter { it.calendarId in visibleIds } }).toMap(),
                    calendars = calendars,
                )
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, WeeklyCalendarUiState())

    actual fun setWeek(week: LocalDate) = _weekStart.update { week }

    actual fun refresh() = _refreshTrigger.update { it + 1 }

    private fun LocalDate.startMs(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun LocalDate.endMs(): Long =
        plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}