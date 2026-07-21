package com.rayner.peregrine.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun FrigateWebRtcPlayer(
    signalingUrl: String,
    isMicEnabled: Boolean,
    isSpeakerEnabled: Boolean,
    okHttpClient: OkHttpClient,
    aspectRatio: Float,
    onError: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val eglBase = remember { EglBase.create() }
    
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    isFirstFrameRendered = true
                }
                override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) {}
            })
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setMirror(false)
            setZOrderMediaOverlay(true)
        }
    }

    val peerConnectionHolder = remember {
        WebRtcPeerConnectionHolder(context, eglBase, renderer, okHttpClient, onError)
    }

    var isVisible by remember { mutableStateOf(true) }
    val isLoading = !isFirstFrameRendered

    LaunchedEffect(isFirstFrameRendered, isVisible) {
        if (isVisible && !isFirstFrameRendered) {
            delay(10000L) // 10 second timeout
            if (!isFirstFrameRendered) {
                onError?.invoke("WebRTC connection timeout")
            }
        }
    }

    DisposableEffect(lifecycleOwner, signalingUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isVisible = true
                isFirstFrameRendered = false
                peerConnectionHolder.start(signalingUrl)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isVisible = false
                peerConnectionHolder.releasePeerConnection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isMicEnabled) {
        peerConnectionHolder.setMicEnabled(isMicEnabled)
    }

    LaunchedEffect(isSpeakerEnabled) {
        peerConnectionHolder.setSpeakerEnabled(isSpeakerEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            peerConnectionHolder.release()
            renderer.release()
            eglBase.release()
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        if (isVisible) {
            ZoomableBox(modifier = Modifier.aspectRatio(aspectRatio)) {
                AndroidView(
                    factory = {
                        renderer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (isLoading && isVisible) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}



private class WebRtcPeerConnectionHolder(
    context: Context,
    eglBase: EglBase,
    private val renderer: SurfaceViewRenderer,
    private val okHttpClient: OkHttpClient,
    private val onError: ((String) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var isMicEnabled: Boolean = false
    private var isSpeakerEnabled: Boolean = false
    private var isMicAdded: Boolean = false
    private var currentSignalingUrl: String? = null
    private val pendingIceCandidates = java.util.Collections.synchronizedList(mutableListOf<IceCandidate>())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setInjectableLogger(null, Logging.Severity.LS_NONE)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun start(signalingUrl: String) {
        scope.launch {
            doStart(signalingUrl)
        }
    }

    private suspend fun doStart(signalingUrl: String) {
        this.currentSignalingUrl = signalingUrl
        this.isMicAdded = false
        pendingIceCandidates.clear()
        releasePeerConnection()

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = isSpeakerEnabled

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED // Allow TCP fallback
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        val iceGatheringDeferred = CompletableDeferred<Unit>()

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                if (newState == PeerConnection.IceConnectionState.FAILED || newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    onError?.invoke("WebRTC ICE connection state: $newState")
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
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
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dataChannel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                receiver?.track()?.let { handleTrack(it) }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                transceiver?.receiver?.track()?.let { handleTrack(it) }
            }

            private fun handleTrack(track: MediaStreamTrack) {
                if (track is VideoTrack) track.addSink(renderer)
                if (track is AudioTrack) {
                    remoteAudioTrack = track
                    track.setEnabled(isSpeakerEnabled)
                }
            }
        }) ?: return

        peerConnection = pc
        this.currentPc = pc

        // Only add audio/mic if mic is enabled to avoid unnecessary permission/resource usage
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, 
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
            
        if (isMicEnabled) {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            localAudioSource = factory.createAudioSource(MediaConstraints())
            localAudioTrack = factory.createAudioTrack("audio_local", localAudioSource).also {
                it.setEnabled(true)
                pc.addTrack(it, listOf("stream0"))
            }
            isMicAdded = true
        } else {
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, 
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        }

        val offer = try {
            pc.createOfferAwait(MediaConstraints())
        } catch (e: Exception) {
            onError?.invoke("WebRTC create offer failed: ${e.message}")
            return
        }
        
        pc.setLocalDescriptionAwait(offer)

        // For HTTP signaling, we must wait for ICE gathering to complete before sending the offer
        // as go2rtc expects the offer to contain all ICE candidates in a one-shot exchange.
        if (!signalingUrl.startsWith("ws")) {
            withTimeoutOrNull(2000L) {
                iceGatheringDeferred.await()
            }
        }

        try {
            val sdp = pc.localDescription.description
            val answerSdp = exchangeOffer(signalingUrl, sdp)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
            if (isMicEnabled) {
                addMicTrack()
            }
        } catch (e: Exception) {
            onError?.invoke("WebRTC signaling failed: ${e.message}")
        }
    }

    private var currentPc: PeerConnection? = null
    private var currentWs: okhttp3.WebSocket? = null

    private fun sendIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) return
        
        synchronized(pendingIceCandidates) {
            val ws = currentWs
            if (ws == null) {
                pendingIceCandidates.add(candidate)
            } else {
                val msg = JSONObject()
                    .put("type", "webrtc/candidate")
                    .put("value", candidate.sdp)
                ws.send(msg.toString())
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        val wasEnabled = isMicEnabled
        isMicEnabled = enabled
        
        if (peerConnection != null) {
            if (enabled && !isMicAdded) {
                scope.launch { addMicTrack() }
            } else {
                localAudioTrack?.setEnabled(enabled)
                
                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (enabled) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } else if (!wasEnabled) { // Only go back to normal if we were the ones who changed it
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            }
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        isSpeakerEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (enabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            // Only force speakerphone if no bluetooth/wired headset is connected
            val hasHeadset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isWiredHeadsetOn
            }
            
            audioManager.isSpeakerphoneOn = !hasHeadset
        } else {
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
        }
    }

    private suspend fun addMicTrack() {
        val pc = peerConnection ?: return
        val signalingUrl = currentSignalingUrl ?: return
        if (isMicAdded) return

        localAudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio_local", localAudioSource).also {
            it.setEnabled(isMicEnabled)
            pc.addTrack(it, listOf("stream0"))
        }
        isMicAdded = true
        
        renegotiate(signalingUrl)
    }

    private suspend fun renegotiate(signalingUrl: String) {
        val pc = peerConnection ?: return
        try {
            val offer = pc.createOfferAwait(MediaConstraints())
            pc.setLocalDescriptionAwait(offer)
            val answerSdp = exchangeOffer(signalingUrl, pc.localDescription.description)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        } catch (e: Exception) {
        }
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
            if (!response.isSuccessful) throw Exception("Signaling failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = try { JSONObject(body) } catch (e: Exception) { null }
            json?.optString("sdp") ?: body // go2rtc can return raw SDP or JSON
        }
    }

    private suspend fun exchangeOfferWebSocket(signalingUrl: String, sdp: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(signalingUrl).build()
        var isResumed = false

        val ws = okHttpClient.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                synchronized(pendingIceCandidates) {
                    currentWs = webSocket
                    
                    // Send buffered candidates
                    pendingIceCandidates.forEach { candidate ->
                        val msg = JSONObject()
                            .put("type", "webrtc/candidate")
                            .put("value", candidate.sdp)
                        webSocket.send(msg.toString())
                    }
                    pendingIceCandidates.clear()
                }

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
                        currentPc?.addIceCandidate(IceCandidate("0", 0, candidateSdp))
                    } else if (type == "webrtc/servers") {
                        val serversJson = json.optJSONArray("value")
                        if (serversJson != null) {
                            val iceServers = mutableListOf<PeerConnection.IceServer>()
                            for (i in 0 until serversJson.length()) {
                                val serverObj = serversJson.getJSONObject(i)
                                val urls = serverObj.optJSONArray("urls")
                                if (urls != null && urls.length() > 0) {
                                    val urlList = mutableListOf<String>()
                                    for (j in 0 until urls.length()) {
                                        urlList.add(urls.getString(j))
                                    }
                                    val builder = PeerConnection.IceServer.builder(urlList)
                                    if (serverObj.has("username")) builder.setUsername(serverObj.getString("username"))
                                    if (serverObj.has("credential")) builder.setPassword(serverObj.getString("credential"))
                                    iceServers.add(builder.createIceServer())
                                }
                            }
                            if (iceServers.isNotEmpty()) {
                                val currentPc = currentPc ?: return@onMessage
                                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                                    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                                }
                                currentPc.setConfiguration(rtcConfig)
                            }
                        }
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

    fun release() {
        releasePeerConnection()
        factory.dispose()
        scope.cancel()
    }

    fun releasePeerConnection() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        remoteAudioTrack = null

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }
}

private suspend fun PeerConnection.createOfferAwait(
    constraints: MediaConstraints
): SessionDescription = suspendCancellableCoroutine { continuation ->
    createOffer(object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp != null) continuation.resume(sdp)
            else continuation.resumeWithException(RuntimeException("Created offer was null"))
        }
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            continuation.resumeWithException(RuntimeException("Create offer failed: $error"))
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
            continuation.resumeWithException(RuntimeException("Set local description failed: $error"))
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
            continuation.resumeWithException(RuntimeException("Set remote description failed: $error"))
        }
    }, sdp)
}
