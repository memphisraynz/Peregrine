package com.rayner.peregrine.ui.screens.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.rayner.peregrine.ui.components.LiveVideoPlayer
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPlayerScreen(
    eventId: String,
    repository: FrigateRepository,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    var videoUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) {
        val config = repository.getServerConfig().firstOrNull()
        config?.let {
            videoUrl = "${it.serverUrl}/api/events/$eventId/clip.mp4"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Clip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            videoUrl?.let {
                LiveVideoPlayer(
                    url = it,
                    isMuted = false,
                    okHttpClient = viewModel.okHttpClient,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: CircularProgressIndicator()
        }
    }
}
