package net.kigawa.kalender.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import net.kigawa.kalender.data.auth.AuthManager
import net.kigawa.kalender.ui.screen.EventDetailScreen
import net.kigawa.kalender.ui.screen.EventEditScreen
import net.kigawa.kalender.ui.screen.LoginScreen
import net.kigawa.kalender.ui.screen.ProfileScreen
import net.kigawa.kalender.ui.screen.WeeklyCalendarScreen
import net.kigawa.kalender.viewmodel.MsAuthState

@Composable
fun AppNavHost(
    googleAuthState: AuthManager.State,
    msAuthState: MsAuthState,
    onGoogleSignIn: () -> Unit,
    onMsSignIn: () -> Unit,
    startDestination: String = AppDestinations.HOME.route,
) {
    val navController = rememberNavController()
    androidx.navigation.compose.NavHost(navController, startDestination) {
        composable(AppDestinations.HOME.route) {
            WeeklyCalendarScreen(
                onEventClick = { eventId ->
                    navController.navigate(AppDestinations.EVENT_DETAIL.route.replace("{eventId}", eventId.toString()))
                },
                onNewEvent = { navController.navigate(AppDestinations.EVENT_NEW.route) },
            )
        }
        composable(
            route = AppDestinations.EVENT_DETAIL.route,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.getLong("eventId")
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { eventId ->
                    navController.navigate(AppDestinations.EVENT_EDIT.route.replace("{eventId}", eventId.toString()))
                },
            )
        }
        composable(
            route = AppDestinations.EVENT_EDIT.route,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.getLong("eventId")
            EventEditScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestinations.EVENT_NEW.route) {
            EventEditScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestinations.PROFILE.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestinations.LOGIN.route) {
            LoginScreen(
                googleState = googleAuthState,
                msState = msAuthState,
                onGoogleSignIn = onGoogleSignIn,
                onMsSignIn = onMsSignIn,
            )
        }
    }
}