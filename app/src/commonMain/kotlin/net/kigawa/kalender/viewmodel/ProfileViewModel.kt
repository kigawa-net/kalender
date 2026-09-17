package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.LinkedAccount
import net.kigawa.kalender.data.auth.AuthController
import net.kigawa.kalender.data.auth.KeycloakAuthState
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.openUrlInBrowser

data class ProfileUiState(
    val linkedAccounts: List<LinkedAccount> = emptyList(),
    val isLoadingLinkedAccounts: Boolean = true,
    val pendingLinkProvider: String? = null,
    val linkError: String? = null,
    val calendarsByOwnerEmail: Map<String, List<UserCalendar>> = emptyMap(),
)

class ProfileViewModel(
    private val authController: AuthController,
    private val apiClient: KalenderApiClient,
    private val localStore: LocalCalendarStore,
    private val accountLinkRedirectUri: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        refreshLinkedAccounts()
        viewModelScope.launch {
            localStore.observeCalendars().collect { calendars ->
                _uiState.update { it.copy(calendarsByOwnerEmail = calendars.groupBy { c -> c.ownerEmail }) }
            }
        }
    }

    fun refreshLinkedAccounts() {
        viewModelScope.launch {
            val authState = authController.authState.value
            if (authState !is KeycloakAuthState.SignedIn) {
                _uiState.update { it.copy(isLoadingLinkedAccounts = false, linkedAccounts = emptyList()) }
                return@launch
            }
            _uiState.update { it.copy(isLoadingLinkedAccounts = true) }
            val accounts = apiClient.fetchLinkedAccounts(authState.accessToken)
            _uiState.update { it.copy(isLoadingLinkedAccounts = false, linkedAccounts = accounts) }
        }
    }

    /** provider: "google" または "microsoft"。連携用URLを取得しブラウザで開く */
    fun linkAccount(provider: String, platformHandle: Any? = null) {
        viewModelScope.launch {
            val authState = authController.authState.value
            if (authState !is KeycloakAuthState.SignedIn) return@launch
            _uiState.update { it.copy(pendingLinkProvider = provider, linkError = null) }
            val url = apiClient.fetchAccountLinkUrl(authState.accessToken, provider, accountLinkRedirectUri)
            if (url == null) {
                _uiState.update {
                    it.copy(pendingLinkProvider = null, linkError = "連携用URLの取得に失敗しました")
                }
                return@launch
            }
            openUrlInBrowser(url, platformHandle)
        }
    }

    fun unlinkAccount(provider: String, ownerEmail: String) {
        viewModelScope.launch {
            val authState = authController.authState.value
            if (authState is KeycloakAuthState.SignedIn) {
                apiClient.unlinkAccount(authState.accessToken, provider)
            }
            localStore.deleteCalendarsByOwnerEmail(ownerEmail)
            refreshLinkedAccounts()
        }
    }

    fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        viewModelScope.launch { localStore.updateCalendarVisibility(id, isVisible) }
    }
}
