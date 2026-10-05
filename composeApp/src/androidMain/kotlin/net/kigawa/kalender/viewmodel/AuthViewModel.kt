package net.kigawa.kalender.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.auth.AuthManager
import net.kigawa.kalender.data.auth.SignInCancelledException

actual class AuthViewModel(
    private val googleAuthManager: GoogleAuthManager,
    private val msAuthManager: MsAuthManager,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val scope: kotlinx.coroutines.CoroutineScope = viewModelScope,
) : ViewModel() {

    actual val googleAuthState: StateFlow<AuthManager.State> = googleAuthManager.authState

    private val _msAuthState = MutableStateFlow<MsAuthState>(MsAuthState.Initializing)
    actual val msAuthState: StateFlow<MsAuthState> = _msAuthState

    private val prefs = savedStateHandle.get<android.content.SharedPreferences>("prefs") ?: 
        throw IllegalStateException("SharedPreferences not found in SavedStateHandle")

    @Volatile
    private var hadPreviousSession = prefs.getBoolean("had_auth", false)

    actual val isLoggedIn: StateFlow<Boolean?> = combine(
        googleAuthState,
        msAuthState,
    ) { google, ms ->
        val loggedIn = google is AuthManager.State.SignedIn || ms is MsAuthState.SignedIn
        when {
            loggedIn -> {
                hadPreviousSession = true
                true
            }
            ms is MsAuthState.Initializing || google is AuthManager.State.Loading ->
                if (hadPreviousSession) true else null
            else -> {
                hadPreviousSession = false
                false
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), if (hadPreviousSession) true else null)

    init {
        scope.launch {
            isLoggedIn
                .filterNotNull()
                .distinctUntilChanged()
                .collect { loggedIn ->
                    prefs.edit().putBoolean("had_auth", loggedIn).apply()
                }
        }

        scope.launch {
            _msAuthState.value = MsAuthState.SignedOut
        }

        scope.launch {
            googleAuthManager.trySignInSilently()
        }
    }

    actual fun signInWithGoogle() {
        val context = android.app.ApplicationProvider.getApplicationContext()
        scope.launch {
            googleAuthManager.signIn(context)
        }
    }

    actual fun handleGoogleConsentResult(data: Any?) {
        val context = android.app.ApplicationProvider.getApplicationContext()
        googleAuthManager.handleConsentResult(context, data as Intent?)
    }

    actual fun signInWithMicrosoft() {
        val activity = android.app.Activity.getRunningActivities().firstOrNull() ?: return
        scope.launch {
            _msAuthState.value = MsAuthState.SigningIn
            runCatching { msAuthManager.acquireToken(activity) }
                .onSuccess { token ->
                    val accounts = msAuthManager.getAccounts()
                    val email = accounts.firstOrNull() ?: "Unknown"
                    _msAuthState.value = MsAuthState.SignedIn(email, token)
                }
                .onFailure { e ->
                    _msAuthState.value =
                        if (e is SignInCancelledException) MsAuthState.SignedOut
                        else MsAuthState.Error(e.message ?: "認証に失敗しました")
                }
        }
    }

    actual fun signOut() {
        hadPreviousSession = false
        prefs.edit().putBoolean("had_auth", false).apply()
        googleAuthManager.signOut()
        _msAuthState.value = MsAuthState.SignedOut
    }
}