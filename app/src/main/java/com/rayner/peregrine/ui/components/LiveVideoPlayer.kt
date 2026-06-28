package com.rayner.peregrine.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun LiveVideoPlayer(
    url: String,
    isMuted: Boolean,
    isPlaying: Boolean = true,
    showController: Boolean = true,
    onPlayerCreated: ((Player) -> Unit)? = null,
    placeholderUrl: String? = null,
    okHttpClient: OkHttpClient,
    imageLoader: coil3.ImageLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlayerReady by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()

    val exoPlayer = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            isPlayerReady = true
                        }
                    }
                })
                onPlayerCreated?.invoke(this)
            }
    }

    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isVisible = true
                    exoPlayer.play()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isVisible = false
                    exoPlayer.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.TopCenter // Pins the actual video surface to the top
    ) {
        if (isVisible) {
            ZoomableBox(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            player = exoPlayer
                            useController = showController
                            controllerAutoShow = showController
                            controllerHideOnTouch = showController
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            setBackgroundColor(backgroundColor)
                            findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)
                                ?.setBackgroundColor(backgroundColor)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        if ((!isPlayerReady || !isVisible) && placeholderUrl != null) {
            AsyncImage(
                model = placeholderUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter
            )
        }
    }
}


