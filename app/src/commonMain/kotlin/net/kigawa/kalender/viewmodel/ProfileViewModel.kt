package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.auth.GoogleAuthController
import net.kigawa.kalender.data.auth.GoogleAuthState
import net.kigawa.kalender.data.auth.MicrosoftAuthController
import net.kigawa.kalender.model.UserCalendar

data class OutlookAccount(val email: String)

data class GoogleAccount(val email: String, val displayName: String?)

data class ProfileUiState(
    val accounts: List<OutlookAccount> = emptyList(),
    val googleAccount: GoogleAccount? = null,
    val isAddingAccount: Boolean = false,
    val isAddingGoogleAccount: Boolean = false,
    val addAccountError: String? = null,
    val addGoogleAccountError: String? = null,
    val calendarsByOwnerEmail: Map<String, List<UserCalendar>> = emptyMap(),
)

class ProfileViewModel(
    private val googleAuthController: GoogleAuthController,
    private val microsoftAuthController: MicrosoftAuthController,
    private val localStore: LocalCalendarStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            microsoftAuthController.accounts.collect { accounts ->
                _uiState.update { it.copy(accounts = accounts.map { a -> OutlookAccount(a.email) }) }
            }
        }
        viewModelScope.launch {
            googleAuthController.authState.collect { state ->
                _uiState.update {
                    when (state) {
                        is GoogleAuthState.SignedIn -> it.copy(
                            googleAccount = GoogleAccount(state.email, state.displayName),
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = null,
                        )
                        is GoogleAuthState.SignedOut -> it.copy(googleAccount = null, isAddingGoogleAccount = false)
                        is GoogleAuthState.Loading -> it.copy(isAddingGoogleAccount = true, addGoogleAccountError = null)
                        is GoogleAuthState.Error -> it.copy(
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = state.message,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            localStore.observeCalendars().collect { calendars ->
                _uiState.update { it.copy(calendarsByOwnerEmail = calendars.groupBy { c -> c.ownerEmail }) }
            }
        }
    }

    fun addAccount(platformHandle: Any? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAccount = true, addAccountError = null) }
            runCatching { microsoftAuthController.addAccount(platformHandle) }
                .onFailure { e ->
                    _uiState.update { it.copy(isAddingAccount = false, addAccountError = e.message ?: "認証に失敗しました") }
                    return@launch
                }
            _uiState.update { it.copy(isAddingAccount = false) }
        }
    }

    fun removeAccount(email: String) {
        viewModelScope.launch {
            microsoftAuthController.removeAccount(email)
            localStore.deleteCalendarsByOwnerEmail(email)
        }
    }

    fun addGoogleAccount(platformHandle: Any? = null) {
        viewModelScope.launch { googleAuthController.signIn(platformHandle) }
    }

    fun removeGoogleAccount() {
        val email = (googleAuthController.authState.value as? GoogleAuthState.SignedIn)?.email
        googleAuthController.signOut()
        if (email != null) {
            viewModelScope.launch { localStore.deleteCalendarsByOwnerEmail(email) }
        }
    }

    fun dismissAddAccountError() {
        _uiState.update { it.copy(addAccountError = null) }
    }

    fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        viewModelScope.launch { localStore.updateCalendarVisibility(id, isVisible) }
    }
}
