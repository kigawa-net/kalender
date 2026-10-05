package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import net.kigawa.kalender.model.CalendarEvent

data class EventDetailUiState(
    val event: CalendarEvent? = null,
    val calendarName: String = "",
    val isLoading: Boolean = true,
)

expect class EventDetailViewModel : ViewModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<EventDetailUiState>
}