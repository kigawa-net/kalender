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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleAuthManager {

    sealed class AuthState {
        object SignedOut : AuthState()
        object Loading : AuthState()
        data class SignedIn(
            val email: String,
            val displayName: String?,
            val accessToken: String,
        ) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _pendingConsent = Channel<IntentSender>(Channel.BUFFERED)
    val pendingConsent = _pendingConsent.receiveAsFlow()

    private var pendingEmail: String? = null
    private var pendingDisplayName: String? = null

    suspend fun signIn(context: Context, webClientId: String) {
        _authState.value = AuthState.Loading
        try {
            val manager = CredentialManager.create(context)
            val credential = tryGetCredential(manager, context, webClientId, authorized = true)
                ?: tryGetCredential(manager, context, webClientId, authorized = false)
            if (credential == null) {
                _authState.value = AuthState.Error(
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
                requestCalendarScope(context, webClientId)
            } else {
                _authState.value = AuthState.Error("サポートされていない認証タイプ: ${credential.type}")
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "SignIn failed", e)
            val message = when {
                e.message?.contains("Developer console is not set up correctly") == true ->
                    "Google Cloud Console に Android OAuth クライアントが登録されていません。" +
                    "パッケージ名と SHA-1 フィンガープリントを確認してください。"
                else -> e.message ?: "サインインに失敗しました"
            }
            _authState.value = AuthState.Error(message)
        }
    }

    private suspend fun tryGetCredential(
        manager: CredentialManager,
        context: Context,
        webClientId: String,
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

    private suspend fun requestCalendarScope(context: Context, webClientId: String) {
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
            _authState.value = AuthState.Error(message)
            return
        }
        when {
            result.hasResolution() -> {
                result.pendingIntent?.intentSender?.let { _pendingConsent.send(it) }
                    ?: run { _authState.value = AuthState.Error("同意画面の起動に失敗しました") }
            }
            result.accessToken != null -> {
                _authState.value = AuthState.SignedIn(
                    email = pendingEmail ?: "",
                    displayName = pendingDisplayName,
                    accessToken = result.accessToken!!,
                )
            }
            else -> _authState.value = AuthState.Error("アクセストークンの取得に失敗しました (No result)")
        }
    }

    fun handleConsentResult(context: Context, data: Intent?) {
        try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token != null) {
                _authState.value = AuthState.SignedIn(
                    email = pendingEmail ?: "",
                    displayName = pendingDisplayName,
                    accessToken = token,
                )
            } else {
                _authState.value = AuthState.Error("アクセストークンの取得に失敗しました")
            }
        } catch (e: ApiException) {
            Log.e("GoogleAuthManager", "Consent result failed", e)
            val message = when (e.statusCode) {
                12501 -> "サインインがキャンセルされました (12501)"
                else -> "認証に失敗しました (${e.statusCode}): ${e.message}"
            }
            _authState.value = AuthState.Error(message)
        }
    }

    suspend fun trySignInSilently(context: Context, webClientId: String) {
        _authState.value = AuthState.Loading
        try {
            val manager = CredentialManager.create(context)
            val credential = tryGetCredential(manager, context, webClientId, authorized = true)
            if (credential == null ||
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                _authState.value = AuthState.SignedOut
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
                    Identity.getAuthorizationClient(context)
                        .authorize(authRequest)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            } catch (e: Exception) {
                Log.e("GoogleAuthManager", "Silent authorization failed", e)
                _authState.value = AuthState.SignedOut
                return
            }
            if (!result.hasResolution() && result.accessToken != null) {
                _authState.value = AuthState.SignedIn(
                    email = email,
                    displayName = displayName,
                    accessToken = result.accessToken!!,
                )
            } else {
                _authState.value = AuthState.SignedOut
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Silent sign-in failed", e)
            _authState.value = AuthState.SignedOut
        }
    }

    fun signOut() {
        pendingEmail = null
        pendingDisplayName = null
        _authState.value = AuthState.SignedOut
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}
