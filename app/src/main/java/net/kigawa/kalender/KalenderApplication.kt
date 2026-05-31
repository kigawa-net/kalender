package net.kigawa.kalender

import android.app.Application
import net.kigawa.kalender.data.auth.GoogleAuthManager

class KalenderApplication : Application() {
    val googleAuthManager = GoogleAuthManager()
    val msAccessToken = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val msAccountEmail = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
