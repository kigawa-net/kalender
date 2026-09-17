package net.kigawa.kalender.util

import net.kigawa.kalender.data.auth.jsNavigateTo

actual fun openUrlInBrowser(url: String, platformHandle: Any?) {
    jsNavigateTo(url)
}
