package com.remote.gaming.media

import android.app.*
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.remote.gaming.R
import org.webrtc.*

class MediaProjectionService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
    }

    fun startScreenStreaming(
        projectionData: Intent,
        peerConnectionFactory: PeerConnectionFactory,
        eglBaseContext: EglBase.Context,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 60
    ): VideoTrack {
        surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBaseContext)
        
        videoCapturer = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopScreenStreaming()
            }
        })

        videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource!!.capturerObserver)
        videoCapturer?.startCapture(width, height, fps)

        videoTrack = peerConnectionFactory.createVideoTrack("SCREEN_TRACK", videoSource)
        videoTrack?.setEnabled(true)

        return videoTrack!!
    }

    fun stopScreenStreaming() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoSource?.dispose()
            surfaceTextureHelper?.dispose()
            virtualDisplay?.release()
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopScreenStreaming()
        super.onDestroy()
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "remote_gaming_capture"
        val channel = NotificationChannel(
            channelId,
            "Remote Gaming Streaming",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Chromebook Remote Gaming Active")
            .setContentText("Streaming screen at 60 FPS with low-latency WebRTC")
            .setSmallIcon(R.drawable.ic_gaming)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}