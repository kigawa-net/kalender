package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
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
import net.kigawa.kalender.data.CalendarDataSource
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.OutlookCalendarDataSource
import net.kigawa.kalender.data.auth.AuthManager
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

actual class EventEditViewModel(
    private val calendarLocalSource: CalendarLocalSource,
    private val googleAuthManager: GoogleAuthManager,
    private val msAuthManager: MsAuthManager,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val scope: kotlinx.coroutines.CoroutineScope = viewModelScope,
) : ViewModel() {

    private val eventId: Long? = savedStateHandle.get<Long>("eventId")

    private val _uiState = MutableStateFlow(EventEditUiState())
    actual val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    actual val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    init {
        scope.launch {
            calendarLocalSource.observeVisibleCalendars().collect { calendars ->
                _uiState.update { state ->
                    val calId = if (state.calendarId == 0L) calendars.firstOrNull()?.id ?: 0L else state.calendarId
                    state.copy(calendars = calendars, calendarId = calId)
                }
            }
        }
        if (eventId != null) {
            scope.launch {
                val event = calendarLocalSource.observeEventById(eventId!!).filterNotNull().first()
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
            _uiState.update { it.copy(isNew = true, isLoading = false, startMs = rounded, endMs = rounded + 3600_000L) }
        }
    }

    actual fun setTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    actual fun setDescription(value: String) = _uiState.update { it.copy(description = value) }
    actual fun setLocation(value: String) = _uiState.update { it.copy(location = value) }
    actual fun setCalendarId(value: Long) = _uiState.update { it.copy(calendarId = value) }

    actual fun setAllDay(value: Boolean) {
        val state = _uiState.value
        if (value) {
            val startDate = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val startMs = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val exclusiveEndDate = if (!endDate.isAfter(startDate)) startDate.plusDays(1) else endDate
            val endMs = exclusiveEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _uiState.update { it.copy(allDay = true, startMs = startMs, endMs = endMs) }
        } else {
            val startDate = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val startMs = startDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _uiState.update { it.copy(allDay = false, startMs = startMs, endMs = startMs + 3600_000L) }
        }
    }

    actual fun setStartDate(dateMs: Long) {
        val state = _uiState.value
        val currentStart = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault())
        val newDate = Instant.ofEpochMilli(dateMs).atOffset(ZoneOffset.UTC).toLocalDate()
        val newStart = newDate.atTime(currentStart.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    actual fun setStartTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val currentStart = Instant.ofEpochMilli(state.startMs).atZone(ZoneId.systemDefault())
        val newStart = currentStart.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    actual fun setEndDate(dateMs: Long) {
        val state = _uiState.value
        val currentEnd = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault())
        val newDate = Instant.ofEpochMilli(dateMs).atOffset(ZoneOffset.UTC).toLocalDate()
        val newEnd = newDate.atTime(currentEnd.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        _uiState.update { it.copy(endMs = newEnd) }
    }

    actual fun setEndTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val currentEnd = Instant.ofEpochMilli(state.endMs).atZone(ZoneId.systemDefault())
        val newEnd = currentEnd.toLocalDate().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        _uiState.update { it.copy(endMs = newEnd) }
    }

    actual fun save() {
        scope.launch {
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
                val calendar = state.calendars.find { it.id == state.calendarId }
                    ?: state.calendars.firstOrNull()
                    ?: run {
                        _uiState.update { it.copy(isSaving = false, error = "カレンダーが見つかりません") }
                        return@launch
                    }
                val isGoogle = calendar.accountName.contains("@")
                val event = CalendarEvent(
                    id = eventId ?: 0L,
                    calendarId = calendar.id,
                    title = state.title.trim(),
                    startMs = state.startMs,
                    endMs = state.endMs,
                    allDay = state.allDay,
                    color = calendar.color,
                    timeZone = if (isGoogle) ZoneId.systemDefault().id else "UTC",
                    description = state.description.trim(),
                    location = state.location.trim(),
                    remoteId = state.remoteId,
                )
                val saved = if (isGoogle) {
                    val googleState = googleAuthManager.authState.value
                    if (googleState !is AuthManager.State.SignedIn) {
                        _uiState.update { it.copy(isSaving = false, error = "Google認証が必要です") }
                        return@launch
                    }
                    val dataSource = GoogleCalendarDataSource(googleState.accessToken, googleState.email)
                    if (state.isNew) dataSource.createEvent(calendar.accountName, event)
                    else dataSource.updateEvent(calendar.accountName, event)
                } else {
                    val msState = msAuthManager.authState.value
                    if (msState !is MsAuthState.SignedIn) {
                        _uiState.update { it.copy(isSaving = false, error = "Microsoft認証が必要です") }
                        return@launch
                    }
                    val dataSource = OutlookCalendarDataSource(msState.accessToken, msState.email)
                    if (state.isNew) dataSource.createEvent(calendar.accountName, event)
                    else dataSource.updateEvent(calendar.accountName, event)
                }
                calendarLocalSource.upsertEvent(saved)
                _navigateBack.emit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "保存に失敗しました") }
            }
        }
    }

    actual fun delete() {
        val id = eventId ?: return
        scope.launch {
            val state = _uiState.value
            if (state.remoteId.isEmpty()) {
                _uiState.update { it.copy(error = "この予定は削除できません（再同期してください）") }
                return@launch
            }
            _uiState.update { it.copy(isDeleting = true, error = null) }
            try {
                val calendar = state.calendars.find { it.id == state.calendarId }
                    ?: run {
                        _uiState.update { it.copy(isDeleting = false, error = "カレンダーが見つかりません") }
                        return@launch
                    }
                val isGoogle = calendar.accountName.contains("@")
                if (isGoogle) {
                    val googleState = googleAuthManager.authState.value
                    if (googleState !is AuthManager.State.SignedIn) {
                        _uiState.update { it.copy(isDeleting = false, error = "Google認証が必要です") }
                        return@launch
                    }
                    GoogleCalendarDataSource(googleState.accessToken, googleState.email).deleteEvent(calendar.accountName, state.remoteId)
                } else {
                    val msState = msAuthManager.authState.value
                    if (msState !is MsAuthState.SignedIn) {
                        _uiState.update { it.copy(isDeleting = false, error = "Microsoft認証が必要です") }
                        return@launch
                    }
                    OutlookCalendarDataSource(msState.accessToken, msState.email).deleteEvent(calendar.accountName, state.remoteId)
                }
                calendarLocalSource.deleteEventById(id)
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