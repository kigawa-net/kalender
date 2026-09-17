@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
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
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.OutlookCalendarDataSource
import net.kigawa.kalender.data.auth.AuthController
import net.kigawa.kalender.data.auth.KeycloakAuthState
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.nowMs
import net.kigawa.kalender.util.plusDays
import net.kigawa.kalender.util.systemZone
import net.kigawa.kalender.util.toLocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class EventEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val title: String = "",
    val startMs: Long = nowMs(),
    val endMs: Long = nowMs() + 3600_000L,
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
    private val authController: AuthController,
    private val apiClient: KalenderApiClient,
    private val localStore: LocalCalendarStore,
    private val httpClient: HttpClient,
    private val eventId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    init {
        viewModelScope.launch {
            localStore.observeVisibleCalendars().collect { calendars ->
                _uiState.update { state ->
                    val calId = if (state.calendarId == 0L) calendars.firstOrNull()?.id ?: 0L else state.calendarId
                    state.copy(calendars = calendars, calendarId = calId)
                }
            }
        }
        if (eventId != null) {
            viewModelScope.launch {
                val event = localStore.observeEventById(eventId).filterNotNull().first()
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
            val rounded = roundToNextHour(nowMs())
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
        val zone = systemZone()
        if (value) {
            val startDate = state.startMs.toLocalDate(zone)
            val endDate = state.endMs.toLocalDate(zone)
            val startMs = startDate.atStartOfDayIn(zone).toEpochMilliseconds()
            // Google Calendar の end.date は exclusive なので、同日の場合は翌日を設定する
            val exclusiveEndDate = if (endDate <= startDate) startDate.plusDays(1) else endDate
            val endMs = exclusiveEndDate.atStartOfDayIn(zone).toEpochMilliseconds()
            _uiState.update { it.copy(allDay = true, startMs = startMs, endMs = endMs) }
        } else {
            val startDate = state.startMs.toLocalDate(zone)
            val startMs = startDate.atTime(10, 0).toInstant(zone).toEpochMilliseconds()
            _uiState.update { it.copy(allDay = false, startMs = startMs, endMs = startMs + 3600_000L) }
        }
    }

    fun setStartDate(dateMs: Long) {
        val state = _uiState.value
        val zone = systemZone()
        val currentStart = Instant.fromEpochMilliseconds(state.startMs).toLocalDateTime(zone)
        // DatePickerは選択日をUTC真夜中のエポック値で返す。UTCオフセットで解釈しないと負の地域で1日ずれる
        val newDate = Instant.fromEpochMilliseconds(dateMs).toLocalDateTime(TimeZone.UTC).date
        val newStart = newDate.atTime(currentStart.hour, currentStart.minute).toInstant(zone).toEpochMilliseconds()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    fun setStartTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val zone = systemZone()
        val currentStart = Instant.fromEpochMilliseconds(state.startMs).toLocalDateTime(zone)
        val newStart = currentStart.date.atTime(hour, minute).toInstant(zone).toEpochMilliseconds()
        val durationMs = state.endMs - state.startMs
        _uiState.update { it.copy(startMs = newStart, endMs = newStart + durationMs) }
    }

    fun setEndDate(dateMs: Long) {
        val state = _uiState.value
        val zone = systemZone()
        val currentEnd = Instant.fromEpochMilliseconds(state.endMs).toLocalDateTime(zone)
        val newDate = Instant.fromEpochMilliseconds(dateMs).toLocalDateTime(TimeZone.UTC).date
        val newEnd = newDate.atTime(currentEnd.hour, currentEnd.minute).toInstant(zone).toEpochMilliseconds()
        _uiState.update { it.copy(endMs = newEnd) }
    }

    fun setEndTime(hour: Int, minute: Int) {
        val state = _uiState.value
        val zone = systemZone()
        val currentEnd = Instant.fromEpochMilliseconds(state.endMs).toLocalDateTime(zone)
        val newEnd = currentEnd.date.atTime(hour, minute).toInstant(zone).toEpochMilliseconds()
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
                    timeZone = if (isGoogle) systemZone().id else "UTC",
                    description = state.description.trim(),
                    location = state.location.trim(),
                    remoteId = state.remoteId,
                )
                val saved = if (isGoogle) {
                    val dataSource = buildGoogleDataSource(calendar.ownerEmail) ?: run {
                        _uiState.update { it.copy(isSaving = false, error = "Googleアカウントが連携されていません") }
                        return@launch
                    }
                    if (state.isNew) dataSource.createEvent(calendar.accountName, event)
                    else dataSource.updateEvent(calendar.accountName, event)
                } else {
                    val dataSource = buildOutlookDataSource(calendar.ownerEmail) ?: run {
                        _uiState.update { it.copy(isSaving = false, error = "Microsoftアカウントが連携されていません") }
                        return@launch
                    }
                    if (state.isNew) dataSource.createEvent(calendar.accountName, event)
                    else dataSource.updateEvent(calendar.accountName, event)
                }
                localStore.upsertEvent(saved)
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
                val calendar = state.calendars.find { it.id == state.calendarId }
                    ?: run {
                        _uiState.update { it.copy(isDeleting = false, error = "カレンダーが見つかりません") }
                        return@launch
                    }
                val isGoogle = calendar.accountName.contains("@")
                if (isGoogle) {
                    val dataSource = buildGoogleDataSource(calendar.ownerEmail) ?: run {
                        _uiState.update { it.copy(isDeleting = false, error = "Googleアカウントが連携されていません") }
                        return@launch
                    }
                    dataSource.deleteEvent(calendar.accountName, state.remoteId)
                } else {
                    val dataSource = buildOutlookDataSource(calendar.ownerEmail) ?: run {
                        _uiState.update { it.copy(isDeleting = false, error = "Microsoftアカウントが連携されていません") }
                        return@launch
                    }
                    dataSource.deleteEvent(calendar.accountName, state.remoteId)
                }
                localStore.deleteEventById(id)
                _navigateBack.emit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = e.message ?: "削除に失敗しました") }
            }
        }
    }

    private suspend fun buildGoogleDataSource(ownerEmail: String): GoogleCalendarDataSource? {
        val authState = authController.authState.value
        if (authState !is KeycloakAuthState.SignedIn) return null
        val token = apiClient.fetchCalendarToken(authState.accessToken, "google") ?: return null
        return GoogleCalendarDataSource(token, ownerEmail, httpClient)
    }

    private suspend fun buildOutlookDataSource(ownerEmail: String): OutlookCalendarDataSource? {
        val authState = authController.authState.value
        if (authState !is KeycloakAuthState.SignedIn) return null
        val token = apiClient.fetchCalendarToken(authState.accessToken, "microsoft") ?: return null
        return OutlookCalendarDataSource(token, ownerEmail, httpClient)
    }

    private fun roundToNextHour(ms: Long): Long {
        val zone = systemZone()
        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)
        val nextHourDate = if (ldt.hour == 23) ldt.date.plusDays(1) else ldt.date
        val nextHour = if (ldt.hour == 23) 0 else ldt.hour + 1
        return nextHourDate.atTime(nextHour, 0).toInstant(zone).toEpochMilliseconds()
    }
}
