package net.kigawa.kalender.util

internal actual fun platformLogError(tag: String, message: String) {
    // wasmJsでは console API へ直接アクセスできないため、標準出力に出力する
    println("[$tag] ERROR: $message")
}

internal actual fun platformLogWarning(tag: String, message: String) {
    println("[$tag] WARN: $message")
}
