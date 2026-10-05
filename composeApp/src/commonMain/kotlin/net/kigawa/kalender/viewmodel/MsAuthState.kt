package net.kigawa.kalender.viewmodel

sealed class MsAuthState {
    object Initializing : MsAuthState()
    object SignedOut : MsAuthState()
    object SigningIn : MsAuthState()
    data class SignedIn(val email: String, val accessToken: String) : MsAuthState()
    data class Error(val message: String) : MsAuthState()
}