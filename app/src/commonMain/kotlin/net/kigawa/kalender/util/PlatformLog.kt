package net.kigawa.kalender.util

/**
 * プラットフォーム共通のエラーログ出力。
 * Androidでは Log.e、Web(wasmJs)では console.error に出力する。
 */
internal expect fun platformLogError(tag: String, message: String)

/** プラットフォーム共通の警告ログ出力。 */
internal expect fun platformLogWarning(tag: String, message: String)

/** 例外情報を含めたエラーログ出力の共通ヘルパー */
internal fun logErrorWithException(tag: String, message: String, e: Throwable? = null) {
    val msg = if (e != null) "$message: ${e.message}\n${e.stackTraceToString()}" else message
    platformLogError(tag, msg)
}
