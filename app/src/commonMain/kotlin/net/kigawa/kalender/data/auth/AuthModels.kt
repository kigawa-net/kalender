package net.kigawa.kalender.data.auth

import kotlinx.coroutines.flow.StateFlow

sealed class KeycloakAuthState {
    object SignedOut : KeycloakAuthState()
    object Loading : KeycloakAuthState()
    data class SignedIn(val accessToken: String, val email: String, val displayName: String?) : KeycloakAuthState()
    data class Error(val message: String) : KeycloakAuthState()
}

/**
 * kigawa-net共有Keycloak(realm kigawa-net)によるログインの共通インターフェース。
 * Google/Microsoftへの個別ログインはKeycloak側のIdentity Brokeringに委譲し、
 * このアプリはKeycloakの発行するアクセストークン1種類のみを扱う。
 * platformHandle: Android では Activity/Context、Webでは無視される(サインインはブラウザリダイレクトで行う)
 */
interface AuthController {
    val authState: StateFlow<KeycloakAuthState>
    suspend fun trySignInSilently()
    suspend fun signIn(platformHandle: Any? = null)
    fun signOut()
}
