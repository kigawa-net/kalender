package net.kigawa.kalender.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import net.kigawa.kalender.data.KalenderApiClient
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.data.auth.AuthController

interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** プラットフォームごとの依存関係をまとめて保持する簡易DIコンテナ */
class AppContainer(
    val httpClient: HttpClient,
    val localStore: LocalCalendarStore,
    val settings: KeyValueStore,
    val authController: AuthController,
    val apiClient: KalenderApiClient,
    /** Keycloakアカウントリンク完了後に戻ってくるリダイレクトURI(ログイン用と同じもので構わない) */
    val accountLinkRedirectUri: String,
    val appScope: CoroutineScope,
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap the app in CompositionLocalProvider(LocalAppContainer provides container).")
}
