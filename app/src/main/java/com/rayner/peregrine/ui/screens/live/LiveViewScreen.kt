package com.rayner.peregrine.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.ui.components.FrigateWebRtcPlayer
import com.rayner.peregrine.ui.components.LiveVideoPlayer
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewScreen(
    viewModel: LiveViewModel = hiltViewModel(),
    initialCameraName: String? = null,
    autoStartLive: Boolean = false,
    onReviewClick: (String) -> Unit,
    onCameraClick: (String) -> Unit
) {
    val imageLoader = viewModel.imageLoader
    val okHttpClient = viewModel.okHttpClient
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialCameraName, autoStartLive, uiState.cameras) {
        if (autoStartLive && initialCameraName != null) {
            val targetCamera = uiState.cameras.firstOrNull { it.name == initialCameraName }
            if (targetCamera != null && !targetCamera.isLive) {
                viewModel.setLive(initialCameraName, true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Live View") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.activeReviews.isNotEmpty()) {
                ActiveReviewsBar(
                    reviews = uiState.activeReviews,
                    onReviewClick = onReviewClick
                )
            }

            if (uiState.isLoading && uiState.cameras.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null && uiState.cameras.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                val camerasToShow = initialCameraName?.let { selectedName ->
                    uiState.cameras.filter { it.name == selectedName }
                } ?: uiState.cameras

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(camerasToShow) { camera ->
                        CameraCard(
                            camera = camera,
                            imageLoader = imageLoader,
                            okHttpClient = okHttpClient,
                            isDetailView = initialCameraName != null,
                            onMicToggle = { viewModel.toggleMic(camera.name) },
                            onSpeakerToggle = { viewModel.toggleSpeaker(camera.name) },
                            onCardClick = {
                                if (initialCameraName == null) {
                                    onCameraClick(camera.name)
                                } else if (!camera.isLive) {
                                    viewModel.setLive(camera.name, true)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveReviewsBar(
    reviews: List<Map<String, Any>>,
    onReviewClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Active Reviews",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reviews) { review ->
                val id = review["id"] as? String ?: ""
                val camera = review["camera"] as? String ?: "Unknown"
                val label = review["label"] as? String ?: "Event"

                AssistChip(
                    onClick = { onReviewClick(id) },
                    label = { Text("$camera: $label") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
fun CameraCard(
    camera: Camera,
    imageLoader: ImageLoader,
    okHttpClient: OkHttpClient,
    isDetailView: Boolean,
    onMicToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(1.77f)
            .clickable(onClick = onCardClick)
    ) {
        Box {
            if (camera.isLive && camera.mseUrl != null) {
                FrigateWebRtcPlayer(
                    signalingUrl = camera.mseUrl
                        .replace("/live/mse/api/ws?src=", "/api/go2rtc/webrtc?src=")
                        .replace("_sub", ""),
                    isMicEnabled = camera.isMicEnabled,
                    isSpeakerEnabled = camera.isSpeakerEnabled,
                    okHttpClient = okHttpClient,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = camera.snapshotUrl,
                    imageLoader = imageLoader,
                    contentDescription = camera.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (camera.isLive) transparent else MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!camera.isLive) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Live",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (isDetailView) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onMicToggle,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (camera.isMicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = if (camera.isMicEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (camera.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Toggle Mic",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onSpeakerToggle,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (camera.isSpeakerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = if (camera.isSpeakerEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (camera.isSpeakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Speaker",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private val transparent = androidx.compose.ui.graphics.Color.Transparent
