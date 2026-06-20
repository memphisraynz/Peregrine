package com.rayner.peregrine.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.coroutines.resume

@Composable
fun FrigateWebRtcPlayer(
    signalingUrl: String,
    isMicEnabled: Boolean,
    isSpeakerEnabled: Boolean,
    okHttpClient: OkHttpClient,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBase.eglBaseContext, null)
            setEnableHardwareScaler(true)
        }
    }

    val peerConnectionHolder = remember {
        WebRtcPeerConnectionHolder(context, eglBase, renderer, okHttpClient)
    }

    LaunchedEffect(signalingUrl) {
        peerConnectionHolder.start(signalingUrl)
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
        modifier = modifier
    )
}

private class WebRtcPeerConnectionHolder(
    context: Context,
    eglBase: EglBase,
    private val renderer: SurfaceViewRenderer,
    private val okHttpClient: OkHttpClient
) {
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    suspend fun start(signalingUrl: String) {
        releasePeerConnection()

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {
                val track = receiver?.track()
                when (track) {
                    is VideoTrack -> track.addSink(renderer)
                    is AudioTrack -> {
                        remoteAudioTrack = track
                        track.setEnabled(true)
                    }
                }
            }
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                when (track) {
                    is VideoTrack -> track.addSink(renderer)
                    is AudioTrack -> {
                        remoteAudioTrack = track
                        track.setEnabled(true)
                    }
                }
            }
        }) ?: return

        peerConnection = pc

        val audioSource: AudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio", audioSource).also {
            pc.addTrack(it)
        }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        val offer = pc.createOfferAwait(constraints)
        pc.setLocalDescriptionAwait(offer)

        val answerSdp = exchangeOffer(signalingUrl, offer.description)
        pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        remoteAudioTrack?.setEnabled(enabled)
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
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            json.getString("sdp")
        }
    }

    fun release() {
        releasePeerConnection()
        factory.dispose()
    }

    private fun releasePeerConnection() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack = null
        remoteAudioTrack = null
    }
}

private suspend fun PeerConnection.createOfferAwait(
    constraints: MediaConstraints
): SessionDescription = suspendCancellableCoroutine { continuation ->
    createOffer(object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
            continuation.resume(sessionDescription!!)
        }
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }, constraints)
}

private suspend fun PeerConnection.setLocalDescriptionAwait(
    sessionDescription: SessionDescription
) = suspendCancellableCoroutine<Unit> { continuation ->
    setLocalDescription(object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) = Unit
        override fun onSetSuccess() {
            continuation.resume(Unit)
        }
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }, sessionDescription)
}

private suspend fun PeerConnection.setRemoteDescriptionAwait(
    sessionDescription: SessionDescription
) = suspendCancellableCoroutine<Unit> { continuation ->
    setRemoteDescription(object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) = Unit
        override fun onSetSuccess() {
            continuation.resume(Unit)
        }
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }, sessionDescription)
}
