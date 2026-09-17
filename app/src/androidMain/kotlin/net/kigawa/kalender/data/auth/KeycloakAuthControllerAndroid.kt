package net.kigawa.kalender.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom

private const val REDIRECT_SCHEME = "net.kigawa.kalender"
private const val REDIRECT_HOST = "oauth2redirect"
const val KEYCLOAK_REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

private const val KC_SCOPES = "openid email profile offline_access"
private const val PREFS_NAME = "kalender_keycloak_auth"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_VERIFIER = "pkce_verifier"
private const val KEY_STATE = "pkce_state"

/**
 * Android版kigawa-net共有Keycloakログイン。System Browser(Custom Tabs)による
 * Authorization Code + PKCEフローを実装する。リダイレクトはAndroidManifestのカスタムスキーム
 * intent-filter経由でMainActivityが受け取り、[handleRedirectIntent]へ渡す。
 */
class KeycloakAuthControllerAndroid(
    private val applicationContext: Context,
    private val appScope: CoroutineScope,
    private val realmUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient,
) : AuthController {

    private val _authState = MutableStateFlow<KeycloakAuthState>(KeycloakAuthState.Loading)
    override val authState: StateFlow<KeycloakAuthState> = _authState.asStateFlow()

    private val prefs get() = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun trySignInSilently() {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken == null) {
            _authState.value = KeycloakAuthState.SignedOut
            return
        }
        refreshTokens(refreshToken)
    }

    override suspend fun signIn(platformHandle: Any?) {
        val context = (platformHandle as? Context) ?: applicationContext
        _authState.value = KeycloakAuthState.Loading
        val verifier = randomVerifier()
        val challenge = challengeS256(verifier)
        val state = "kc:" + randomVerifier()
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()

        val url = buildString {
            append("$realmUrl/protocol/openid-connect/auth")
            append("?client_id=").append(Uri.encode(clientId))
            append("&response_type=code")
            append("&redirect_uri=").append(Uri.encode(KEYCLOAK_REDIRECT_URI))
            append("&scope=").append(Uri.encode(KC_SCOPES))
            append("&code_challenge=").append(Uri.encode(challenge))
            append("&code_challenge_method=S256")
            append("&state=").append(Uri.encode(state))
        }
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    /** AndroidManifestのカスタムスキームintent-filter経由でMainActivityが受け取ったリダイレクトを処理する */
    fun handleRedirectIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST) return

        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val storedState = prefs.getString(KEY_STATE, null)
        prefs.edit().remove(KEY_STATE).remove(KEY_VERIFIER).apply()

        if (error != null) {
            _authState.value = KeycloakAuthState.Error("認証エラー: $error")
            return
        }
        if (code == null || state == null || state != storedState) {
            return
        }
        val verifier = prefs.getString(KEY_VERIFIER, null)
        appScope.launch {
            try {
                if (verifier == null) {
                    _authState.value = KeycloakAuthState.Error("認証セッションが見つかりません。再度お試しください。")
                    return@launch
                }
                val tokens = exchangeCodeForToken(code, verifier)
                applyTokens(tokens)
            } catch (e: Exception) {
                Log.e("KeycloakAuth", "Token exchange failed", e)
                _authState.value = KeycloakAuthState.Error(e.message ?: "認証に失敗しました")
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): TokenResponse {
        val response = httpClient.submitForm(
            url = "$realmUrl/protocol/openid-connect/token",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", KEYCLOAK_REDIRECT_URI)
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
                prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
                _authState.value = KeycloakAuthState.SignedOut
                return
            }
            val newRefreshToken = body["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            prefs.edit().putString(KEY_REFRESH_TOKEN, newRefreshToken).apply()
            applyAccessToken(accessToken)
        } catch (e: Exception) {
            Log.e("KeycloakAuth", "Silent refresh failed", e)
            _authState.value = KeycloakAuthState.SignedOut
        }
    }

    private suspend fun applyTokens(tokens: TokenResponse) {
        tokens.refreshToken?.let { prefs.edit().putString(KEY_REFRESH_TOKEN, it).apply() }
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
        prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
        _authState.value = KeycloakAuthState.SignedOut
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private data class TokenResponse(val accessToken: String, val refreshToken: String?)
}
