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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.ui.components.FrigateWebRtcMic
import com.rayner.peregrine.ui.components.FrigateWebRtcPlayer
import com.rayner.peregrine.ui.components.HlsPlayer
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
    val context = LocalContext.current
    val view = LocalView.current

    // Keep screen awake when in detail view
    DisposableEffect(initialCameraName) {
        if (initialCameraName != null) {
            val window = (context as? android.app.Activity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
            TopAppBar(title = { Text(initialCameraName ?: "Live View") })
        },
        floatingActionButton = {
            if (initialCameraName != null) {
                val camera = uiState.cameras.firstOrNull { it.name == initialCameraName }
                if (camera != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        FloatingActionButton(
                            onClick = { viewModel.toggleMic(camera.name) },
                            containerColor = if (camera.isMicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (camera.isMicEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = if (camera.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Toggle Mic"
                            )
                        }
                        FloatingActionButton(
                            onClick = { viewModel.toggleSpeaker(camera.name) },
                            containerColor = if (camera.isSpeakerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (camera.isSpeakerEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Icon(
                                imageVector = if (camera.isSpeakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle Speaker"
                            )
                        }
                    }
                }
            }
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
                    items(camerasToShow, key = { it.name }) { camera ->
                        CameraCard(
                            camera = camera,
                            imageLoader = imageLoader,
                            okHttpClient = okHttpClient,
                            isDetailView = initialCameraName != null,
                            snapshotTimestamp = uiState.snapshotTimestamp,
                            onCardClick = {
                                if (initialCameraName == null) {
                                    onCameraClick(camera.name)
                                } else if (!camera.isLive) {
                                    viewModel.setLive(camera.name, true)
                                }
                            },
                            onTogglePlayerType = {
                                viewModel.togglePlayerType(camera.name)
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
    snapshotTimestamp: Long,
    onCardClick: () -> Unit,
    onTogglePlayerType: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .then(if (isDetailView) Modifier.wrapContentHeight() else Modifier.aspectRatio(camera.width.toFloat() / camera.height.toFloat()))
            .clickable(onClick = onCardClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (camera.isLive) {
                if (camera.useHls && camera.hlsUrl != null) {
                    HlsPlayer(
                        url = camera.hlsUrl,
                        isSpeakerEnabled = camera.isSpeakerEnabled,
                        okHttpClient = okHttpClient,
                        modifier = Modifier.fillMaxWidth().aspectRatio(camera.width.toFloat() / camera.height.toFloat())
                    )
                } else if (camera.mseUrl != null) {
                    // Try removing codec hints to let SDP negotiation handle it
                    val signalingUrl = camera.mseUrl
                        .replace("/live/mse/api/ws?src=", "/api/go2rtc/webrtc?src=")
                    
                    val ratio = camera.width.toFloat() / camera.height.toFloat()
                    
                    FrigateWebRtcPlayer(
                        signalingUrl = signalingUrl,
                        isMicEnabled = camera.isMicEnabled,
                        isSpeakerEnabled = camera.isSpeakerEnabled,
                        okHttpClient = okHttpClient,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                    )
                }

                // Parallel microphone connection for HLS mode or two-way talk
                if (camera.isMicEnabled && camera.mseUrl != null) {
                    val micSignalingUrl = camera.mseUrl
                        .replace("/live/mse/api/ws?src=", "/api/go2rtc/webrtc?src=")
                        .let { base -> "$base&media=microphone" }
                    
                    FrigateWebRtcMic(
                        signalingUrl = micSignalingUrl,
                        isEnabled = true,
                        okHttpClient = okHttpClient
                    )
                }
            } else {
                val snapshotUrl = if (camera.snapshotUrl.contains("?")) {
                    "${camera.snapshotUrl}&cache=$snapshotTimestamp"
                } else {
                    "${camera.snapshotUrl}?cache=$snapshotTimestamp"
                }

                val ratio = camera.width.toFloat() / camera.height.toFloat()
                
                var lastSuccessfulPainter by remember { mutableStateOf<Painter?>(null) }

                Box(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
                    AsyncImage(
                        model = snapshotUrl,
                        imageLoader = imageLoader,
                        contentDescription = camera.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        placeholder = lastSuccessfulPainter,
                        onSuccess = { state ->
                            lastSuccessfulPainter = state.painter
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = camera.name,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (camera.isLive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (camera.useHls) "HLS" else "RTC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onTogglePlayerType() }
                        )
                    }
                }
            }
        }
    }
}
