package net.kigawa.kalender.util

/**
 * "#rrggbb" 形式の16進カラーコードをARGB Intへ変換する。android.graphics.Colorは
 * Android専用のためマルチプラットフォームで使えるパーサーをここに用意する。
 */
fun parseHexColorOrNull(hex: String): Int? {
    if (hex.isEmpty()) return null
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or rgb
}
