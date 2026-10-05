package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

data class EventEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val title: String = "",
    val startMs: Long = System.currentTimeMillis(),
    val endMs: Long = System.currentTimeMillis() + 3600_000L,
    val allDay: Boolean = false,
    val description: String = "",
    val location: String = "",
    val calendarId: Long = 0L,
    val remoteId: String = "",
    val calendars: List<UserCalendar> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
)

expect class EventEditViewModel : ViewModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<EventEditUiState>
    val navigateBack: kotlinx.coroutines.flow.SharedFlow<Unit>
    fun setTitle(value: String)
    fun setDescription(value: String)
    fun setLocation(value: String)
    fun setCalendarId(value: Long)
    fun setAllDay(value: Boolean)
    fun setStartDate(dateMs: Long)
    fun setStartTime(hour: Int, minute: Int)
    fun setEndDate(dateMs: Long)
    fun setEndTime(hour: Int, minute: Int)
    fun save()
    fun delete()
}