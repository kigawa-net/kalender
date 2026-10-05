package net.kigawa.kalender.data.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.web.dom.document
import org.jetbrains.compose.web.dom.getElementById
import org.jetbrains.compose.web.dom.window

class GoogleAuthManagerImpl : GoogleAuthManager {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _pendingConsent = Channel<String>(Channel.BUFFERED)
    override val pendingConsent = _pendingConsent.receiveAsFlow()

    private var gisClient: dynamic = null
    private var tokenClient: dynamic = null

    override suspend fun signIn(webClientId: String) {
        _authState.value = AuthState.Loading
        try {
            initGis(webClientId)
            tokenClient = window.google.accounts.oauth2.initTokenClient({
                client_id = webClientId,
                scope = "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events",
                callback = { response: dynamic ->
                    if (response.access_token != null) {
                        _authState.value = AuthState.SignedIn(
                            email = response.email ?: "",
                            displayName = null,
                            accessToken = response.access_token,
                        )
                    } else {
                        _authState.value = AuthState.Error("アクセストークンの取得に失敗しました")
                    }
                },
                error_callback = { error: dynamic ->
                    _authState.value = AuthState.Error("認証エラー: ${error.error}")
                },
            })
            tokenClient.requestAccessToken()
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "サインインに失敗しました")
        }
    }

    private fun initGis(webClientId: String) {
        if (gisClient == null) {
            // Load GIS script if not loaded
            if (!window.hasOwnProperty("google")) {
                val script = document.createElement("script")
                script.src = "https://accounts.google.com/gsi/client"
                script.async = true
                script.onload = {
                    initGisInternal(webClientId)
                }
                document.head.appendChild(script)
            } else {
                initGisInternal(webClientId)
            }
        }
    }

    private fun initGisInternal(webClientId: String) {
        gisClient = window.google.accounts.id.initialize({
            client_id = webClientId,
            callback = { response: dynamic ->
                // Handle credential response if needed
            },
        })
    }

    override fun handleConsentResult(data: Any?) {
        // Not used in Web GIS flow
    }

    override suspend fun trySignInSilently(webClientId: String) {
        _authState.value = AuthState.Loading
        try {
            initGis(webClientId)
            val tokenClient = window.google.accounts.oauth2.initTokenClient({
                client_id = webClientId,
                scope = "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events",
                prompt = "",
                callback = { response: dynamic ->
                    if (response.access_token != null) {
                        _authState.value = AuthState.SignedIn(
                            email = response.email ?: "",
                            displayName = null,
                            accessToken = response.access_token,
                        )
                    } else {
                        _authState.value = AuthState.SignedOut
                    }
                },
            })
            tokenClient.requestAccessToken()
        } catch (e: Exception) {
            _authState.value = AuthState.SignedOut
        }
    }

    override fun signOut() {
        _authState.value = AuthState.SignedOut
        // Revoke token if needed
        window.google?.accounts?.oauth2?.revoke(null, { })
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}