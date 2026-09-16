package net.kigawa.kalender

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kigawa.kalender.data.auth.GoogleAuthControllerWeb
import net.kigawa.kalender.data.auth.MicrosoftAuthControllerWeb
import net.kigawa.kalender.data.db.WebCalendarStore
import net.kigawa.kalender.di.AppContainer
import net.kigawa.kalender.di.KeyValueStoreWeb

// Google Cloud Console の OAuth 2.0 ウェブクライアントID (Androidアプリと同じ値を使い回している)。
// 事前に Google Cloud Console でこのアプリのオリジンを「承認済みのJavaScript生成元」に追加すること。
private const val GOOGLE_WEB_CLIENT_ID = "441586545378-e7leqo6rc4clr6as5hla5jg408icqpdb.apps.googleusercontent.com"

// Microsoft Entra のアプリケーション(クライアント)ID (Androidアプリと同じ値を使い回している)。
// 事前に Azure Portal でこのアプリ登録に「シングルページアプリケーション」プラットフォームを追加し、
// このWebアプリのオリジンをリダイレクトURIとして登録すること。
private const val MICROSOFT_CLIENT_ID = "3b5392c1-34fe-447b-a09f-ae8144d7564a"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val httpClient = HttpClient(Js)
    val container = AppContainer(
        httpClient = httpClient,
        localStore = WebCalendarStore(),
        settings = KeyValueStoreWeb(),
        googleAuthController = GoogleAuthControllerWeb(GOOGLE_WEB_CLIENT_ID, httpClient),
        microsoftAuthController = MicrosoftAuthControllerWeb(MICROSOFT_CLIENT_ID, httpClient),
        appScope = appScope,
    )
    ComposeViewport(document.body!!) {
        KalenderRoot(container = container)
    }
}
