package net.kigawa.kalender.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import net.kigawa.kalender.KalenderApplication
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.CalendarRepository
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.OutlookCalendarDataSource
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.data.db.KalenderDatabase
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeeklyCalendarUiState(
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val events: List<CalendarEvent> = emptyList(),
    val eventsByWeek: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val calendars: List<UserCalendar> = emptyList(),
    val isLoading: Boolean = false,
)

class WeeklyCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = (application as KalenderApplication).googleAuthManager
    private val db = KalenderDatabase.getInstance(application)
    private val localSource = CalendarLocalSource(db.calendarDao(), db.eventDao())

    private val _weekStart = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    private val _refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeeklyCalendarUiState> = combine(
        authManager.authState,
        (application as KalenderApplication).msAccessToken
    ) { googleState, msToken ->
        googleState to msToken
    }.flatMapLatest { (googleState, msToken) ->
        val dataSources = mutableListOf<net.kigawa.kalender.data.CalendarDataSource>()
        if (googleState is GoogleAuthManager.AuthState.SignedIn) {
            dataSources.add(GoogleCalendarDataSource(googleState.accessToken))
        }
        if (!msToken.isNullOrEmpty()) {
            dataSources.add(OutlookCalendarDataSource(msToken))
        }

        if (dataSources.isEmpty()) {
            return@flatMapLatest flowOf(WeeklyCalendarUiState())
        }

        val repository = CalendarRepository(
            dataSources = dataSources,
            localSource = localSource,
            cacheMetaDao = db.cacheMetaDao(),
            scope = viewModelScope,
        )
        repository.syncCalendars()
        combine(_weekStart, _refreshTrigger) { week, _ -> week }.flatMapLatest { weekStart ->
            // ±2週分を取得: HorizontalPager の beyondViewportPageCount=1 により
            // スワイプ中に現在週±2週目まで描画されるため
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
                WeeklyCalendarUiState(
                    weekStart = weekStart,
                    events = weekEvents[2],
                    eventsByWeek = weeks.zip(weekEvents).toMap(),
                    calendars = calendars,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyCalendarUiState())

    fun setWeek(week: LocalDate) = _weekStart.update { week }

    fun refresh() = _refreshTrigger.update { it + 1 }

    private fun LocalDate.startMs(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun LocalDate.endMs(): Long =
        plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
