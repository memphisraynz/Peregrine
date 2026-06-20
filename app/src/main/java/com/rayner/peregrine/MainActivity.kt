package com.rayner.peregrine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rayner.peregrine.ui.screens.MainAppScaffold
import com.rayner.peregrine.ui.theme.PeregrineTheme
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: FrigateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeregrineTheme {
                MainAppScaffold(repository)
            }
        }
    }
}
