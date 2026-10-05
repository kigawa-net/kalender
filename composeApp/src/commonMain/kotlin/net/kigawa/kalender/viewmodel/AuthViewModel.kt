package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import net.kigawa.kalender.data.auth.AuthManager

expect class AuthViewModel : ViewModel {
    val googleAuthState: kotlinx.coroutines.flow.StateFlow<AuthManager.State>
    val msAuthState: kotlinx.coroutines.flow.StateFlow<MsAuthState>
    val isLoggedIn: kotlinx.coroutines.flow.StateFlow<Boolean?>
    fun signInWithGoogle()
    fun handleGoogleConsentResult(data: Any?)
    fun signInWithMicrosoft()
    fun signOut()
}