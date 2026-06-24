package com.rayner.peregrine.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun HlsPlayer(
    url: String,
    isSpeakerEnabled: Boolean,
    okHttpClient: OkHttpClient,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()

    val exoPlayer = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(url))

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(hlsMediaSource)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("HlsPlayer", "Error playing HLS stream: ${error.message}", error)
                }
            })
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (isSpeakerEnabled) 1f else 0f
        }
    }

    LaunchedEffect(isSpeakerEnabled) {
        exoPlayer.volume = if (isSpeakerEnabled) 1f else 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setBackgroundColor(backgroundColor)
                findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)
                    ?.setBackgroundColor(backgroundColor)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}