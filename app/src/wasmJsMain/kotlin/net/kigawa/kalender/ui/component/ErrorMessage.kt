package net.kigawa.kalender.ui.component

import org.jetbrains.compose.web.dom.window

actual fun copyToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}