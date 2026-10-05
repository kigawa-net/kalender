package net.kigawa.kalender.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.identity.client.IAccount
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.auth.AuthManager
import net.kigawa.kalender.data.auth.MsalAuthManager
import net.kigawa.kalender.data.auth.SignInCancelledException
import net.kigawa.kalender.model.UserCalendar

actual class ProfileViewModel(
    private val calendarLocalSource: CalendarLocalSource,
    private val googleAuthManager: GoogleAuthManager,
    private val msAuthManager: MsAuthManager,
    private val scope: kotlinx.coroutines.CoroutineScope = viewModelScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    actual val uiState: StateFlow<ProfileUiState> = _uiState

    private val msalDeferred: Deferred<Result<MsalAuthManager>> = scope.async {
        runCatching { MsalAuthManager.create(android.app.ApplicationProvider.getApplicationContext()) }
    }
    private val iAccountByEmail = mutableMapOf<String, IAccount>()

    init {
        scope.launch {
            msalDeferred.await().onSuccess { manager ->
                runCatching { manager.getAccounts() }.onSuccess { accounts ->
                    accounts.forEach { iAccountByEmail[it.username] = it }
                    _uiState.update {
                        it.copy(accounts = accounts.map { a -> OutlookAccount(a.username) })
                    }
                }
            }
        }
        scope.launch {
            googleAuthManager.authState.collect { state ->
                _uiState.update {
                    when (state) {
                        is AuthManager.State.SignedIn -> it.copy(
                            googleAccount = GoogleAccount(state.email, state.displayName),
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = null
                        )
                        is AuthManager.State.SignedOut -> it.copy(
                            googleAccount = null,
                            isAddingGoogleAccount = false
                        )
                        is AuthManager.State.Loading -> it.copy(
                            isAddingGoogleAccount = true,
                            addGoogleAccountError = null
                        )
                        is AuthManager.State.Error -> it.copy(
                            isAddingGoogleAccount = false,
                            addGoogleAccountError = state.message
                        )
                    }
                }
            }
        }
        scope.launch {
            calendarLocalSource.observeCalendars().collect { calendars ->
                _uiState.update { it.copy(calendarsByOwnerEmail = calendars.groupBy { c -> c.ownerEmail }) }
            }
        }
    }

    actual fun addAccount(activity: Activity) {
        scope.launch {
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

    actual fun removeAccount(email: String) {
        scope.launch {
            val account = iAccountByEmail[email] ?: return@launch
            runCatching {
                msalDeferred.await().getOrThrow().removeAccount(account)
            }
            iAccountByEmail.remove(email)
            _uiState.update { it.copy(accounts = it.accounts.filter { a -> a.email != email }) }
            calendarLocalSource.deleteCalendarsByOwnerEmail(email)
        }
    }

    actual fun addGoogleAccount(context: Context) {
        scope.launch {
            val webClientId = context.getString(net.kigawa.kalender.R.string.google_web_client_id)
            googleAuthManager.signIn(context, webClientId)
        }
    }

    actual fun removeGoogleAccount() {
        val email = (googleAuthManager.authState.value as? AuthManager.State.SignedIn)?.email
        googleAuthManager.signOut()
        if (email != null) {
            scope.launch {
                calendarLocalSource.deleteCalendarsByOwnerEmail(email)
            }
        }
    }

    actual fun dismissAddAccountError() {
        _uiState.update { it.copy(addAccountError = null) }
    }

    actual fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        scope.launch {
            calendarLocalSource.updateCalendarVisibility(id, isVisible)
        }
    }
}