package com.rayner.peregrine.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Screen("login", "Login")
    object Live : Screen("live", "Cameras", Icons.Outlined.Videocam)
    object Review : Screen("review", "Review", Icons.Outlined.History)
    object Explore : Screen("explore", "Explore", Icons.Outlined.CompassCalibration)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
    object Logs : Screen("logs", "Logs")
    object ReviewPlayer : Screen("review_player/{eventId}", "Clip") {
        fun createRoute(eventId: String) = "review_player/$eventId"
    }
    object CameraDetail : Screen("camera/{cameraName}?autoplay={autoplay}", "Camera") {
        fun createRoute(cameraName: String, autoplay: Boolean = false) = "camera/$cameraName?autoplay=$autoplay"
    }
}

val bottomNavItems = listOf(
    Screen.Live,
    Screen.Review,
    Screen.Explore,
    Screen.Settings
)
