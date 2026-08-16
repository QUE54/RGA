package com.remote.gaming.webrtc

import android.content.Context
import com.remote.gaming.input.AccessibilityInputService
import org.json.JSONObject
import org.webrtc.*

class WebRtcStreamer(
    private val context: Context,
    private val eglBase: EglBase,
    private val onSignalingMessage: (String, JSONObject) -> Unit
) : PeerConnection.Observer, DataChannel.Observer {

    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var inputDataChannel: DataChannel? = null

    init {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8 */ true,
            /* enableH264HighProfile */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(videoTrack: VideoTrack, audioTrack: AudioTrack?) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this)
        
        // Add Screen video track
        peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
        audioTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }

        // Setup DataChannel for zero-latency touch receiving
        val dcInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        }
        inputDataChannel = peerConnection?.createDataChannel("touch-input", dcInit)
        inputDataChannel?.registerObserver(this)
    }

    fun handleOffer(offerSdp: String) {
        val description = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                createAnswer()
            }
        }, description)
    }

    private fun createAnswer() {
        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                val payload = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", desc.description)
                }
                onSignalingMessage("WEBRTC_ANSWER", payload)
            }
        }, sdpMediaConstraints)
    }

    override fun onMessage(buffer: DataChannel.Buffer) {
        val data = ByteArray(buffer.data.remaining())
        buffer.data.get(data)
        val jsonStr = String(data)
        
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("t")
            
            if (type == "TOUCH") {
                val action = json.optString("a")
                val ax = json.optDouble("ax").toFloat()
                val ay = json.optDouble("ay").toFloat()

                if (action == "DOWN") {
                    AccessibilityInputService.instance?.injectTap(ax, ay)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // PeerConnection Observer Callbacks
    override fun onIceCandidate(candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdp", candidate.sdp)
        }
        onSignalingMessage("WEBRTC_ICE_CANDIDATE", payload)
    }

    override fun onBufferedAmountChange(l: Long) {}
    override fun onStateChange() {}
    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(b: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(dc: DataChannel) {
        inputDataChannel = dc
        dc.registerObserver(this)
    }
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(err: String) {}
        override fun onSetFailure(err: String) {}
    }
}