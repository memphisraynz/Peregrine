package com.rayner.peregrine.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rayner.peregrine.ui.screens.explore.ExploreScreen
import com.rayner.peregrine.ui.screens.live.LiveViewScreen
import com.rayner.peregrine.ui.screens.login.LoginScreen
import com.rayner.peregrine.ui.screens.review.ReviewPlayerScreen
import com.rayner.peregrine.ui.screens.review.ReviewScreen
import com.rayner.peregrine.ui.screens.settings.LogsScreen
import com.rayner.peregrine.ui.screens.settings.SettingsScreen
import com.rayner.peregrine.domain.repository.FrigateRepository
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.navArgument

@Composable
fun PeregrineNavGraph(
    navController: NavHostController,
    repository: FrigateRepository, // Passed from Hilt at root
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Live.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Live.route) {
            LiveViewScreen(
                onReviewClick = { eventId ->
                    navController.navigate(Screen.ReviewPlayer.createRoute(eventId))
                },
                onCameraClick = { cameraName ->
                    navController.navigate(Screen.CameraDetail.createRoute(cameraName, autoplay = true))
                }
            )
        }
        composable(
            route = Screen.CameraDetail.route,
            arguments = listOf(
                navArgument("cameraName") { type = NavType.StringType },
                navArgument("autoplay") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val cameraName = backStackEntry.arguments?.getString("cameraName") ?: ""
            val autoplay = backStackEntry.arguments?.getBoolean("autoplay") ?: false
            LiveViewScreen(
                initialCameraName = cameraName,
                autoStartLive = autoplay,
                onReviewClick = { eventId ->
                    navController.navigate(Screen.ReviewPlayer.createRoute(eventId))
                },
                onCameraClick = { }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(onItemClick = { eventId ->
                navController.navigate(Screen.ReviewPlayer.createRoute(eventId))
            })
        }
        composable(
            route = Screen.ReviewPlayer.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            ReviewPlayerScreen(
                eventId = eventId,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onViewLogs = {
                    navController.navigate(Screen.Logs.route)
                }
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
    }
}
