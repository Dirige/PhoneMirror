package com.phonemirror.server.screen

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.phonemirror.common.Protocol
import com.phonemirror.common.VideoCodecInfo
import com.phonemirror.server.MainActivity
import com.phonemirror.server.R
import com.phonemirror.server.encoder.VideoEncoder
import com.phonemirror.server.network.ConnectionServer
import com.phonemirror.server.discovery.UdpDiscovery

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: VideoEncoder? = null
    private var connectionServer: ConnectionServer? = null
    private var udpDiscovery: UdpDiscovery? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("data", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra("data")
        val port = intent.getIntExtra("port", Protocol.DEFAULT_STREAM_PORT)

        startForegroundNotification()
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, data ?: run { stopSelf(); return START_NOT_STICKY })

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)

        val scale = 0.75f
        val w = (metrics.widthPixels * scale).toInt() and 0xFFFE
        val h = (metrics.heightPixels * scale).toInt() and 0xFFFE

        val codecInfo = VideoCodecInfo(w, h, 30, 4_000_000)
        videoEncoder = VideoEncoder(codecInfo)
        val surface = videoEncoder!!.start()
        videoEncoder!!.onFrameEncoded = { buf, info ->
            connectionServer?.broadcastVideoFrame(buf, info)
        }

        mediaProjection!!.createVirtualDisplay(
            "PhoneMirror", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )

        connectionServer = ConnectionServer(port, codecInfo).apply { start() }
        udpDiscovery = UdpDiscovery(port).apply { start() }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "屏幕投屏", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("手机镜像")
            .setContentText("正在投屏...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .build()
        startForeground(1, n)
    }

    override fun onDestroy() {
        udpDiscovery?.stop()
        connectionServer?.stop()
        videoEncoder?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
