package com.rayner.peregrine.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

import com.rayner.peregrine.ui.screens.explore.ExploreScreen
import com.rayner.peregrine.ui.screens.live.LiveViewScreen
import com.rayner.peregrine.ui.screens.login.LoginScreen
import com.rayner.peregrine.ui.screens.review.ReviewScreen
import com.rayner.peregrine.ui.screens.settings.SettingsScreen

@Composable
fun PeregrineNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Live.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Live.route) {
            LiveViewScreen()
        }
        composable(Screen.Review.route) {
            ReviewScreen()
        }
        composable(Screen.Explore.route) {
            ExploreScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$name Screen (Coming Soon)")
    }
}
