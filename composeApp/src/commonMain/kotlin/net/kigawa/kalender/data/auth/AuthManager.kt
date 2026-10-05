package net.kigawa.kalender.data.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthManager {
    sealed class State {
        object SignedOut : State()
        object Loading : State()
        data class SignedIn(
            val email: String,
            val displayName: String?,
            val accessToken: String,
        ) : State()
        data class Error(val message: String) : State()
    }

    val authState: StateFlow<State>

    suspend fun signIn()
    suspend fun signOut()
    suspend fun trySignInSilently()
}