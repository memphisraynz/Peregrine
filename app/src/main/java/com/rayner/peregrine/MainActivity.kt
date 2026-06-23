package com.rayner.peregrine

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
