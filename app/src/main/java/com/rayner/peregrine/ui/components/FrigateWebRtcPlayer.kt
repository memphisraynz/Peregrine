package com.rayner.peregrine.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun FrigateWebRtcPlayer(
    signalingUrl: String,
    isMicEnabled: Boolean,
    isSpeakerEnabled: Boolean,
    okHttpClient: OkHttpClient,
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
        }
    }

    val peerConnectionHolder = remember {
        WebRtcPeerConnectionHolder(context, eglBase, renderer, okHttpClient)
    }

    var connectionState by remember { mutableStateOf(PeerConnection.IceConnectionState.NEW) }
    
    val isLoading = !isFirstFrameRendered

    DisposableEffect(lifecycleOwner, signalingUrl) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isFirstFrameRendered = false
                peerConnectionHolder.start(signalingUrl) { state ->
                    connectionState = state
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                peerConnectionHolder.releasePeerConnection()
                connectionState = PeerConnection.IceConnectionState.DISCONNECTED
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

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                FrameLayout(context).apply {
                    addView(
                        renderer,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private class WebRtcPeerConnectionHolder(
    context: Context,
    eglBase: EglBase,
    private val renderer: SurfaceViewRenderer,
    private val okHttpClient: OkHttpClient
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

    fun start(signalingUrl: String, onStateChange: (PeerConnection.IceConnectionState) -> Unit) {
        scope.launch {
            doStart(signalingUrl, onStateChange)
        }
    }

    private suspend fun doStart(signalingUrl: String, onStateChange: (PeerConnection.IceConnectionState) -> Unit) {
        this.currentSignalingUrl = signalingUrl
        this.isMicAdded = false
        releasePeerConnection()

        // Prepare AudioManager for WebRTC
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeakerEnabled

        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        
        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                newState?.let { onStateChange(it) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) = Unit
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

        // Phase 1: Baseline stream (Video + Audio RECV_ONLY)
        val transceiverInit = RtpTransceiver.RtpTransceiverInit(
            RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
            listOf("stream0")
        )
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit)
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)

        val offer = pc.createOfferAwait(MediaConstraints())
        pc.setLocalDescriptionAwait(offer)

        try {
            val answerSdp = exchangeOffer(signalingUrl, offer.description)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
            
            // Phase 3: If mic is already requested, activate it now
            if (isMicEnabled) {
                addMicTrack()
            }
        } catch (e: Exception) {
            // Error ignored for reduced noise
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        isMicEnabled = enabled
        // Mic handled separately now
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        isSpeakerEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        audioManager.isSpeakerphoneOn = enabled
        
        if (enabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val speaker = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            // We keep MODE_IN_COMMUNICATION while the call is active to avoid routing issues,
            // but we could switch back to NORMAL if preferred.
        }
    }

    private suspend fun addMicTrack() {
        val pc = peerConnection ?: return
        val signalingUrl = currentSignalingUrl ?: return
        if (isMicAdded) return

        localAudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio_local", localAudioSource).also {
            it.setEnabled(false) // Handle mic separately via FrigateWebRtcMic
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
            val answerSdp = exchangeOffer(signalingUrl, offer.description)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        } catch (e: Exception) {
            // Error ignored for reduced noise
        }
    }

    private suspend fun exchangeOffer(signalingUrl: String, sdp: String): String = withContext(Dispatchers.IO) {
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
            val json = JSONObject(body)
            json.getString("sdp")
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
