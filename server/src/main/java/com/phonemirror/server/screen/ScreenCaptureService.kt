package com.phonemirror.server.screen

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
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

    companion object {
        private const val TAG = "ScreenCaptureService"
        // 静态变量传递 projection 数据，避免 Intent 序列化问题
        var projectionResultCode: Int = Activity.RESULT_CANCELED
        var projectionData: Intent? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: VideoEncoder? = null
    private var connectionServer: ConnectionServer? = null
    private var udpDiscovery: UdpDiscovery? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val port = intent?.getIntExtra("port", Protocol.DEFAULT_STREAM_PORT) ?: Protocol.DEFAULT_STREAM_PORT
            val resultCode = projectionResultCode
            val data = projectionData

            if (data == null) {
                Log.e(TAG, "Projection data is null")
                stopSelf()
                return START_NOT_STICKY
            }

            // Android 14 要求：必须先 startForeground，再 getMediaProjection，且必须在 3 秒内完成
            startForegroundNotification()

            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = pm.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                stopSelf()
                return START_NOT_STICKY
            }

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val scale = 0.75f
            val w = (metrics.widthPixels * scale).toInt() and 0xFFFE
            val h = (metrics.heightPixels * scale).toInt() and 0xFFFE

            Log.d(TAG, "Starting capture: ${w}x${h} @ ${metrics.densityDpi}dpi")

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

            // 清理静态变量
            projectionData = null

            Log.i(TAG, "Screen capture started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
            stopSelf()
        }

        return START_NOT_STICKY
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
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onDestroy() {
        udpDiscovery?.stop()
        connectionServer?.stop()
        videoEncoder?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
