package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kigawa.kalender.di.KeyValueStore
import net.kigawa.kalender.data.auth.GoogleAuthController
import net.kigawa.kalender.data.auth.GoogleAuthState
import net.kigawa.kalender.data.auth.MicrosoftAuthController
import net.kigawa.kalender.data.auth.MsAuthState

private const val PREF_HAD_AUTH = "had_auth"

class AuthViewModel(
    private val googleAuthController: GoogleAuthController,
    private val microsoftAuthController: MicrosoftAuthController,
    private val settings: KeyValueStore,
) : ViewModel() {

    val googleAuthState: StateFlow<GoogleAuthState> = googleAuthController.authState
    val msAuthState: StateFlow<MsAuthState> = microsoftAuthController.authState

    // 過去に認証成功した記録。アプリ起動時のスピナー表示を省くために使用する
    private var hadPreviousSession = settings.getBoolean(PREF_HAD_AUTH, false)

    /**
     * null = 初回起動中（認証状態未確定）
     * true = 認証済み or 過去に認証したことがある（バックグラウンド再認証中を含む）
     * false = 未ログイン
     */
    val isLoggedIn: StateFlow<Boolean?> = combine(
        googleAuthState,
        msAuthState,
    ) { google, ms ->
        val loggedIn = google is GoogleAuthState.SignedIn || ms is MsAuthState.SignedIn
        when {
            loggedIn -> {
                hadPreviousSession = true
                true
            }
            ms is MsAuthState.Initializing || google is GoogleAuthState.Loading ->
                if (hadPreviousSession) true else null
            else -> {
                hadPreviousSession = false
                false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), if (hadPreviousSession) true else null)

    init {
        viewModelScope.launch {
            isLoggedIn
                .filterNotNull()
                .distinctUntilChanged()
                .collect { loggedIn -> settings.putBoolean(PREF_HAD_AUTH, loggedIn) }
        }

        viewModelScope.launch { microsoftAuthController.trySignInSilently() }
        viewModelScope.launch { googleAuthController.trySignInSilently() }
    }

    fun signInWithGoogle(platformHandle: Any? = null) {
        viewModelScope.launch { googleAuthController.signIn(platformHandle) }
    }

    fun signInWithMicrosoft(platformHandle: Any? = null) {
        viewModelScope.launch { microsoftAuthController.addAccount(platformHandle) }
    }

    fun signOut() {
        hadPreviousSession = false
        settings.putBoolean(PREF_HAD_AUTH, false)
        googleAuthController.signOut()
        microsoftAuthController.signOut()
    }
}
