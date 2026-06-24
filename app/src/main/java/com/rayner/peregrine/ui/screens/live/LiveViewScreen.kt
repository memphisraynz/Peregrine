package com.rayner.peregrine.ui.screens.live

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.ui.components.FrigateWebRtcMic
import com.rayner.peregrine.ui.components.FrigateWebRtcPlayer
import com.rayner.peregrine.ui.components.HlsPlayer
import com.rayner.peregrine.ui.theme.AlertBadgeBg
import com.rayner.peregrine.ui.theme.AlertBadgeText
import com.rayner.peregrine.ui.theme.DetectionColors
import com.rayner.peregrine.ui.theme.LiveDot
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewScreen(
    viewModel: LiveViewModel = hiltViewModel(),
    initialCameraName: String? = null,
    autoStartLive: Boolean = false,
    onReviewClick: (String) -> Unit,
    onCameraClick: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDetail = initialCameraName != null
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val window = (context as? android.app.Activity)?.window
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onResume()
                    viewModel.loadData() // Refresh data when returning to the screen
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onPause()
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { 
                    val title = if (isDetail) {
                        uiState.cameras.find { it.name == initialCameraName }?.displayName ?: initialCameraName!!
                    } else {
                        "Cameras"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    if (isDetail && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isDetail) {
            CameraDetailContent(
                cameraName = initialCameraName!!,
                uiState = uiState,
                viewModel = viewModel,
                onReviewClick = onReviewClick,
                modifier = Modifier.padding(padding)
            )
        } else {
            LiveHomeContent(
                uiState = uiState,
                viewModel = viewModel,
                onReviewClick = onReviewClick,
                onCameraClick = onCameraClick,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun LiveHomeContent(
    uiState: LiveUiState,
    viewModel: LiveViewModel,
    onReviewClick: (String) -> Unit,
    onCameraClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.cameras.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.activeReviews.isNotEmpty()) {
                    item {
                        AlertsCarousel(
                            reviews = uiState.activeReviews,
                            onReviewClick = onReviewClick,
                            baseUrl = uiState.baseUrl,
                            imageLoader = viewModel.imageLoader
                        )
                    }
                }

                item {
                    SectionDivider("All cameras")
                }

                items(uiState.cameras, key = { it.name }) { camera ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        CameraCard(
                            camera = camera,
                            imageLoader = viewModel.imageLoader,
                            onClick = { onCameraClick(camera.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsCarousel(
    reviews: List<ReviewItemEntity>,
    onReviewClick: (String) -> Unit,
    baseUrl: String,
    imageLoader: coil3.ImageLoader
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent alerts",
                style = MaterialTheme.typography.titleMedium
            )
        }

        val scrollState = rememberLazyListState()

        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reviews, key = { it.id }) { review ->
                Box(modifier = Modifier.animateItem()) {
                    AlertCard(review, onReviewClick, baseUrl, imageLoader)
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    review: ReviewItemEntity,
    onReviewClick: (String) -> Unit,
    baseUrl: String,
    imageLoader: coil3.ImageLoader
) {
    val id = review.id
    val label = getDisplayLabel(review)
    val colors = getDetectionColors(review)

    val thumbPath = review.thumbPath
    val normalizedPath = thumbPath.replace("/media/frigate", "")
    val fullThumbUrl = "$baseUrl$normalizedPath"

    Card(
        modifier = Modifier
            .width(130.dp)
            .height(84.dp)
            .clickable { onReviewClick(id) },
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = fullThumbUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            DetectionChipSmall(
                label = label,
                colors = colors,
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
            )
        }
    }
}

@Composable
fun SectionDivider(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
fun MotionDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "motion")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .background(LiveDot.copy(alpha = alpha), CircleShape)
            .border(1.0.dp, Color.White, CircleShape)
    )
}

@Composable
fun CameraCard(
    camera: Camera,
    imageLoader: coil3.ImageLoader,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f) // Fixed aspect ratio for 16:9 cameras
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val snapshotTimestamp = camera.snapshotTimestamp
            val snapshotUrlWithCache = if (camera.snapshotUrl.contains("?")) {
                "${camera.snapshotUrl}&cache=$snapshotTimestamp"
            } else {
                "${camera.snapshotUrl}?cache=$snapshotTimestamp"
            }

            val context = LocalContext.current
            var lastSuccessfulPainter by remember { mutableStateOf<androidx.compose.ui.graphics.painter.Painter?>(null) }

            // 1. Static/Instant Layer: Loads from disk cache immediately
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(context)
                    .data(camera.snapshotUrl)
                    .memoryCacheKey(camera.name)
                    .diskCacheKey(camera.name)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // 2. Live Layer: Fetches fresh data every 1s/5s
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(context)
                    .data(snapshotUrlWithCache)
                    .memoryCacheKey(camera.name)
                    .diskCacheKey(camera.name)
                    .memoryCachePolicy(coil3.request.CachePolicy.WRITE_ONLY)
                    .diskCachePolicy(coil3.request.CachePolicy.WRITE_ONLY)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = camera.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholder = lastSuccessfulPainter,
                onSuccess = { state ->
                    lastSuccessfulPainter = state.painter
                }
            )

            // Overlays
            if (camera.hasMotion) {
                MotionDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            Surface(
                color = Color(0x9E141218),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = camera.displayName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun getDisplayLabel(review: ReviewItemEntity): String {
    val subLabel = review.subLabels.firstOrNull()
    return if (review.objects.contains("person-verified") && subLabel != null) {
        subLabel
    } else {
        review.primaryLabel ?: "Alert"
    }
}

private fun getDetectionColors(review: ReviewItemEntity): DetectionColors.Pair {
    val subLabel = review.subLabels.firstOrNull()
    if (review.objects.contains("person-verified") && subLabel != null) {
        return DetectionColors.Verified
    }
    return when (review.primaryLabel?.lowercase()) {
        "person" -> DetectionColors.Person
        "car", "vehicle", "truck", "bus" -> DetectionColors.Vehicle
        "dog", "cat", "animal" -> DetectionColors.Animal
        else -> DetectionColors.Person
    }
}

@Composable
fun CameraDetailContent(
    cameraName: String,
    uiState: LiveUiState,
    viewModel: LiveViewModel,
    onReviewClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val camera = uiState.cameras.firstOrNull { it.name == cameraName } ?: return
    val okHttpClient = viewModel.okHttpClient

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp) // Increased height for a "bigger" stream
                .background(Color.Black)
        ) {
            if (camera.isLive) {
                if (camera.useHls && camera.hlsUrl != null) {
                    HlsPlayer(
                        url = camera.hlsUrl,
                        isSpeakerEnabled = camera.isSpeakerEnabled,
                        okHttpClient = okHttpClient,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (camera.mseUrl != null) {
                    val signalingUrl = camera.mseUrl
                        .replace("/live/mse/api/ws?src=", "/api/go2rtc/webrtc?src=")
                    
                    val ratio = camera.width.toFloat() / camera.height.toFloat()
                    FrigateWebRtcPlayer(
                        signalingUrl = signalingUrl,
                        isMicEnabled = camera.isMicEnabled,
                        isSpeakerEnabled = camera.isSpeakerEnabled,
                        okHttpClient = okHttpClient,
                        aspectRatio = ratio,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(camera.snapshotUrl)
                        .memoryCacheKey(camera.name)
                        .diskCacheKey(camera.name)
                        .build(),
                    imageLoader = viewModel.imageLoader,
                    contentDescription = camera.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // LIVE pill
            LivePill(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }

        // Microphone connection
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

        // FAB row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailFAB(
                icon = if (camera.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                isActive = camera.isMicEnabled,
                onClick = { viewModel.toggleMic(camera.name) },
                activeColors = DetectionColors.Person,
                inactiveColors = DetectionColors.Person
            )
            Spacer(modifier = Modifier.width(12.dp))
            DetailFAB(
                icon = if (camera.isSpeakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                isActive = camera.isSpeakerEnabled,
                onClick = { viewModel.toggleSpeaker(camera.name) },
                activeColors = DetectionColors.Animal,
                inactiveColors = DetectionColors.Animal
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Recent activity
        Text(
            text = "Recent activity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val cameraReviews = remember(uiState.allReviews, cameraName) {
            uiState.allReviews.filter { it.camera == cameraName }.take(10)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(cameraReviews, key = { it.id }) { review ->
                AlertCard(
                    review = review,
                    onReviewClick = { viewModel.setLive(cameraName, false); onReviewClick(it) },
                    baseUrl = uiState.baseUrl,
                    imageLoader = viewModel.imageLoader
                )
            }
        }
    }
}

@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Surface(
        color = Color(0x9E141218),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LiveDot.copy(alpha = alpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun DetailFAB(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColors: DetectionColors.Pair,
    inactiveColors: DetectionColors.Pair
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) activeColors.bright else activeColors.container,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColors.onBright else activeColors.onContainer,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun DetectionChipSmall(
    label: String,
    colors: DetectionColors.Pair,
    modifier: Modifier = Modifier
) {
    Surface(
        color = colors.container,
        shape = RoundedCornerShape(7.dp),
        modifier = modifier
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onContainer,
            fontSize = 10.sp
        )
    }
}
