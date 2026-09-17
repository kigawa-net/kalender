package net.kigawa.kalender.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

actual fun openUrlInBrowser(url: String, platformHandle: Any?) {
    val context = platformHandle as? Context ?: return
    val intent = CustomTabsIntent.Builder().build()
    if (context !is android.app.Activity) {
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    intent.launchUrl(context, Uri.parse(url))
}
