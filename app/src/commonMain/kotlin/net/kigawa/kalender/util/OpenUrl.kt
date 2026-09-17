package net.kigawa.kalender.util

/**
 * アカウント連携URLなど、外部ブラウザ/Custom Tabsで開く必要があるURLを開く。
 * platformHandle: Android では Context、Webでは無視される(同一タブでの遷移になる)
 */
expect fun openUrlInBrowser(url: String, platformHandle: Any? = null)
