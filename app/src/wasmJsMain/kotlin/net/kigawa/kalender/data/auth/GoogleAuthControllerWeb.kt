package net.kigawa.kalender.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise

private const val KEY_GOOGLE_TOKEN = "kalender_google_access_token"
private const val KEY_GOOGLE_EMAIL = "kalender_google_email"
private const val KEY_GOOGLE_NAME = "kalender_google_display_name"

private const val GOOGLE_SCOPES =
    "openid email profile https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events"

/**
 * Web版Google認証。GoogleのOAuthトークンエンドポイントはクライアントシークレット無しの
 * 認可コード交換を正式サポートしないため、Google Identity Services (GIS) の
 * token client (ポップアップでアクセストークンを直接返す) を利用する。
 */
class GoogleAuthControllerWeb(private val webClientId: String, private val httpClient: HttpClient) : GoogleAuthController {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.SignedOut)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    override suspend fun signIn(platformHandle: Any?) {
        _authState.value = GoogleAuthState.Loading
        try {
            val promise: Promise<JsString> = jsRequestGoogleAccessToken(webClientId, GOOGLE_SCOPES)
            val token = promise.await<JsString>().toString()
            applyToken(token, persist = true)
        } catch (e: Exception) {
            _authState.value = GoogleAuthState.Error(e.message ?: "サインインに失敗しました")
        }
    }

    override suspend fun trySignInSilently() {
        val storedToken = localStorage.getItem(KEY_GOOGLE_TOKEN)
        val storedEmail = localStorage.getItem(KEY_GOOGLE_EMAIL)
        if (storedToken != null && storedEmail != null) {
            // ブラウザのアクセストークンは長期間有効なリフレッシュトークンを持たないため、
            // 前回のトークンが有効か軽く検証してから復元する。
            val stillValid = runCatching { fetchUserInfo(storedToken) }.isSuccess
            if (stillValid) {
                _authState.value = GoogleAuthState.SignedIn(
                    email = storedEmail,
                    displayName = localStorage.getItem(KEY_GOOGLE_NAME),
                    accessToken = storedToken,
                )
                return
            }
        }
        _authState.value = GoogleAuthState.SignedOut
    }

    private suspend fun applyToken(token: String, persist: Boolean) {
        val info = fetchUserInfo(token)
        val email = info["email"]?.jsonPrimitive?.content ?: ""
        val name = info["name"]?.jsonPrimitive?.content
        if (persist) {
            localStorage.setItem(KEY_GOOGLE_TOKEN, token)
            localStorage.setItem(KEY_GOOGLE_EMAIL, email)
            name?.let { localStorage.setItem(KEY_GOOGLE_NAME, it) }
        }
        _authState.value = GoogleAuthState.SignedIn(email = email, displayName = name, accessToken = token)
    }

    private suspend fun fetchUserInfo(token: String): kotlinx.serialization.json.JsonObject {
        val response = httpClient.get("https://openidconnect.googleapis.com/v1/userinfo") {
            header("Authorization", "Bearer $token")
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    override fun signOut() {
        localStorage.removeItem(KEY_GOOGLE_TOKEN)
        localStorage.removeItem(KEY_GOOGLE_EMAIL)
        localStorage.removeItem(KEY_GOOGLE_NAME)
        _authState.value = GoogleAuthState.SignedOut
    }
}
