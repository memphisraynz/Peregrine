package com.rayner.peregrine

import android.app.AlertDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
        checkBackgroundDataRestriction()

        (repository as? FrigateRepositoryImpl)?.let { repo ->
            lifecycleScope.launch {
                repo.restoreServerUrl()
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

    // Notification images are fetched from a background service (PeregrineMessagingService)
    // while the phone may be asleep. Data Saver's per-app "unrestricted data usage" exemption
    // is separate from battery optimization and isn't granted by default - without it, those
    // background fetches fail. Re-checked on every launch, since it stops firing on its own
    // once the user grants it (no dismissal state to track).
    private fun checkBackgroundDataRestriction() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        if (connectivityManager.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.background_data_dialog_title)
            .setMessage(R.string.background_data_dialog_message)
            .setPositiveButton(R.string.background_data_dialog_positive) { _, _ -> openBackgroundDataSettings() }
            .setNegativeButton(R.string.background_data_dialog_negative, null)
            .show()
    }

    private fun openBackgroundDataSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }.apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
