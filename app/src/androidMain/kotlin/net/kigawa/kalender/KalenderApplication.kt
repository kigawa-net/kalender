package net.kigawa.kalender

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.auth.KEYCLOAK_REDIRECT_URI
import net.kigawa.kalender.data.auth.KeycloakAuthControllerAndroid
import net.kigawa.kalender.data.db.RoomCalendarStore
import net.kigawa.kalender.di.AppContainer
import net.kigawa.kalender.di.KeyValueStoreAndroid

class KalenderApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val httpClient = HttpClient(OkHttp)

    val authController = KeycloakAuthControllerAndroid(
        applicationContext = this,
        appScope = appScope,
        realmUrl = getString(R.string.keycloak_realm_url),
        clientId = getString(R.string.keycloak_client_id),
        httpClient = httpClient,
    )

    val container = AppContainer(
        httpClient = httpClient,
        localStore = RoomCalendarStore.fromContext(this),
        settings = KeyValueStoreAndroid(this),
        authController = authController,
        apiClient = KalenderApiClient(httpClient),
        accountLinkRedirectUri = KEYCLOAK_REDIRECT_URI,
        appScope = appScope,
    )
}
