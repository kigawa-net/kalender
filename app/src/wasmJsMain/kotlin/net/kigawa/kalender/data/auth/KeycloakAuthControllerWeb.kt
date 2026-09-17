@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kigawa.kalender.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise

private const val KC_SCOPES = "openid email profile offline_access"
private const val KEY_REFRESH_TOKEN = "kalender_kc_refresh_token"
private const val SESSION_KEY_VERIFIER = "kalender_kc_pkce_verifier"
private const val SESSION_KEY_STATE = "kalender_kc_pkce_state"

/**
 * Web版kigawa-net共有Keycloakログイン。Authorization Code + PKCEフローを
 * (Microsoft Web実装と同じ既存のPKCE/セッションストレージJS interopヘルパーを再利用して)直接実装する。
 */
class KeycloakAuthControllerWeb(
    private val realmUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient,
) : AuthController {

    private val _authState = MutableStateFlow<KeycloakAuthState>(KeycloakAuthState.Loading)
    override val authState: StateFlow<KeycloakAuthState> = _authState.asStateFlow()

    override suspend fun trySignInSilently() {
        val code = jsGetQueryParam("code")?.toString()
        val state = jsGetQueryParam("state")?.toString()
        val storedState = jsSessionStorageGet(SESSION_KEY_STATE)?.toString()
        if (code != null && state != null && state == storedState && state.startsWith("kc:")) {
            handleRedirectCallback(code)
            return
        }

        val refreshToken = localStorage.getItem(KEY_REFRESH_TOKEN)
        if (refreshToken == null) {
            _authState.value = KeycloakAuthState.SignedOut
            return
        }
        refreshTokens(refreshToken)
    }

    override suspend fun signIn(platformHandle: Any?) {
        _authState.value = KeycloakAuthState.Loading
        val verifier = jsRandomPkceVerifier().toString()
        val challengePromise: Promise<JsString> = jsPkceChallengeS256(verifier)
        val challenge = challengePromise.await<JsString>().toString()
        val state = "kc:" + jsRandomPkceVerifier().toString()
        jsSessionStorageSet(SESSION_KEY_VERIFIER, verifier)
        jsSessionStorageSet(SESSION_KEY_STATE, state)

        val redirectUri = jsRedirectUri().toString()
        val url = buildString {
            append("$realmUrl/protocol/openid-connect/auth")
            append("?client_id=").append(clientId.encodeURLParameter())
            append("&response_type=code")
            append("&redirect_uri=").append(redirectUri.encodeURLParameter())
            append("&scope=").append(KC_SCOPES.encodeURLParameter())
            append("&code_challenge=").append(challenge.encodeURLParameter())
            append("&code_challenge_method=S256")
            append("&state=").append(state.encodeURLParameter())
        }
        jsNavigateTo(url) // ブラウザがページ遷移するため、この関数はここで実質終了する
    }

    private suspend fun handleRedirectCallback(code: String) {
        jsSessionStorageRemove(SESSION_KEY_STATE)
        val verifier = jsSessionStorageGet(SESSION_KEY_VERIFIER)?.toString()
        jsSessionStorageRemove(SESSION_KEY_VERIFIER)
        jsClearQueryParams()
        if (verifier == null) {
            _authState.value = KeycloakAuthState.Error("認証セッションが見つかりません。再度お試しください。")
            return
        }
        try {
            val tokens = exchangeCodeForToken(code, verifier)
            applyTokens(tokens)
        } catch (e: Exception) {
            _authState.value = KeycloakAuthState.Error(e.message ?: "認証に失敗しました")
        }
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): TokenResponse {
        val response = httpClient.submitForm(
            url = "$realmUrl/protocol/openid-connect/token",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", jsRedirectUri().toString())
                append("code_verifier", verifier)
            },
        )
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return TokenResponse(
            accessToken = body["access_token"]?.jsonPrimitive?.content
                ?: throw Exception(body["error_description"]?.jsonPrimitive?.content ?: "トークン取得に失敗しました"),
            refreshToken = body["refresh_token"]?.jsonPrimitive?.content,
        )
    }

    private suspend fun refreshTokens(refreshToken: String) {
        try {
            val response = httpClient.submitForm(
                url = "$realmUrl/protocol/openid-connect/token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                },
            )
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val accessToken = body["access_token"]?.jsonPrimitive?.content
            if (accessToken == null) {
                localStorage.removeItem(KEY_REFRESH_TOKEN)
                _authState.value = KeycloakAuthState.SignedOut
                return
            }
            val newRefreshToken = body["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            localStorage.setItem(KEY_REFRESH_TOKEN, newRefreshToken)
            applyAccessToken(accessToken)
        } catch (e: Exception) {
            _authState.value = KeycloakAuthState.SignedOut
        }
    }

    private suspend fun applyTokens(tokens: TokenResponse) {
        tokens.refreshToken?.let { localStorage.setItem(KEY_REFRESH_TOKEN, it) }
        applyAccessToken(tokens.accessToken)
    }

    private suspend fun applyAccessToken(accessToken: String) {
        val info = fetchUserInfo(accessToken)
        val email = info["email"]?.jsonPrimitive?.content ?: ""
        val name = info["name"]?.jsonPrimitive?.content
        _authState.value = KeycloakAuthState.SignedIn(accessToken = accessToken, email = email, displayName = name)
    }

    private suspend fun fetchUserInfo(accessToken: String): JsonObject {
        val response = httpClient.get("$realmUrl/protocol/openid-connect/userinfo") {
            header("Authorization", "Bearer $accessToken")
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    override fun signOut() {
        localStorage.removeItem(KEY_REFRESH_TOKEN)
        _authState.value = KeycloakAuthState.SignedOut
    }

    private data class TokenResponse(val accessToken: String, val refreshToken: String?)
}
