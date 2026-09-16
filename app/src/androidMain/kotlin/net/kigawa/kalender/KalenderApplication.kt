package net.kigawa.kalender

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kigawa.kalender.data.auth.GoogleAuthControllerAndroid
import net.kigawa.kalender.data.auth.MicrosoftAuthControllerAndroid
import net.kigawa.kalender.data.db.RoomCalendarStore
import net.kigawa.kalender.di.AppContainer
import net.kigawa.kalender.di.KeyValueStoreAndroid

class KalenderApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val googleAuthController = GoogleAuthControllerAndroid(
        applicationContext = this,
        webClientId = getString(R.string.google_web_client_id),
    )

    private val microsoftAuthController = MicrosoftAuthControllerAndroid(this, appScope)

    val container = AppContainer(
        httpClient = HttpClient(OkHttp),
        localStore = RoomCalendarStore(this),
        settings = KeyValueStoreAndroid(this),
        googleAuthController = googleAuthController,
        microsoftAuthController = microsoftAuthController,
        appScope = appScope,
    )
}
