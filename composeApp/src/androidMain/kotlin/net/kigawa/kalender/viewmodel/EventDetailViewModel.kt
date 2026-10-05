package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.model.CalendarEvent

actual class EventDetailViewModel(
    private val calendarLocalSource: CalendarLocalSource,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val scope: kotlinx.coroutines.CoroutineScope = viewModelScope,
) : ViewModel() {

    private val eventId: Long = savedStateHandle.get<Long>("eventId")!!

    actual val uiState: StateFlow<EventDetailUiState> = combine(
        calendarLocalSource.observeEventById(eventId),
        calendarLocalSource.observeCalendars(),
    ) { event, calendars ->
        val calendarName = calendars.find { it.id == event?.calendarId }?.name ?: ""
        EventDetailUiState(event = event, calendarName = calendarName, isLoading = false)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), EventDetailUiState())
}