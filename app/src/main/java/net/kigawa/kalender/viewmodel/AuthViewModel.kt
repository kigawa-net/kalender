package net.kigawa.kalender.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kigawa.kalender.KalenderApplication
import net.kigawa.kalender.R
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.data.auth.MsalAuthManager
import net.kigawa.kalender.data.auth.SignInCancelledException

sealed class MsAuthState {
    object Initializing : MsAuthState()
    object SignedOut : MsAuthState()
    object SigningIn : MsAuthState()
    data class SignedIn(val email: String, val accessToken: String) : MsAuthState()
    data class Error(val message: String) : MsAuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val googleAuthManager = (application as KalenderApplication).googleAuthManager

    val googleAuthState: StateFlow<GoogleAuthManager.AuthState> = googleAuthManager.authState

    private val _msAuthState = MutableStateFlow<MsAuthState>(MsAuthState.Initializing)
    val msAuthState: StateFlow<MsAuthState> = _msAuthState

    private val msalDeferred: Deferred<Result<MsalAuthManager>> = viewModelScope.async {
        runCatching { MsalAuthManager.create(getApplication()) }
    }

    /** null = まだ初期状態確認中, true = 認証済み, false = 未ログイン */
    val isLoggedIn: StateFlow<Boolean?> = combine(
        googleAuthState,
        msAuthState,
    ) { google, ms ->
        when {
            google is GoogleAuthManager.AuthState.SignedIn || ms is MsAuthState.SignedIn -> true
            ms is MsAuthState.Initializing || google is GoogleAuthManager.AuthState.Loading -> null
            else -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            msalDeferred.await()
                .onSuccess { manager ->
                    val accounts = runCatching { manager.getAccounts() }.getOrDefault(emptyList())
                    if (accounts.isNotEmpty()) {
                        val account = accounts[0]
                        val token = runCatching { manager.acquireTokenSilent(account) }.getOrNull()
                        if (token != null) {
                            (getApplication() as KalenderApplication).msAccessToken.value = token
                            _msAuthState.value = MsAuthState.SignedIn(account.username, token)
                        } else {
                            _msAuthState.value = MsAuthState.SignedOut
                        }
                    } else {
                        _msAuthState.value = MsAuthState.SignedOut
                    }
                }
                .onFailure { _msAuthState.value = MsAuthState.SignedOut }
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val webClientId = context.getString(R.string.google_web_client_id)
            googleAuthManager.trySignInSilently(context, webClientId)
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val webClientId = context.getString(R.string.google_web_client_id)
            googleAuthManager.signIn(context, webClientId)
        }
    }

    fun handleGoogleConsentResult(context: Context, data: Intent?) {
        googleAuthManager.handleConsentResult(context, data)
    }

    fun signInWithMicrosoft(activity: Activity) {
        viewModelScope.launch {
            _msAuthState.value = MsAuthState.SigningIn
            msalDeferred.await()
                .onSuccess { manager ->
                    runCatching { manager.acquireToken(activity) }
                        .onSuccess { token ->
                            val accounts = manager.getAccounts()
                            val email = accounts.firstOrNull()?.username ?: "Unknown"
                            (getApplication() as KalenderApplication).msAccessToken.value = token
                            _msAuthState.value = MsAuthState.SignedIn(email, token)
                        }
                        .onFailure { e ->
                            _msAuthState.value =
                                if (e is SignInCancelledException) MsAuthState.SignedOut
                                else MsAuthState.Error(e.message ?: "認証に失敗しました")
                        }
                }
                .onFailure { e ->
                    _msAuthState.value = MsAuthState.Error(e.message ?: "MSAL初期化に失敗しました")
                }
        }
    }

    fun signOut() {
        googleAuthManager.signOut()
        (getApplication() as KalenderApplication).msAccessToken.value = null
        _msAuthState.value = MsAuthState.SignedOut
    }
}
