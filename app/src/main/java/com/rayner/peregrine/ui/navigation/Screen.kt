package com.rayner.peregrine.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Screen("login", "Login")
    object Live : Screen("live", "Live", Icons.Default.LiveTv)
    object Review : Screen("review", "Review", Icons.Default.RateReview)
    object Explore : Screen("explore", "Explore", Icons.Default.Explore)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Live,
    Screen.Review,
    Screen.Explore,
    Screen.Settings
)
