package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.model.CalendarEvent

data class EventDetailUiState(
    val event: CalendarEvent? = null,
    val calendarName: String = "",
    val isLoading: Boolean = true,
)

class EventDetailViewModel(
    localStore: LocalCalendarStore,
    eventId: Long,
) : ViewModel() {
    val uiState: StateFlow<EventDetailUiState> = combine(
        localStore.observeEventById(eventId),
        localStore.observeCalendars(),
    ) { event, calendars ->
        val calendarName = calendars.find { it.id == event?.calendarId }?.name ?: ""
        EventDetailUiState(event = event, calendarName = calendarName, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventDetailUiState())
}
