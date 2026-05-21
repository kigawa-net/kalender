package net.kigawa.kalender

import android.app.Application
import net.kigawa.kalender.data.auth.GoogleAuthManager

class KalenderApplication : Application() {
    val googleAuthManager = GoogleAuthManager()
    
    // Microsoft 認証の状態を保持（WeeklyCalendarViewModel が参照するため）
    // 本来は Repository などで管理すべきだが、現状の構造に合わせる
    val msAccessToken = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
