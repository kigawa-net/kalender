package net.kigawa.kalender.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.db.KalenderDatabase
import net.kigawa.kalender.model.CalendarEvent

data class EventDetailUiState(
    val event: CalendarEvent? = null,
    val calendarName: String = "",
    val isLoading: Boolean = true,
)

class EventDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val eventId: Long = checkNotNull(savedStateHandle["eventId"])
    private val db = KalenderDatabase.getInstance(application)
    private val localSource = CalendarLocalSource(db.calendarDao(), db.eventDao())

    val uiState: StateFlow<EventDetailUiState> = combine(
        localSource.observeEventById(eventId),
        localSource.observeCalendars(),
    ) { event, calendars ->
        val calendarName = calendars.find { it.id == event?.calendarId }?.name ?: ""
        EventDetailUiState(event = event, calendarName = calendarName, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventDetailUiState())
}
