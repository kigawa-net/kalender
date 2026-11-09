@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kigawa.kalender.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise

/**
 * Google OAuth2 PKCE フローによる Google Calendar API 認証。
 * google-client-secret ファイルにクライアント ID を設定する必要があります。
 * 
 * 使用方法:
 * 1. Google Cloud Console で OAuth クライアントを作成
 * 2. クライアント ID を google-client-secret ファイルに保存
 * 3. アプリを再ビルド
 */
class GoogleCalendarAuth(
    private val httpClient: HttpClient,
    private val clientId: String = getGoogleClientId(),
) : AuthController {

    private val _authState = MutableStateFlow<KeycloakAuthState>(KeycloakAuthState.SignedOut)
    override val authState: StateFlow<KeycloakAuthState> = _authState.asStateFlow()

    companion object {
        // 環境変数やファイルからクライアント ID を取得
        private fun getGoogleClientId(): String {
            // 環境変数やファイルから読み込む
            return "" // TODO: google-client-secret ファイルから読み込む
        }
    }

    override suspend fun trySignInSilently() {
        // シークレットセッション認証は GIS SDK が必要
        _authState.value = KeycloakAuthState.SignedOut
    }

    override suspend fun signIn(platformHandle: Any?) {
        _authState.value = KeycloakAuthState.Loading
        signInWithGoogle()
    }

    private suspend fun signInWithGoogle() {
        try {
            val verifier = jsRandomPkceVerifier().toString()
            val challengePromise: Promise<JsString> = jsPkceChallengeS256(verifier)
            val challenge = challengePromise.await<JsString>().toString()

            // 認証 URL を生成
            val redirectUri = jsRedirectUri().toString()
            val authUrl = buildGoogleAuthUrl(clientId, verifier, challenge, redirectUri)

            // ブラウザで認証画面を開く
            jsNavigateTo(authUrl)
        } catch (e: Exception) {
            _authState.value = KeycloakAuthState.Error(e.message ?: "認証に失敗しました")
        }
    }

    private fun buildGoogleAuthUrl(
        clientId: String,
        verifier: String,
        challenge: String,
        redirectUri: String
    ): String {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&scope=https://www.googleapis.com/auth/calendar.readonly" +
            "&access_type=offline" +
            "&prompt=select_account" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }


    override fun signOut() {
        _authState.value = KeycloakAuthState.SignedOut
    }
}
