package com.rayner.peregrine.ui.screens.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.rayner.peregrine.ui.components.LiveVideoPlayer
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.floor
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPlayerScreen(
    eventId: String,
    repository: FrigateRepository,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var thumbUrl by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(true) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId, uiState.reviewItems) {
        val item = uiState.reviewItems.find { it["id"] == eventId }
        val config = repository.getServerConfig().firstOrNull()
        
        if (item != null && config != null) {
            val base = config.serverUrl.removeSuffix("/")
            val camera = item["camera"] as? String ?: ""
            val start = floor(item["start_time"] as? Double ?: 0.0).toLong()
            val end = floor(item["end_time"] as? Double ?: (start + 3600.0)).toLong()
            
            videoUrl = "$base/vod/$camera/start/$start/end/$end/master.m3u8"
            thumbUrl = "$base/api/review/thumbnail/$eventId"
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isMuted = !isMuted },
                containerColor = if (!isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Toggle Audio"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            videoUrl?.let {
                LiveVideoPlayer(
                    url = it,
                    isMuted = isMuted,
                    placeholderUrl = thumbUrl,
                    okHttpClient = viewModel.okHttpClient,
                    imageLoader = viewModel.imageLoader,
                    modifier = Modifier.weight(1f) // Push to top by using weight
                )
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
