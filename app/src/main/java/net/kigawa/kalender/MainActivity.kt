
package net.kigawa.kalender

import android.app.Activity
import android.content.ContextWrapper
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.kigawa.kalender.ui.screen.EventDetailScreen
import net.kigawa.kalender.ui.screen.LoginScreen
import net.kigawa.kalender.ui.screen.ProfileScreen
import net.kigawa.kalender.ui.screen.WeeklyCalendarScreen
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalenderTheme {
                val context = LocalContext.current
                val authViewModel: AuthViewModel = viewModel()
                val googleState by authViewModel.googleAuthState.collectAsStateWithLifecycle()
                val msState by authViewModel.msAuthState.collectAsStateWithLifecycle()
                val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()

                val consentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    authViewModel.handleGoogleConsentResult(context, result.data)
                }

                LaunchedEffect(Unit) {
                    (context.applicationContext as KalenderApplication)
                        .googleAuthManager.pendingConsent.collect { intentSender ->
                            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                }

                when (isLoggedIn) {
                    null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    true -> KalenderApp()
                    false -> LoginScreen(
                        googleState = googleState,
                        msState = msState,
                        onGoogleSignIn = { authViewModel.signInWithGoogle(context) },
                        onMsSignIn = { context.findActivity()?.let { authViewModel.signInWithMicrosoft(it) } },
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String,
) {
    HOME("Home", Icons.Default.Home, "weekly_calendar"),
    FAVORITES("Favorites", Icons.Default.Favorite, "favorites"),
    PROFILE("Profile", Icons.Default.AccountBox, "profile"),
}

@PreviewScreenSizes
@Composable
fun KalenderApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentDestination = AppDestinations.entries.find { dest ->
        currentRoute?.startsWith(dest.route) == true
    } ?: AppDestinations.HOME

    NavigationSuiteScaffold(
        modifier = Modifier.statusBarsPadding(),
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.HOME.route) {
            composable(AppDestinations.HOME.route) {
                WeeklyCalendarScreen(
                    onEventClick = { eventId ->
                        navController.navigate("event_detail/$eventId")
                    }
                )
            }
            composable(
                route = "event_detail/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
            ) {
                EventDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestinations.FAVORITES.route) {
                Text("Favorites")
            }
            composable(AppDestinations.PROFILE.route) {
                ProfileScreen()
            }
        }
    }
}
