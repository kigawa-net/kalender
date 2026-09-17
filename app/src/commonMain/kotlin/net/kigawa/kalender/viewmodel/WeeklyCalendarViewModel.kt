package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import net.kigawa.kalender.data.CalendarDataSource
import net.kigawa.kalender.data.CalendarRepository
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.OutlookCalendarDataSource
import net.kigawa.kalender.data.auth.AuthController
import net.kigawa.kalender.data.auth.KeycloakAuthState
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.mondayOfWeek
import net.kigawa.kalender.util.plusWeeks
import net.kigawa.kalender.util.startOfDayMs
import net.kigawa.kalender.util.todayLocalDate

data class WeeklyCalendarUiState(
    val weekStart: LocalDate = mondayOfWeek(todayLocalDate()),
    val events: List<CalendarEvent> = emptyList(),
    val eventsByWeek: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val calendars: List<UserCalendar> = emptyList(),
    val isLoading: Boolean = false,
)

class WeeklyCalendarViewModel(
    private val authController: AuthController,
    private val apiClient: KalenderApiClient,
    private val localStore: LocalCalendarStore,
    private val httpClient: HttpClient,
) : ViewModel() {

    private val _weekStart = MutableStateFlow(mondayOfWeek(todayLocalDate()))
    private val _refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeeklyCalendarUiState> = authController.authState
        .flatMapLatest { authState ->
            if (authState !is KeycloakAuthState.SignedIn) {
                return@flatMapLatest flowOf(WeeklyCalendarUiState())
            }
            val dataSources = buildDataSources(authState.accessToken)

            if (dataSources.isEmpty()) {
                return@flatMapLatest flowOf(WeeklyCalendarUiState())
            }

            // 認証状態の変化（アカウント連携追加/解除/起動時の再認証）のたびにキャッシュを破棄する。
            // 新しいデータソースが追加された場合にキャッシュが有効だとそのソースのイベントが取得されないため、
            // 意図的に全週を再フェッチする。TTL による節約はバックグラウンド復帰時の _refreshTrigger で行う。
            localStore.clearWeekCache()

            val repository = CalendarRepository(
                dataSources = dataSources,
                localStore = localStore,
                scope = viewModelScope,
            )
            repository.syncCalendars()
            combine(_weekStart, _refreshTrigger) { week, _ -> week }.flatMapLatest { weekStart ->
                // ±2週分を取得: HorizontalPager の beyondViewportPageCount=1 により
                // スワイプ中に現在週±2週目まで描画されるため
                val weeks = (-2..2).map { weekStart.plusWeeks(it) }
                combine(
                    combine(
                        repository.eventsForWeek(weeks[0].startOfDayMs(), weeks[0].plusWeeks(1).startOfDayMs()),
                        repository.eventsForWeek(weeks[1].startOfDayMs(), weeks[1].plusWeeks(1).startOfDayMs()),
                        repository.eventsForWeek(weeks[2].startOfDayMs(), weeks[2].plusWeeks(1).startOfDayMs()),
                        repository.eventsForWeek(weeks[3].startOfDayMs(), weeks[3].plusWeeks(1).startOfDayMs()),
                        repository.eventsForWeek(weeks[4].startOfDayMs(), weeks[4].plusWeeks(1).startOfDayMs()),
                    ) { w0, w1, w2, w3, w4 -> listOf(w0, w1, w2, w3, w4) },
                    repository.calendars,
                ) { weekEvents, calendars ->
                    val visibleIds = calendars.filter { it.isVisible }.map { it.id }.toSet()
                    WeeklyCalendarUiState(
                        weekStart = weekStart,
                        events = weekEvents[2].filter { it.calendarId in visibleIds },
                        eventsByWeek = weeks.zip(weekEvents.map { w -> w.filter { it.calendarId in visibleIds } }).toMap(),
                        calendars = calendars,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyCalendarUiState())

    /** Keycloakに紐付け済みのGoogle/Microsoftアカウントごとに、実カレンダーAPI用のデータソースを構築する */
    private suspend fun buildDataSources(keycloakAccessToken: String): List<CalendarDataSource> {
        val linkedAccounts = apiClient.fetchLinkedAccounts(keycloakAccessToken)
        val dataSources = mutableListOf<CalendarDataSource>()
        for (account in linkedAccounts) {
            val ownerEmail = account.providerUserName ?: continue
            val providerToken = apiClient.fetchCalendarToken(keycloakAccessToken, account.provider) ?: continue
            when (account.provider) {
                "google" -> dataSources.add(GoogleCalendarDataSource(providerToken, ownerEmail, httpClient))
                "microsoft" -> dataSources.add(OutlookCalendarDataSource(providerToken, ownerEmail, httpClient))
            }
        }
        return dataSources
    }

    fun setWeek(week: LocalDate) = _weekStart.update { week }

    fun refresh() = _refreshTrigger.update { it + 1 }
}
