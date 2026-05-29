package net.kigawa.kalender.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.KalenderApplication
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.data.db.KalenderDatabase
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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

class EventEditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val eventId: Long? = savedStateHandle["eventId"]
    private val authManager = (application as KalenderApplication).googleAuthManager
    private val db = KalenderDatabase.getInstance(application)
    private val localSource = CalendarLocalSource(db.calendarDao(), db.eventDao())

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    init {
        viewModelScope.launch {
            localSource.observeCalendars().collect { calendars ->
                _uiState.update { state ->
                    val calId = if (state.calendarId == 0L) calendars.firstOrNull()?.id ?: 0L else state.calendarId
                    state.copy(calendars = calendars, calendarId = calId)
                }
            }
        }
        if (eventId != null) {
            viewModelScope.launch {
                val event = localSource.observeEventById(eventId).filterNotNull().first()
                _uiState.update {
                    it.copy(
                        isNew = false,
                        isLoading = false,
                        title = event.title,
                        startMs = event.startMs,
                        endMs = event.endMs,
                        allDay = event.allDay,
                        description = event.description,
                        location = event.location,
                        calendarId = event.calendarId,
                        remoteId = event.remoteId,
                    )
                }
            }
        } else {
            val rounded = roundToNextHour(System.currentTimeMillis())
            _uiState.update {
                it.copy(isNew = true, isLoading = false, startMs = rounded, endMs = rounded + 3600_000L)
            }
        }
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun setDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun setLocation(value: String) = _uiState.update { it.copy(location = value) }
    fun setCalendarId(value: Long) = _uiState.update { it.copy(calendarId = value) }

    fun setAllDay(value: Boolean) {
        val state = _uiState.value
        if (value) {
            val startDate = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val startMs = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Google Calendar の end.date は exclusive なので、同日の場合は翌日を設定する
            val exclusiveEndDate = if (!endDate.isAfter(startDate)) startDate.plusDays(1) else endDate
            val endMs = exclusiveEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _uiState.update { it.copy(allDay = true, startMs = startMs, endMs = endMs) }
        } else {
            val startDate = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val startMs = startDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _uiState.update { it.copy(allDay = false, startMs = startMs, endMs = startMs + 3600_000L) }
        }
    }

    fun setStartDate(dateMs: Long) {
        val state = _uiState.value
        val currentStart = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault())
        // DatePickerは選択日をUTC真夜中のエポック値で返す。UTCオフセットで解釈しないと負の地域で1日ずれる
        val newDate = Instant.ofEpochMilli(dateMs).atOffset(ZoneOffset.UTC).toLocalDate()
        val newStart = newDate.atTime(currentStart.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    fun setStartTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val currentStart = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault())
        val newStart = currentStart.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    fun setEndDate(dateMs: Long) {
        val state = _uiState.value
        val currentEnd = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault())
        // DatePickerは選択日をUTC真夜中のエポック値で返す。UTCオフセットで解釈しないと負の地域で1日ずれる
        val newDate = Instant.ofEpochMilli(dateMs).atOffset(ZoneOffset.UTC).toLocalDate()
        val newEnd = newDate.atTime(currentEnd.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        _uiState.update { it.copy(endMs = newEnd) }
    }

    fun setEndTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val currentEnd = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault())
        val newEnd = currentEnd.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        _uiState.update { it.copy(endMs = newEnd) }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.title.isBlank()) {
                _uiState.update { it.copy(error = "タイトルを入力してください") }
                return@launch
            }
            if (!state.isNew && state.remoteId.isEmpty()) {
                _uiState.update { it.copy(error = "この予定は編集できません（再同期してください）") }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val googleState = authManager.authState.value
                if (googleState !is GoogleAuthManager.AuthState.SignedIn) {
                    _uiState.update { it.copy(isSaving = false, error = "Google認証が必要です") }
                    return@launch
                }
                val googleDataSource = GoogleCalendarDataSource(googleState.accessToken)
                val calendar = state.calendars.find { it.id == state.calendarId }
                    ?: state.calendars.firstOrNull()
                    ?: run {
                        _uiState.update { it.copy(isSaving = false, error = "カレンダーが見つかりません") }
                        return@launch
                    }
                val event = CalendarEvent(
                    id = eventId ?: 0L,
                    calendarId = calendar.id,
                    title = state.title.trim(),
                    startMs = state.startMs,
                    endMs = state.endMs,
                    allDay = state.allDay,
                    color = calendar.color,
                    timeZone = ZoneId.systemDefault().id,
                    description = state.description.trim(),
                    location = state.location.trim(),
                    remoteId = state.remoteId,
                )
                val saved = if (state.isNew) {
                    googleDataSource.createEvent(calendar.accountName, event)
                } else {
                    googleDataSource.updateEvent(calendar.accountName, event)
                }
                localSource.upsertEvent(saved)
                _navigateBack.emit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "保存に失敗しました") }
            }
        }
    }

    fun delete() {
        val id = eventId ?: return
        viewModelScope.launch {
            val state = _uiState.value
            if (state.remoteId.isEmpty()) {
                _uiState.update { it.copy(error = "この予定は削除できません（再同期してください）") }
                return@launch
            }
            _uiState.update { it.copy(isDeleting = true, error = null) }
            try {
                val googleState = authManager.authState.value
                if (googleState !is GoogleAuthManager.AuthState.SignedIn) {
                    _uiState.update { it.copy(isDeleting = false, error = "Google認証が必要です") }
                    return@launch
                }
                val googleDataSource = GoogleCalendarDataSource(googleState.accessToken)
                val calendar = state.calendars.find { it.id == state.calendarId }
                    ?: run {
                        _uiState.update { it.copy(isDeleting = false, error = "カレンダーが見つかりません") }
                        return@launch
                    }
                googleDataSource.deleteEvent(calendar.accountName, state.remoteId)
                localSource.deleteEventById(id)
                _navigateBack.emit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = e.message ?: "削除に失敗しました") }
            }
        }
    }

    private fun roundToNextHour(ms: Long): Long =
        Instant.ofEpochMilli(ms)
            .atZone(ZoneId.systemDefault())
            .plusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
}
