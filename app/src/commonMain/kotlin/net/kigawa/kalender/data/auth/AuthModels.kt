package net.kigawa.kalender.data.auth

import kotlinx.coroutines.flow.StateFlow

sealed class GoogleAuthState {
    object SignedOut : GoogleAuthState()
    object Loading : GoogleAuthState()
    data class SignedIn(val email: String, val displayName: String?, val accessToken: String) : GoogleAuthState()
    data class Error(val message: String) : GoogleAuthState()
}

/**
 * Google カレンダー認証の共通インターフェース。
 * platformHandle: Android では Context、Webでは無視される(サインインはブラウザリダイレクトで行う)
 */
interface GoogleAuthController {
    val authState: StateFlow<GoogleAuthState>
    suspend fun trySignInSilently()
    suspend fun signIn(platformHandle: Any? = null)
    fun signOut()
}

sealed class MsAuthState {
    object Initializing : MsAuthState()
    object SignedOut : MsAuthState()
    object SigningIn : MsAuthState()
    data class SignedIn(val email: String, val accessToken: String) : MsAuthState()
    data class Error(val message: String) : MsAuthState()
}

data class MicrosoftAccountInfo(val email: String)

/**
 * Microsoft(Outlook)認証の共通インターフェース。複数アカウントを保持できるが、
 * イベントの読み書きに使われる「アクティブな」アカウントは authState が表す一つのみ。
 * platformHandle: Android では Activity、Webでは無視される。
 */
interface MicrosoftAuthController {
    val authState: StateFlow<MsAuthState>
    val accounts: StateFlow<List<MicrosoftAccountInfo>>
    suspend fun trySignInSilently()
    suspend fun addAccount(platformHandle: Any? = null)
    suspend fun removeAccount(email: String)
    fun signOut()
}
