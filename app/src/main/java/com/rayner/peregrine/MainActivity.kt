package com.rayner.peregrine

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rayner.peregrine.data.repository.FrigateRepositoryImpl
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.rayner.peregrine.ui.screens.MainAppScaffold
import com.rayner.peregrine.ui.theme.PeregrineTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: FrigateRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        (repository as? FrigateRepositoryImpl)?.let { repo ->
            lifecycleScope.launch {
                repo.restorePersistedAuthCookie()
            }
        }

        setContent {
            PeregrineTheme {
                MainAppScaffold(repository)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Local Network Permission (Android 16+ / API 36+)
        if (Build.VERSION.SDK_INT >= 36) {
            permissions.add("android.permission.ACCESS_LOCAL_NETWORK")
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
