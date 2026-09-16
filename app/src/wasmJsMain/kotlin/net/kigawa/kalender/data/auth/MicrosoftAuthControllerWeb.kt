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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise

private const val AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
private const val MS_SCOPES = "openid email profile offline_access Calendars.ReadWrite"
private const val KEY_ACCOUNTS = "kalender_ms_accounts"
private const val KEY_ACTIVE_EMAIL = "kalender_ms_active_email"
private const val SESSION_KEY_VERIFIER = "kalender_ms_pkce_verifier"
private const val SESSION_KEY_STATE = "kalender_ms_pkce_state"

@Serializable
private data class StoredMsAccount(val email: String, val refreshToken: String)

/**
 * Web版Microsoft/Outlook認証。Azure ADはSPA(クライアントシークレット無し)からの
 * PKCE認可コード交換を正式サポートしているため、ブラウザリダイレクトによる
 * Authorization Code + PKCE フローを直接実装する。
 *
 * 事前準備: Azure Portalでこのアプリ登録に「シングルページアプリケーション」
 * プラットフォームを追加し、リダイレクトURIとしてこのアプリのオリジンを登録すること。
 */
class MicrosoftAuthControllerWeb(
    private val clientId: String,
    private val httpClient: HttpClient,
) : MicrosoftAuthController {

    private val _authState = MutableStateFlow<MsAuthState>(MsAuthState.Initializing)
    override val authState: StateFlow<MsAuthState> = _authState.asStateFlow()

    private val _storedAccounts = MutableStateFlow(loadAccounts())
    private val _accounts = MutableStateFlow(_storedAccounts.value.map { MicrosoftAccountInfo(it.email) })
    override val accounts: StateFlow<List<MicrosoftAccountInfo>> = _accounts.asStateFlow()

    private fun loadAccounts(): List<StoredMsAccount> = runCatching {
        localStorage.getItem(KEY_ACCOUNTS)?.let { Json.decodeFromString<List<StoredMsAccount>>(it) }
    }.getOrNull() ?: emptyList()

    private fun setStoredAccounts(accounts: List<StoredMsAccount>) {
        _storedAccounts.value = accounts
        _accounts.value = accounts.map { MicrosoftAccountInfo(it.email) }
        localStorage.setItem(KEY_ACCOUNTS, Json.encodeToString(accounts))
    }

    override suspend fun trySignInSilently() {
        val code = jsGetQueryParam("code")?.toString()
        val state = jsGetQueryParam("state")?.toString()
        val storedState = jsSessionStorageGet(SESSION_KEY_STATE)?.toString()
        if (code != null && state != null && state == storedState && state.startsWith("ms:")) {
            handleRedirectCallback(code)
            return
        }

        val activeEmail = localStorage.getItem(KEY_ACTIVE_EMAIL)
        val account = _storedAccounts.value.find { it.email == activeEmail } ?: _storedAccounts.value.firstOrNull()
        if (account == null) {
            _authState.value = MsAuthState.SignedOut
            return
        }
        refreshAccount(account)
    }

    private suspend fun handleRedirectCallback(code: String) {
        jsSessionStorageRemove(SESSION_KEY_STATE)
        val verifier = jsSessionStorageGet(SESSION_KEY_VERIFIER)?.toString()
        jsSessionStorageRemove(SESSION_KEY_VERIFIER)
        jsClearQueryParams()
        if (verifier == null) {
            _authState.value = MsAuthState.Error("認証セッションが見つかりません。再度お試しください。")
            return
        }
        try {
            val tokens = exchangeCodeForToken(code, verifier)
            applyTokens(tokens)
        } catch (e: Exception) {
            _authState.value = MsAuthState.Error(e.message ?: "認証に失敗しました")
        }
    }

    override suspend fun addAccount(platformHandle: Any?) {
        _authState.value = MsAuthState.SigningIn
        val verifier = jsRandomPkceVerifier().toString()
        val challengePromise: Promise<JsString> = jsPkceChallengeS256(verifier)
        val challenge = challengePromise.await<JsString>().toString()
        val state = "ms:" + jsRandomPkceVerifier().toString()
        jsSessionStorageSet(SESSION_KEY_VERIFIER, verifier)
        jsSessionStorageSet(SESSION_KEY_STATE, state)

        val redirectUri = jsRedirectUri().toString()
        val url = buildString {
            append("$AUTHORITY/authorize")
            append("?client_id=").append(clientId.encodeURLParameter())
            append("&response_type=code")
            append("&redirect_uri=").append(redirectUri.encodeURLParameter())
            append("&response_mode=query")
            append("&scope=").append(MS_SCOPES.encodeURLParameter())
            append("&code_challenge=").append(challenge.encodeURLParameter())
            append("&code_challenge_method=S256")
            append("&state=").append(state.encodeURLParameter())
            append("&prompt=select_account")
        }
        jsNavigateTo(url) // ブラウザがページ遷移するため、この関数はここで実質終了する
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): TokenResponse {
        val response = httpClient.submitForm(
            url = "$AUTHORITY/token",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", jsRedirectUri().toString())
                append("code_verifier", verifier)
                append("scope", MS_SCOPES)
            },
        )
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return TokenResponse(
            accessToken = body["access_token"]?.jsonPrimitive?.content
                ?: throw Exception(body["error_description"]?.jsonPrimitive?.content ?: "トークン取得に失敗しました"),
            refreshToken = body["refresh_token"]?.jsonPrimitive?.content,
        )
    }

    private suspend fun refreshAccount(account: StoredMsAccount) {
        try {
            val response = httpClient.submitForm(
                url = "$AUTHORITY/token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("grant_type", "refresh_token")
                    append("refresh_token", account.refreshToken)
                    append("scope", MS_SCOPES)
                },
            )
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val accessToken = body["access_token"]?.jsonPrimitive?.content
            if (accessToken == null) {
                _authState.value = MsAuthState.SignedOut
                return
            }
            val newRefreshToken = body["refresh_token"]?.jsonPrimitive?.content ?: account.refreshToken
            updateStoredAccount(account.email, newRefreshToken)
            localStorage.setItem(KEY_ACTIVE_EMAIL, account.email)
            _authState.value = MsAuthState.SignedIn(account.email, accessToken)
        } catch (e: Exception) {
            _authState.value = MsAuthState.SignedOut
        }
    }

    private suspend fun applyTokens(tokens: TokenResponse) {
        val email = fetchEmail(tokens.accessToken)
        if (tokens.refreshToken != null) {
            updateStoredAccount(email, tokens.refreshToken)
        }
        localStorage.setItem(KEY_ACTIVE_EMAIL, email)
        _authState.value = MsAuthState.SignedIn(email, tokens.accessToken)
    }

    private fun updateStoredAccount(email: String, refreshToken: String) {
        setStoredAccounts(_storedAccounts.value.filterNot { it.email == email } + StoredMsAccount(email, refreshToken))
    }

    private suspend fun fetchEmail(accessToken: String): String {
        val response = httpClient.get("https://graph.microsoft.com/v1.0/me") {
            header("Authorization", "Bearer $accessToken")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return body["mail"]?.jsonPrimitive?.content
            ?: body["userPrincipalName"]?.jsonPrimitive?.content
            ?: "unknown"
    }

    override suspend fun removeAccount(email: String) {
        setStoredAccounts(_storedAccounts.value.filterNot { it.email == email })
        if (localStorage.getItem(KEY_ACTIVE_EMAIL) == email) {
            localStorage.removeItem(KEY_ACTIVE_EMAIL)
        }
        _authState.value = MsAuthState.SignedOut
    }

    override fun signOut() {
        localStorage.removeItem(KEY_ACTIVE_EMAIL)
        _authState.value = MsAuthState.SignedOut
    }

    private data class TokenResponse(val accessToken: String, val refreshToken: String?)
}
