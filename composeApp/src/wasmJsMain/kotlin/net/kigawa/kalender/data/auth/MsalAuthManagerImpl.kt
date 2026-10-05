package net.kigawa.kalender.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import org.jetbrains.compose.web.dom.document
import org.jetbrains.compose.web.dom.getElementById
import org.jetbrains.compose.web.dom.window

class SignInCancelledException : Exception("サインインがキャンセルされました")

class MsalAuthManagerImpl(
    private val clientId: String,
    private val authority: String,
) {
    private var msalInstance: dynamic = null
    private var isInitialized = false

    companion object {
        suspend fun create(config: MsalConfig): MsalAuthManagerImpl = MsalAuthManagerImpl(config.clientId, config.authority).also { it.init() }
    }

    private fun init() {
        if (!window.hasOwnProperty("msal")) {
            val script = document.createElement("script")
            script.src = "https://alcdn.msauth.net/browser/2.38.3/js/msal-browser.min.js"
            script.async = true
            script.onload = { initMsal() }
            document.head.appendChild(script)
        } else {
            initMsal()
        }
    }

    private fun initMsal() {
        msalInstance = window.msal.PublicClientApplication({
            auth = {
                clientId = clientId,
                authority = authority,
            },
            cache = {
                cacheLocation = "localStorage",
            },
        })
        isInitialized = true
    }

    suspend fun acquireToken(): String = awaitCompletion { completion ->
        if (!isInitialized) {
            // Wait for initialization
            val checkInit = window.setInterval({
                if (isInitialized) {
                    window.clearInterval(checkInit)
                    doAcquireToken(completion)
                }
            }, 100)
        } else {
            doAcquireToken(completion)
        }
    }

    private fun doAcquireToken(completion: CompletableDeferred<String>) {
        val loginRequest = {
            scopes = arrayOf("Calendars.Read"),
            prompt = "select_account",
        }
        msalInstance.loginPopup(loginRequest)
            .then { response: dynamic ->
                if (response.accessToken != null) {
                    completion.resume(response.accessToken)
                } else {
                    completion.resumeWithException(Exception("アクセストークンの取得に失敗しました"))
                }
            }
            .catch { error: dynamic ->
                if (error.name == "AuthError" && error.message?.contains("user cancelled") == true) {
                    completion.resumeWithException(SignInCancelledException())
                } else {
                    completion.resumeWithException(Exception(error.message ?: "認証に失敗しました"))
                }
            }
    }

    suspend fun acquireTokenSilent(account: String): String = awaitCompletion { completion ->
        val silentRequest = {
            scopes = arrayOf("Calendars.Read"),
            account = msalInstance.getAccountByUsername(account),
        }
        msalInstance.acquireTokenSilent(silentRequest)
            .then { response: dynamic ->
                completion.resume(response.accessToken)
            }
            .catch { error: dynamic ->
                completion.resumeWithException(Exception(error.message ?: "サイレント認証に失敗しました"))
            }
    }

    suspend fun getAccounts(): List<String> = awaitCompletion { completion ->
        val accounts = msalInstance.getAllAccounts()
        completion.resume(accounts.map { it.username })
    }

    suspend fun removeAccount(account: String) = awaitCompletion { completion ->
        val msalAccount = msalInstance.getAccountByUsername(account)
        msalInstance.removeAccount(msalAccount).then {
            completion.resume(Unit)
        }.catch { error: dynamic ->
            completion.resumeWithException(Exception(error.message ?: "アカウント削除に失敗しました"))
        }
    }

    private suspend fun awaitCompletion(block: (CompletableDeferred<Any>) -> Unit): Any {
        val completion = CompletableDeferred<Any>()
        block(completion)
        return completion.await()
    }

    data class MsalConfig(
        val clientId: String,
        val authority: String,
    )
}