package com.rayner.peregrine.data.remote.ws

import android.util.Log
import com.google.gson.Gson
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverUrlManager: ServerUrlManager,
    private val repository: FrigateRepository
) {
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    
    private val _updates = MutableSharedFlow<WsMessage>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates = _updates.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun connect() {
        if (webSocket != null) return
        
        scope.launch {
            val config = repository.getServerConfig().firstOrNull() ?: return@launch
            val authCookie = config.authCookie
            val baseUrl = serverUrlManager.getUrl() ?: config.serverUrl
            val url = baseUrl.toHttpUrlOrNull() ?: return@launch
            
            // Build the handshake URL
            val wsUrl = url.newBuilder()
                .encodedPath("/ws")
                .build()
            
            Log.d("FrigateWS", "Connecting to $wsUrl with cookie presence: ${authCookie != null}")

            val requestBuilder = Request.Builder()
                .url(wsUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .addHeader("Origin", "${url.scheme}://${url.host}")

            if (authCookie != null) {
                requestBuilder.addHeader("Cookie", "frigate_token=$authCookie")
            }

            val request = requestBuilder.build()

            // Use a clean client to avoid interference, but keep DNS settings if they were customized
            val wsClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .build()

            webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("FrigateWS", "WebSocket Opened")
                    // Send onConnect message to start receiving updates
                    val connectMsg = gson.toJson(WsMessage("onConnect", ""))
                    webSocket.send(connectMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        // Some messages might be raw strings, only emit if it looks like JSON
                        if (text.startsWith("{")) {
                            val msg = gson.fromJson(text, WsMessage::class.java)
                            if (msg != null && msg.topic != null) {
                                scope.launch { _updates.emit(msg) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FrigateWS", "Parse error: ${e.message}")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("FrigateWS", "WebSocket Closing: $reason")
                    webSocket.close(1000, null)
                    this@FrigateWebSocketManager.webSocket = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("FrigateWS", "WebSocket Failure: ${t.message}, Code: ${response?.code}")
                    this@FrigateWebSocketManager.webSocket = null
                }
            })
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
    }
}

data class WsMessage(
    val topic: String,
    val payload: Any?
)
