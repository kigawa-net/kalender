package net.kigawa.kalender.di

import android.content.Context

class KeyValueStoreAndroid(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("kalender_auth", Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
