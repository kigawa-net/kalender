package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.time.LocalDate

data class WeeklyCalendarUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList(),
    val eventsByWeek: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val calendars: List<UserCalendar> = emptyList(),
    val isLoading: Boolean = false,
)

expect class WeeklyCalendarViewModel : ViewModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<WeeklyCalendarUiState>
    fun setWeek(week: LocalDate)
    fun refresh()
}