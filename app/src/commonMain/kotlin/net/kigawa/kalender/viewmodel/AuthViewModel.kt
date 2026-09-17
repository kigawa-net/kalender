package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kigawa.kalender.data.auth.AuthController
import net.kigawa.kalender.data.auth.KeycloakAuthState
import net.kigawa.kalender.di.KeyValueStore

private const val PREF_HAD_AUTH = "had_auth"

class AuthViewModel(
    private val authController: AuthController,
    private val settings: KeyValueStore,
) : ViewModel() {

    val authState: StateFlow<KeycloakAuthState> = authController.authState

    // 過去に認証成功した記録。アプリ起動時のスピナー表示を省くために使用する
    private var hadPreviousSession = settings.getBoolean(PREF_HAD_AUTH, false)

    /**
     * null = 初回起動中（認証状態未確定）
     * true = 認証済み or 過去に認証したことがある（バックグラウンド再認証中を含む）
     * false = 未ログイン
     */
    val isLoggedIn: StateFlow<Boolean?> = authState
        .map { state ->
            when {
                state is KeycloakAuthState.SignedIn -> {
                    hadPreviousSession = true
                    true
                }
                state is KeycloakAuthState.Loading -> if (hadPreviousSession) true else null
                else -> {
                    hadPreviousSession = false
                    false
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), if (hadPreviousSession) true else null)

    init {
        viewModelScope.launch {
            isLoggedIn
                .filterNotNull()
                .distinctUntilChanged()
                .collect { loggedIn -> settings.putBoolean(PREF_HAD_AUTH, loggedIn) }
        }

        viewModelScope.launch { authController.trySignInSilently() }
    }

    fun signIn(platformHandle: Any? = null) {
        viewModelScope.launch { authController.signIn(platformHandle) }
    }

    fun signOut() {
        hadPreviousSession = false
        settings.putBoolean(PREF_HAD_AUTH, false)
        authController.signOut()
    }
}
