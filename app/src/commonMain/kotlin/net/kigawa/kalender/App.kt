package net.kigawa.kalender

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.kigawa.kalender.di.AppContainer
import net.kigawa.kalender.di.LocalAppContainer
import net.kigawa.kalender.ui.screen.EventDetailScreen
import net.kigawa.kalender.ui.screen.EventEditScreen
import net.kigawa.kalender.ui.screen.LoginScreen
import net.kigawa.kalender.ui.screen.ProfileScreen
import net.kigawa.kalender.ui.screen.WeeklyCalendarScreen
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.AuthViewModel
import net.kigawa.kalender.viewmodel.EventDetailViewModel
import net.kigawa.kalender.viewmodel.EventEditViewModel
import net.kigawa.kalender.viewmodel.ProfileViewModel
import net.kigawa.kalender.viewmodel.WeeklyCalendarViewModel

@Composable
fun KalenderRoot(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        KalenderTheme {
            val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    AuthViewModel(container.authController, container.settings)
                }
            })
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()

            when (isLoggedIn) {
                null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                true -> KalenderApp(container)
                false -> LoginScreen(
                    authState = authState,
                    onSignIn = { authViewModel.signIn() },
                )
            }
        }
    }
}

private enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String,
) {
    HOME("カレンダー", Icons.Default.Home, "weekly_calendar"),
    PROFILE("設定", Icons.Default.Settings, "profile"),
}

@Composable
private fun KalenderApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentDestination = AppDestinations.entries.find { dest ->
        currentRoute?.startsWith(dest.route) == true
    } ?: AppDestinations.HOME

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { dest ->
                    NavigationBarItem(
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestinations.HOME.route) {
                val vm: WeeklyCalendarViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        WeeklyCalendarViewModel(
                            container.authController,
                            container.apiClient,
                            container.localStore,
                            container.httpClient,
                        )
                    }
                })
                WeeklyCalendarScreen(
                    onEventClick = { eventId -> navController.navigate("event_detail/$eventId") },
                    onNewEvent = { navController.navigate("event_new") },
                    viewModel = vm,
                )
            }
            composable(
                route = "event_detail/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.read { getLong("eventId") } ?: 0L
                val vm: EventDetailViewModel = viewModel(factory = viewModelFactory {
                    initializer { EventDetailViewModel(container.localStore, eventId) }
                })
                EventDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("event_edit/$id") },
                    viewModel = vm,
                )
            }
            composable(
                route = "event_edit/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.read { getLong("eventId") } ?: 0L
                val vm: EventEditViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        EventEditViewModel(
                            container.authController,
                            container.apiClient,
                            container.localStore,
                            container.httpClient,
                            eventId,
                        )
                    }
                })
                EventEditScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }
            composable(route = "event_new") {
                val vm: EventEditViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        EventEditViewModel(
                            container.authController,
                            container.apiClient,
                            container.localStore,
                            container.httpClient,
                            null,
                        )
                    }
                })
                EventEditScreen(onBack = { navController.popBackStack() }, viewModel = vm)
            }
            composable(AppDestinations.PROFILE.route) {
                val vm: ProfileViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        ProfileViewModel(
                            container.authController,
                            container.apiClient,
                            container.localStore,
                            container.accountLinkRedirectUri,
                        )
                    }
                })
                ProfileScreen(viewModel = vm)
            }
        }
    }
}
