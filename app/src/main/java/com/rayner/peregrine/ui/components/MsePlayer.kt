package com.rayner.peregrine.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream

@OptIn(UnstableApi::class)
@Composable
fun MsePlayer(
    url: String,
    isSpeakerEnabled: Boolean,
    okHttpClient: OkHttpClient,
    onError: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()

    val exoPlayer = remember {
        android.util.Log.d("MsePlayer", "Initializing player with URL: $url")

        val streamUrl = if (url.contains("/live/mse/api/ws") || url.contains("/api/go2rtc/api/ws")) {
            url.replace("https://", "wss://").replace("http://", "ws://")
        } else {
            url
        }

        val dataSourceFactory = DataSource.Factory {
            if (streamUrl.startsWith("ws://") || streamUrl.startsWith("wss://")) {
                WebSocketDataSource(okHttpClient, streamUrl)
            } else {
                OkHttpDataSource.Factory(okHttpClient).createDataSource()
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()
        
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .setContinueLoadingCheckIntervalBytes(32 * 1024)
            .createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        1000,  // minBufferMs
                        2500,  // maxBufferMs
                        500,   // bufferForPlaybackMs
                        1000   // bufferForPlaybackAfterRebufferMs
                    )
                    .setBackBuffer(0, false)
                    .build()
            )
            .build().apply {
            setMediaSource(mediaSource)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("MsePlayer", "Error playing MSE stream: ${error.message}", error)
                    onError?.invoke("MSE playback error: ${error.message}")
                }

                override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                    isPlaying = isPlayingChanged
                }
            })
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (isSpeakerEnabled) 1f else 0f
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            kotlinx.coroutines.delay(10000L) // 10 second timeout
            if (!isPlaying) {
                onError?.invoke("MSE playback timeout")
            }
        }
    }

    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isSpeakerEnabled) {
        exoPlayer.volume = if (isSpeakerEnabled) 1f else 0f
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

    if (isVisible) {
        ZoomableBox(modifier = modifier.fillMaxSize()) {
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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@UnstableApi
private class WebSocketDataSource(
    private val client: OkHttpClient,
    private val url: String
) : DataSource {
    private var webSocket: WebSocket? = null
    private var pipedOutputStream: PipedOutputStream? = null
    private var pipedInputStream: PipedInputStream? = null
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        pipedOutputStream = PipedOutputStream()
        pipedInputStream = PipedInputStream(pipedOutputStream, 1024 * 1024) // 1MB buffer

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Initialize MSE stream with supported codecs
                // Sending a comprehensive list of codecs supported by modern Android decoders
                val initMsg = JSONObject()
                    .put("type", "mse")
                    .put("value", "avc1.640029,avc1.64002A,avc1.640033,hvc1.1.6.L153.B0,mp4a.40.2,mp4a.40.5,opus")
                webSocket.send(initMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                android.util.Log.d("MsePlayer", "Received handshake response: $text")
                // The server responds with the codec it selected, e.g. {"type":"mse","value":"avc1.640029"}
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    pipedOutputStream?.write(bytes.toByteArray())
                } catch (e: IOException) {
                    webSocket.close(1001, "Stream closed")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                try {
                    pipedOutputStream?.close()
                } catch (e: IOException) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                try {
                    pipedOutputStream?.close()
                } catch (e: IOException) {}
            }
        })

        return -1 // Unknown length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return pipedInputStream?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        webSocket?.close(1000, "Close requested")
        webSocket = null
        try {
            pipedInputStream?.close()
            pipedOutputStream?.close()
        } catch (e: IOException) {}
        pipedInputStream = null
        pipedOutputStream = null
    }
}
