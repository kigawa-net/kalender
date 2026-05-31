package net.kigawa.kalender.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.identity.client.IAccount
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.KalenderApplication
import net.kigawa.kalender.R
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.data.auth.MsalAuthManager
import net.kigawa.kalender.data.auth.SignInCancelledException
import net.kigawa.kalender.data.db.KalenderDatabase
import net.kigawa.kalender.model.UserCalendar

data class OutlookAccount(
    val email: String,
)

data class GoogleAccount(
    val email: String,
    val displayName: String?,
)

data class ProfileUiState(
    val accounts: List<OutlookAccount> = emptyList(),
    val googleAccount: GoogleAccount? = null,
    val isAddingAccount: Boolean = false,
    val isAddingGoogleAccount: Boolean = false,
    val addAccountError: String? = null,
    val addGoogleAccountError: String? = null,
    val calendars: List<UserCalendar> = emptyList(),
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val googleAuthManager = (application as KalenderApplication).googleAuthManager
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    private val msalDeferred: Deferred<Result<MsalAuthManager>> = viewModelScope.async {
        runCatching { MsalAuthManager.create(getApplication()) }
    }
    private val iAccountByEmail = mutableMapOf<String, IAccount>()
    private val db = KalenderDatabase.getInstance(application)
    private val localSource = CalendarLocalSource(db.calendarDao(), db.eventDao())

    init {
        // Outlook
        viewModelScope.launch {
            msalDeferred.await().onSuccess { manager ->
                runCatching { manager.getAccounts() }.onSuccess { accounts ->
                    accounts.forEach { iAccountByEmail[it.username] = it }
                    _uiState.update {
                        it.copy(accounts = accounts.map { a -> OutlookAccount(a.username) })
                    }
                }
            }
        }
        // Google
        viewModelScope.launch {
            googleAuthManager.authState.collect { state ->
                _uiState.update {
                    when (state) {
                        is GoogleAuthManager.AuthState.SignedIn -> it.copy(
                            googleAccount = GoogleAccount(state.email, state.displayName),
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = null
                        )
                        is GoogleAuthManager.AuthState.SignedOut -> it.copy(
                            googleAccount = null,
                            isAddingGoogleAccount = false
                        )
                        is GoogleAuthManager.AuthState.Loading -> it.copy(
                            isAddingGoogleAccount = true,
                            addGoogleAccountError = null
                        )
                        is GoogleAuthManager.AuthState.Error -> it.copy(
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = state.message
                        )
                    }
                }
            }
        }
        // Calendars
        viewModelScope.launch {
            localSource.observeCalendars().collect { calendars ->
                _uiState.update { it.copy(calendars = calendars) }
            }
        }
    }

    fun addAccount(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAccount = true, addAccountError = null) }
            val managerResult = msalDeferred.await()
            managerResult.onFailure { e ->
                _uiState.update {
                    it.copy(isAddingAccount = false, addAccountError = e.message ?: "初期化に失敗しました")
                }
                return@launch
            }
            val manager = managerResult.getOrThrow()
            runCatching { manager.acquireToken(activity) }
                .onSuccess { token ->
                    val accounts = manager.getAccounts()
                    val account = accounts.firstOrNull()
                    if (account != null) {
                        iAccountByEmail[account.username] = account
                        (getApplication() as KalenderApplication).msAccessToken.value = token
                        _uiState.update { state ->
                            val newList =
                                if (state.accounts.any { it.email == account.username }) state.accounts
                                else state.accounts + OutlookAccount(account.username)
                            state.copy(accounts = newList, isAddingAccount = false)
                        }
                    } else {
                        _uiState.update { it.copy(isAddingAccount = false, addAccountError = "アカウント情報の取得に失敗しました") }
                    }
                }
                .onFailure { e ->
                    val error = if (e is SignInCancelledException) null
                                else e.message ?: "認証に失敗しました"
                    _uiState.update { it.copy(isAddingAccount = false, addAccountError = error) }
                }
        }
    }

    fun removeAccount(email: String) {
        viewModelScope.launch {
            val account = iAccountByEmail[email] ?: return@launch
            runCatching {
                msalDeferred.await().getOrThrow().removeAccount(account)
            }
            iAccountByEmail.remove(email)
            _uiState.update { it.copy(accounts = it.accounts.filter { a -> a.email != email }) }
        }
    }

    fun addGoogleAccount(context: Context) {
        viewModelScope.launch {
            val webClientId = context.getString(R.string.google_web_client_id)
            googleAuthManager.signIn(context, webClientId)
        }
    }

    fun removeGoogleAccount() {
        googleAuthManager.signOut()
    }

    fun dismissAddAccountError() {
        _uiState.update { it.copy(addAccountError = null) }
    }

    fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        viewModelScope.launch {
            localSource.updateCalendarVisibility(id, isVisible)
        }
    }
}
