package net.kigawa.kalender.data.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.IAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android(MSAL)によるMicrosoft/Outlook認証実装。複数アカウントを保持できるが、
 * イベントの読み書きに使う「アクティブな」アカウントは authState が表す一つのみ
 * （元のAndroid実装と同じ仕様を踏襲）。
 */
class MicrosoftAuthControllerAndroid(
    private val context: Context,
    scope: CoroutineScope,
) : MicrosoftAuthController {

    private val _authState = MutableStateFlow<MsAuthState>(MsAuthState.Initializing)
    override val authState: StateFlow<MsAuthState> = _authState.asStateFlow()

    private val _accounts = MutableStateFlow<List<MicrosoftAccountInfo>>(emptyList())
    override val accounts: StateFlow<List<MicrosoftAccountInfo>> = _accounts.asStateFlow()

    private val iAccountByEmail = mutableMapOf<String, IAccount>()

    private val managerDeferred: Deferred<Result<MsalAuthManager>> = scope.async {
        runCatching { MsalAuthManager.create(context) }
    }

    override suspend fun trySignInSilently() {
        managerDeferred.await()
            .onSuccess { manager ->
                val allAccounts = runCatching { manager.getAccounts() }.getOrDefault(emptyList())
                allAccounts.forEach { iAccountByEmail[it.username] = it }
                _accounts.value = allAccounts.map { MicrosoftAccountInfo(it.username) }
                val active = allAccounts.firstOrNull()
                _authState.value = if (active != null) {
                    val token = runCatching { manager.acquireTokenSilent(active) }.getOrNull()
                    if (token != null) MsAuthState.SignedIn(active.username, token) else MsAuthState.SignedOut
                } else {
                    MsAuthState.SignedOut
                }
            }
            .onFailure { _authState.value = MsAuthState.SignedOut }
    }

    override suspend fun addAccount(platformHandle: Any?) {
        val activity = platformHandle as? Activity
        if (activity == null) {
            _authState.value = MsAuthState.Error("Activityが必要です")
            return
        }
        _authState.value = MsAuthState.SigningIn
        managerDeferred.await()
            .onSuccess { manager ->
                runCatching { manager.acquireToken(activity) }
                    .onSuccess { token ->
                        val allAccounts = manager.getAccounts()
                        allAccounts.forEach { iAccountByEmail[it.username] = it }
                        _accounts.value = allAccounts.map { MicrosoftAccountInfo(it.username) }
                        val email = allAccounts.firstOrNull()?.username ?: "Unknown"
                        _authState.value = MsAuthState.SignedIn(email, token)
                    }
                    .onFailure { e ->
                        _authState.value =
                            if (e is SignInCancelledException) MsAuthState.SignedOut
                            else MsAuthState.Error(e.message ?: "認証に失敗しました")
                    }
            }
            .onFailure { e -> _authState.value = MsAuthState.Error(e.message ?: "MSAL初期化に失敗しました") }
    }

    override suspend fun removeAccount(email: String) {
        val account = iAccountByEmail[email] ?: return
        val managerResult = managerDeferred.await()
        runCatching { managerResult.getOrThrow().removeAccount(account) }
        iAccountByEmail.remove(email)
        _accounts.value = _accounts.value.filter { it.email != email }
        _authState.value = MsAuthState.SignedOut
    }

    override fun signOut() {
        _authState.value = MsAuthState.SignedOut
    }
}
