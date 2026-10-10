package net.kigawa.kalender.util

import android.util.Log

internal actual fun platformLogError(tag: String, message: String) {
    Log.e(tag, message)
}

internal actual fun platformLogWarning(tag: String, message: String) {
    Log.w(tag, message)
}
