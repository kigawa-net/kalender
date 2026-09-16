package net.kigawa.kalender.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.auth.GoogleAuthController
import net.kigawa.kalender.data.auth.MicrosoftAuthController

interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** プラットフォームごとの依存関係をまとめて保持する簡易DIコンテナ */
class AppContainer(
    val httpClient: HttpClient,
    val localStore: LocalCalendarStore,
    val settings: KeyValueStore,
    val googleAuthController: GoogleAuthController,
    val microsoftAuthController: MicrosoftAuthController,
    val appScope: CoroutineScope,
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap the app in CompositionLocalProvider(LocalAppContainer provides container).")
}
