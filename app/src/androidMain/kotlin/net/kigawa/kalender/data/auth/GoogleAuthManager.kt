package net.kigawa.kalender.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android(Credential Manager + Identity Authorization Client)によるGoogle認証実装。
 * サインイン中の追加同意ダイアログはActivity側でしか起動できないため、
 * [pendingConsent]/[handleConsentResult] はこのクラス固有の公開APIとして残し、
 * MainActivityがこの具象型を直接参照して配線する。
 */
class GoogleAuthControllerAndroid(
    private val applicationContext: Context,
    private val webClientId: String,
) : GoogleAuthController {

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.SignedOut)
    override val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private val _pendingConsent = Channel<IntentSender>(Channel.BUFFERED)
    val pendingConsent: Flow<IntentSender> = _pendingConsent.receiveAsFlow()

    private var pendingEmail: String? = null
    private var pendingDisplayName: String? = null

    override suspend fun signIn(platformHandle: Any?) {
        val context = (platformHandle as? Context) ?: applicationContext
        _authState.value = GoogleAuthState.Loading
        try {
            val manager = CredentialManager.create(context)
            val credential = tryGetCredential(manager, context, authorized = true)
                ?: tryGetCredential(manager, context, authorized = false)
            if (credential == null) {
                _authState.value = GoogleAuthState.Error(
                    "デバイスにGoogleアカウントが登録されていません。\n" +
                        "設定アプリからGoogleアカウントを追加してください。"
                )
                return
            }
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                pendingEmail = googleCred.id
                pendingDisplayName = googleCred.displayName
                requestCalendarScope(context)
            } else {
                _authState.value = GoogleAuthState.Error("サポートされていない認証タイプ: ${credential.type}")
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "SignIn failed", e)
            val message = when {
                e.message?.contains("Developer console is not set up correctly") == true ->
                    "Google Cloud Console に Android OAuth クライアントが登録されていません。" +
                        "パッケージ名と SHA-1 フィンガープリントを確認してください。"
                else -> e.message ?: "サインインに失敗しました"
            }
            _authState.value = GoogleAuthState.Error(message)
        }
    }

    private suspend fun tryGetCredential(
        manager: CredentialManager,
        context: Context,
        authorized: Boolean,
    ) = try {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorized)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(authorized)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        manager.getCredential(context, request).credential
    } catch (e: NoCredentialException) {
        null
    } catch (e: Exception) {
        throw e
    }

    private suspend fun requestCalendarScope(context: Context) {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(CALENDAR_EVENTS_SCOPE)))
            .setAccount(Account(pendingEmail ?: "", "com.google"))
            .build()
        val result = try {
            suspendCancellableCoroutine<AuthorizationResult> { cont ->
                Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } catch (e: ApiException) {
            Log.e("GoogleAuthManager", "Authorization failed", e)
            val message = when (e.statusCode) {
                10 -> "開発者エラー (10): Google Cloud Console の設定（パッケージ名、SHA-1）を確認してください。"
                else -> "認証エラー (${e.statusCode}): ${e.message}"
            }
            _authState.value = GoogleAuthState.Error(message)
            return
        }
        when {
            result.hasResolution() -> {
                result.pendingIntent?.intentSender?.let { _pendingConsent.send(it) }
                    ?: run { _authState.value = GoogleAuthState.Error("同意画面の起動に失敗しました") }
            }
            result.accessToken != null -> {
                _authState.value = GoogleAuthState.SignedIn(
                    email = pendingEmail ?: "",
                    displayName = pendingDisplayName,
                    accessToken = result.accessToken!!,
                )
            }
            else -> _authState.value = GoogleAuthState.Error("アクセストークンの取得に失敗しました (No result)")
        }
    }

    fun handleConsentResult(context: Context, data: Intent?) {
        try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token != null) {
                _authState.value = GoogleAuthState.SignedIn(
                    email = pendingEmail ?: "",
                    displayName = pendingDisplayName,
                    accessToken = token,
                )
            } else {
                _authState.value = GoogleAuthState.Error("アクセストークンの取得に失敗しました")
            }
        } catch (e: ApiException) {
            Log.e("GoogleAuthManager", "Consent result failed", e)
            val message = when (e.statusCode) {
                12501 -> "サインインがキャンセルされました (12501)"
                else -> "認証に失敗しました (${e.statusCode}): ${e.message}"
            }
            _authState.value = GoogleAuthState.Error(message)
        }
    }

    override suspend fun trySignInSilently() {
        _authState.value = GoogleAuthState.Loading
        try {
            val manager = CredentialManager.create(applicationContext)
            val credential = tryGetCredential(manager, applicationContext, authorized = true)
            if (credential == null ||
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                _authState.value = GoogleAuthState.SignedOut
                return
            }
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val email = googleCred.id
            val displayName = googleCred.displayName
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(CALENDAR_EVENTS_SCOPE)))
                .setAccount(Account(email, "com.google"))
                .build()
            val result = try {
                suspendCancellableCoroutine<AuthorizationResult> { cont ->
                    Identity.getAuthorizationClient(applicationContext)
                        .authorize(authRequest)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            } catch (e: Exception) {
                Log.e("GoogleAuthManager", "Silent authorization failed", e)
                _authState.value = GoogleAuthState.SignedOut
                return
            }
            if (!result.hasResolution() && result.accessToken != null) {
                _authState.value = GoogleAuthState.SignedIn(
                    email = email,
                    displayName = displayName,
                    accessToken = result.accessToken!!,
                )
            } else {
                _authState.value = GoogleAuthState.SignedOut
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Silent sign-in failed", e)
            _authState.value = GoogleAuthState.SignedOut
        }
    }

    override fun signOut() {
        pendingEmail = null
        pendingDisplayName = null
        _authState.value = GoogleAuthState.SignedOut
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}
