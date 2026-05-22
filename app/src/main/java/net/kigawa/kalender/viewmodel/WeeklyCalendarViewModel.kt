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
        _weekStart.flatMapLatest { weekStart ->
            val startMs = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs = weekStart.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            combine(
                repository.eventsForWeek(startMs, endMs),
                repository.calendars,
            ) { events, calendars ->
                WeeklyCalendarUiState(weekStart, events, calendars)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyCalendarUiState())

    fun setWeek(week: LocalDate) = _weekStart.update { week }
}
