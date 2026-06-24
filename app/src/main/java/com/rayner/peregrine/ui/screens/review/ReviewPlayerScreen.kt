package com.rayner.peregrine.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.rayner.peregrine.ui.components.LiveVideoPlayer
import com.rayner.peregrine.ui.theme.DetectionColors
import com.rayner.peregrine.util.formatCameraName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.floor

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
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var player by remember { mutableStateOf<Player?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId, uiState.reviewItems) {
        val item = uiState.reviewItems.find { it.id == eventId }
        val config = repository.getServerConfig().firstOrNull()
        
        if (item != null && config != null) {
            val base = config.serverUrl.removeSuffix("/")
            val camera = item.camera
            val buffer = config.vodBuffer
            val start = (floor(item.startTime).toLong() - buffer).coerceAtLeast(0)
            val end = floor(item.endTime ?: (item.startTime + 3600.0)).toLong() + buffer
            
            videoUrl = "$base/vod/$camera/start/$start/end/$end/master.m3u8"
            thumbUrl = "$base/api/review/thumbnail/$eventId"

            if (!item.hasBeenReviewed) {
                viewModel.markAsReviewed(eventId)
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            player?.let {
                currentPosition = it.currentPosition
                duration = it.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val title = remember(eventId, uiState.reviewItems) {
        val item = uiState.reviewItems.find { it.id == eventId }
        if (item != null) formatCameraName(item.camera) else "Event Clip"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color.Black)
            ) {
                videoUrl?.let {
                    LiveVideoPlayer(
                        url = it,
                        isMuted = isMuted,
                        isPlaying = isPlaying,
                        showController = false,
                        onPlayerCreated = { player = it },
                        placeholderUrl = thumbUrl,
                        okHttpClient = viewModel.okHttpClient,
                        imageLoader = viewModel.imageLoader,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Custom Controls Under Video
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Seek Bar
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { 
                        currentPosition = it.toLong()
                        player?.seekTo(it.toLong())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Labels
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Centered Playback Controls Group
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { player?.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "-10s",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = { isPlaying = !isPlaying },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { player?.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "+10s",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Mute Toggle Aligned to the End (Right)
                    Surface(
                        onClick = { isMuted = !isMuted },
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isMuted) DetectionColors.Animal.bright else DetectionColors.Animal.container,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle Audio",
                                tint = if (!isMuted) DetectionColors.Animal.onBright else DetectionColors.Animal.onContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
