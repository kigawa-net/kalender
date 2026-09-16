package net.kigawa.kalender.di

import kotlinx.browser.localStorage

class KeyValueStoreWeb : KeyValueStore {
    override fun getBoolean(key: String, default: Boolean): Boolean =
        localStorage.getItem(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        localStorage.setItem(key, value.toString())
    }
}
