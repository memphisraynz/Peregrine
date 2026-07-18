package com.rayner.peregrine.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun FrigateWebRtcMic(
    signalingUrl: String,
    isEnabled: Boolean,
    okHttpClient: OkHttpClient,
    onError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val micHolder = remember {
        WebRtcMicHolder(context, okHttpClient, onError)
    }

    DisposableEffect(lifecycleOwner, signalingUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                micHolder.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            micHolder.release()
        }
    }

    LaunchedEffect(isEnabled, signalingUrl) {
        if (isEnabled) {
            micHolder.start(signalingUrl)
        } else {
            micHolder.stop()
        }
    }
}

private class WebRtcMicHolder(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val onError: ((String) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var currentWs: okhttp3.WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun initFactory() {
        if (factory != null) return
        
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setInjectableLogger(null, Logging.Severity.LS_NONE)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun start(signalingUrl: String) {
        scope.launch {
            doStart(signalingUrl)
        }
    }

    private suspend fun doStart(signalingUrl: String) {
        stop()
        initFactory()

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val iceGatheringDeferred = CompletableDeferred<Unit>()

        val pc = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.FAILED || newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    onError?.invoke("Mic WebRTC ICE: $newState")
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringDeferred.complete(Unit)
                }
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (signalingUrl.startsWith("ws")) {
                    sendIceCandidate(candidate)
                }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }) ?: return

        peerConnection = pc

        // Request a single audio transceiver in SEND_RECV mode
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        )

        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("audio_mic", audioSource)?.apply {
            setEnabled(true)
            pc.addTrack(this)
        }

        val offer = try {
            pc.createOfferAwait(MediaConstraints())
        } catch (e: Exception) {
            onError?.invoke("Mic create offer failed: ${e.message}")
            return
        }
        
        pc.setLocalDescriptionAwait(offer)

        if (!signalingUrl.startsWith("ws")) {
            withTimeoutOrNull(2000L) {
                iceGatheringDeferred.await()
            }
        }

        try {
            val sdp = pc.localDescription.description
            val answerSdp = exchangeOffer(signalingUrl, sdp)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        } catch (e: Exception) {
            onError?.invoke("Mic signaling failed: ${e.message}")
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate?) {
        val ws = currentWs ?: return
        if (candidate == null) return
        
        val msg = JSONObject()
            .put("type", "webrtc/candidate")
            .put("value", candidate.sdp)
        ws.send(msg.toString())
    }

    fun stop() {
        peerConnection?.close()
        peerConnection = null
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        currentWs?.close(1000, "Stopped")
        currentWs = null
        
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun release() {
        stop()
        factory?.dispose()
        factory = null
        scope.cancel()
    }

    private suspend fun exchangeOffer(signalingUrl: String, sdp: String): String = withContext(Dispatchers.IO) {
        if (signalingUrl.startsWith("ws://") || signalingUrl.startsWith("wss://")) {
            return@withContext exchangeOfferWebSocket(signalingUrl, sdp)
        }
        
        val payload = JSONObject()
            .put("type", "offer")
            .put("sdp", sdp)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(signalingUrl)
            .post(payload)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = try { JSONObject(body) } catch (e: Exception) { null }
            json?.optString("sdp") ?: body
        }
    }

    private suspend fun exchangeOfferWebSocket(signalingUrl: String, sdp: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(signalingUrl).build()
        var isResumed = false

        val ws = okHttpClient.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                currentWs = webSocket
                val offer = JSONObject()
                    .put("type", "webrtc/offer")
                    .put("value", sdp)
                webSocket.send(offer.toString())
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "webrtc/answer") {
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume(json.getString("value"))
                        }
                    } else if (type == "webrtc/candidate") {
                        val candidateSdp = json.getString("value")
                        peerConnection?.addIceCandidate(IceCandidate("0", 0, candidateSdp))
                    }
                } catch (e: Exception) {
                    if (!isResumed) {
                        isResumed = true
                        continuation.resumeWithException(e)
                        webSocket.close(1000, "Error")
                    }
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resumeWithException(t)
                }
            }
        })

        continuation.invokeOnCancellation {
            ws.close(1000, "Cancelled")
            currentWs = null
        }
    }
}

private suspend fun PeerConnection.createOfferAwait(
    constraints: MediaConstraints
): SessionDescription = suspendCancellableCoroutine { continuation ->
    createOffer(object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp != null) continuation.resume(sdp)
            else continuation.resumeWithException(RuntimeException("Null Offer"))
        }
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            continuation.resumeWithException(RuntimeException(error))
        }
        override fun onSetFailure(error: String?) = Unit
    }, constraints)
}

private suspend fun PeerConnection.setLocalDescriptionAwait(
    sdp: SessionDescription
) = suspendCancellableCoroutine<Unit> { continuation ->
    setLocalDescription(object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = Unit
        override fun onSetSuccess() {
            continuation.resume(Unit)
        }
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) {
            continuation.resumeWithException(RuntimeException(error))
        }
    }, sdp)
}

private suspend fun PeerConnection.setRemoteDescriptionAwait(
    sdp: SessionDescription
) = suspendCancellableCoroutine<Unit> { continuation ->
    setRemoteDescription(object : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) = Unit
        override fun onSetSuccess() {
            continuation.resume(Unit)
        }
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) {
            continuation.resumeWithException(RuntimeException(error))
        }
    }, sdp)
}
