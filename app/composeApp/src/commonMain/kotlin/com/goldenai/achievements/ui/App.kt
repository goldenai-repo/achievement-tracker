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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.goldenai.achievements.features.achievements.presentation.FormScreen
import com.goldenai.achievements.features.achievements.presentation.HomeScreen
import com.goldenai.achievements.features.achievements.presentation.ListScreen
import com.goldenai.achievements.features.auth.presentation.AccountScreen
import com.goldenai.achievements.features.auth.presentation.RegisterScreen
import com.goldenai.achievements.features.auth.presentation.SignInScreen
import com.goldenai.achievements.ui.theme.AppTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ListRoute(val category: String? = null)

@Serializable
object AccountRoute

@Serializable
data class FormRoute(val id: String? = null, val category: String? = null)

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
        val onAccount = destination?.hasRoute<AccountRoute>() == true
        val onTab = onHome || onList || onAccount

        fun navigateToTab(route: Any) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
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
                            selected = onAccount,
                            onClick = { navigateToTab(AccountRoute) },
                            icon = { Text("👤") },
                            label = { Text("Account") },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (onHome || onList) {
                    val listCategory =
                        if (onList) backStackEntry?.toRoute<ListRoute>()?.category else null
                    FloatingActionButton(
                        onClick = { navController.navigate(FormRoute(category = listCategory)) },
                    ) {
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
                        onCategoryClick = { category -> navController.navigate(ListRoute(category)) },
                        onItemClick = { id -> navController.navigate(FormRoute(id)) },
                        onBackupClick = { navigateToTab(AccountRoute) },
                    )
                }
                composable<ListRoute> { entry ->
                    val route = entry.toRoute<ListRoute>()
                    ListScreen(
                        category = route.category,
                        onItemClick = { id -> navController.navigate(FormRoute(id = id)) },
                        onAddClick = { navController.navigate(FormRoute(category = route.category)) },
                    )
                }
                composable<AccountRoute> {
                    AccountScreen(
                        onSignIn = { navController.navigate(SignInRoute) },
                        onRegister = { navController.navigate(RegisterRoute) },
                    )
                }
                composable<FormRoute> { entry ->
                    val route = entry.toRoute<FormRoute>()
                    FormScreen(
                        editId = route.id,
                        category = route.category,
                        onBack = { navController.popBackStack() },
                        onSaved = { listCategory ->
                            val restoredList = navController.popBackStack<ListRoute>(inclusive = false)
                            if (!restoredList) {
                                navController.navigate(ListRoute(listCategory)) {
                                    popUpTo<HomeRoute> { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
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
