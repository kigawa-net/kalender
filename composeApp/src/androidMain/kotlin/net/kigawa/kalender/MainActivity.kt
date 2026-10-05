package net.kigawa.kalender

import android.os.Bundle
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import net.kigawa.kalender.ui.navigation.AppDestinations
import net.kigawa.kalender.ui.navigation.AppNavHost
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.AuthViewModel
import net.kigawa.kalender.viewmodel.MsAuthState
import org.koin.androidx.compose.getSharedPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kalender_auth", MODE_PRIVATE)
        setContent {
            KalenderTheme {
                val viewModel: AuthViewModel = viewModel(
                    extras = SavedStateHandle().apply { 
                        put("prefs", prefs) 
                    }
                )
                val googleAuthState by viewModel.googleAuthState.collectAsStateWithLifecycle()
                val msAuthState by viewModel.msAuthState.collectAsStateWithLifecycle()

                val isLoggedIn = googleAuthState is net.kigawa.kalender.data.auth.AuthManager.State.SignedIn ||
                                 msAuthState is MsAuthState.SignedIn

                if (!isLoggedIn) {
                    Surface {
                        AppNavHost(
                            googleAuthState = googleAuthState,
                            msAuthState = msAuthState,
                            onGoogleSignIn = { viewModel.signInWithGoogle(this) },
                            onMsSignIn = { viewModel.signInWithMicrosoft(this) },
                            startDestination = AppDestinations.LOGIN.route,
                        )
                    }
                } else {
                    AppNavHost(
                        googleAuthState = googleAuthState,
                        msAuthState = msAuthState,
                        onGoogleSignIn = { viewModel.signInWithGoogle(this) },
                        onMsSignIn = { viewModel.signInWithMicrosoft(this) },
                        startDestination = AppDestinations.HOME.route,
                    )
                }
            }
        }
    }
}