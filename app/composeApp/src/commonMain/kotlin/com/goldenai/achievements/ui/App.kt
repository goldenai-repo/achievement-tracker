package com.goldenai.achievements.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.goldenai.achievements.features.achievements.presentation.CheckInFormScreen
import com.goldenai.achievements.features.achievements.presentation.HomeScreen
import com.goldenai.achievements.features.achievements.presentation.ListScreen
import com.goldenai.achievements.features.auth.presentation.RegisterScreen
import com.goldenai.achievements.features.auth.presentation.SignInScreen
import com.goldenai.achievements.features.map.ExploreScreen
import com.goldenai.achievements.features.profile.presentation.ProfileScreen
import com.goldenai.achievements.ui.theme.AppTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ListRoute(val typeKey: String? = null)

@Serializable
object ExploreRoute

@Serializable
object AccountRoute

@Serializable
data class FormRoute(val id: String? = null)

@Serializable
object SignInRoute

@Serializable
object RegisterRoute

@Composable
fun App() {
    AppTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination

        val onHome = destination?.hasRoute<HomeRoute>() == true
        val onList = destination?.hasRoute<ListRoute>() == true
        val onExplore = destination?.hasRoute<ExploreRoute>() == true
        val onAccount = destination?.hasRoute<AccountRoute>() == true
        val onTab = onHome || onList || onExplore || onAccount

        fun navigateToTab(route: Any) {
            navController.navigate(route) {
                // HomeRoute is the root of the tab graph. Explicitly popping
                // to it keeps tab navigation reliable with typed destinations.
                popUpTo<HomeRoute> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // A Home card carries an explicit Log filter. Do not restore the
        // existing ListRoute here: restoring it also restores its old
        // ViewModel state (usually All) and silently discards the new filter.
        fun navigateToList(typeKey: String? = null) {
            navController.navigate(ListRoute(typeKey)) {
                popUpTo<HomeRoute> { saveState = true }
                launchSingleTop = false
                restoreState = false
            }
        }

        Scaffold(
            bottomBar = {
                if (onTab) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = onHome,
                            onClick = { navigateToTab(HomeRoute) },
                            icon = { Text("🏠") },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = onList,
                            onClick = { navigateToTab(ListRoute()) },
                            icon = { Text("📜") },
                            label = { Text("Log") },
                        )
                        NavigationBarItem(
                            selected = onExplore,
                            onClick = { navigateToTab(ExploreRoute) },
                            icon = { Text("🗺️") },
                            label = { Text("Explore") },
                        )
                        NavigationBarItem(
                            selected = onAccount,
                            onClick = { navigateToTab(AccountRoute) },
                            icon = { Text("👤") },
                            label = { Text("Profile") },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (onHome || onList) {
                    FloatingActionButton(onClick = { navController.navigate(FormRoute()) }) {
                        Text("＋", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(padding),
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onBackupClick = { navigateToTab(AccountRoute) },
                        onOpenLog = { navigateToList() },
                        onCategoryClick = { typeKey ->
                            val filter = if (typeKey.startsWith("geography.")) {
                                "geography"
                            } else {
                                typeKey.substringBefore('.')
                            }
                            navigateToList(filter)
                        },
                    )
                }
                composable<ListRoute> { entry ->
                    val route = entry.toRoute<ListRoute>()
                    ListScreen(
                        initialType = route.typeKey,
                    )
                }
                composable<ExploreRoute> {
                    ExploreScreen()
                }
                composable<AccountRoute> {
                    ProfileScreen(
                        onSignIn = { navController.navigate(SignInRoute) },
                        onRegister = { navController.navigate(RegisterRoute) },
                        onViewLog = { navigateToList() },
                    )
                }
                composable<FormRoute> {
                    CheckInFormScreen(onDone = { navController.popBackStack() })
                }
                composable<SignInRoute> {
                    SignInScreen(
                        onDone = { navController.popBackStack() },
                        onSwitchToRegister = {
                            navController.navigate(RegisterRoute) {
                                popUpTo<SignInRoute> { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<RegisterRoute> {
                    RegisterScreen(
                        onDone = { navController.popBackStack() },
                        onSwitchToSignIn = {
                            navController.navigate(SignInRoute) {
                                popUpTo<RegisterRoute> { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
