@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kigawa.kalender

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.auth.KeycloakAuthControllerWeb
import net.kigawa.kalender.data.auth.jsRedirectUri
import net.kigawa.kalender.data.db.WebCalendarStore
import net.kigawa.kalender.di.AppContainer
import net.kigawa.kalender.di.KeyValueStoreWeb

// kigawa-net共有KeycloakのrealmURLと、そこに登録したkalenderクライアントのID。
// 事前にkigawa-net共有Keycloakのkigawa-net realmに、このWebアプリのオリジンをリダイレクトURIとして
// 登録したpublicクライアント"kalender"を用意すること。
private const val KEYCLOAK_REALM_URL = "https://user.kigawa.net/realms/kigawa-net"
private const val KEYCLOAK_CLIENT_ID = "kalender"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val httpClient = HttpClient(Js)
    val container = AppContainer(
        httpClient = httpClient,
        localStore = WebCalendarStore(),
        settings = KeyValueStoreWeb(),
        authController = KeycloakAuthControllerWeb(KEYCLOAK_REALM_URL, KEYCLOAK_CLIENT_ID, httpClient),
        apiClient = KalenderApiClient(httpClient),
        accountLinkRedirectUri = jsRedirectUri().toString(),
        appScope = appScope,
    )
    ComposeViewport(document.body!!) {
        KalenderRoot(container = container)
    }
}
