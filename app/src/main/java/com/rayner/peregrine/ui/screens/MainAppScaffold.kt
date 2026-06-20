package com.rayner.peregrine.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rayner.peregrine.ui.navigation.PeregrineNavGraph
import com.rayner.peregrine.ui.navigation.Screen
import com.rayner.peregrine.ui.navigation.bottomNavItems
import com.rayner.peregrine.domain.repository.FrigateRepository
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun MainAppScaffold(repository: FrigateRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val startDestination by produceState(initialValue = Screen.Login.route, repository) {
        val config = repository.getServerConfig().firstOrNull()
        value = if (config?.isLoggedIn == true) Screen.Live.route else Screen.Login.route
    }

    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = null) } },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PeregrineNavGraph(
            navController = navController,
            repository = repository,
            modifier = Modifier.padding(innerPadding),
            startDestination = startDestination
        )
    }
}
