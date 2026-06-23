package com.phonemirror.server.screen

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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
import com.phonemirror.server.util.FileLogger

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: VideoEncoder? = null
    private var connectionServer: ConnectionServer? = null
    private var udpDiscovery: UdpDiscovery? = null

    companion object {
        private const val TAG = "ScreenCaptureService"
        var isRunning: Boolean = false
        var projectionResultCode: Int = Activity.RESULT_CANCELED
        var projectionData: Intent? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FileLogger.log(TAG, "=== 服务启动 ===")
        try {
            val resultCode = projectionResultCode
            val data = projectionData
            
            FileLogger.log(TAG, "Received resultCode: $resultCode, hasData: ${data != null}")

            if (data == null) {
                FileLogger.error(TAG, "Data intent is null")
                stopSelf()
                return START_NOT_STICKY
            }

            if (resultCode != Activity.RESULT_OK) {
                FileLogger.error(TAG, "Result code is not OK: $resultCode")
                stopSelf()
                return START_NOT_STICKY
            }

            val port = Protocol.DEFAULT_STREAM_PORT
            FileLogger.log(TAG, "启动前台服务通知, port=$port")
            startForegroundNotification()
            FileLogger.log(TAG, "前台服务通知启动成功")

            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            FileLogger.log(TAG, "获取 MediaProjection...")
            mediaProjection = pm.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                FileLogger.error(TAG, "Failed to get MediaProjection!")
                stopSelf()
                return START_NOT_STICKY
            }
            FileLogger.log(TAG, "MediaProjection 获取成功")

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val scale = 0.75f
            val w = (metrics.widthPixels * scale).toInt() and 0xFFFE
            val h = (metrics.heightPixels * scale).toInt() and 0xFFFE

            FileLogger.log(TAG, "Starting capture: ${w}x${h} @ ${metrics.densityDpi}dpi")

            val codecInfo = VideoCodecInfo(w, h, 30, 4_000_000)
            videoEncoder = VideoEncoder(codecInfo)
            val surface = videoEncoder!!.start()
            FileLogger.log(TAG, "视频编码器启动成功")
            videoEncoder!!.onFrameEncoded = { buf, info ->
                connectionServer?.broadcastVideoFrame(buf, info)
            }

            mediaProjection!!.createVirtualDisplay(
                "PhoneMirror", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
            FileLogger.log(TAG, "VirtualDisplay 创建成功")

            connectionServer = ConnectionServer(port, codecInfo).apply { start() }
            udpDiscovery = UdpDiscovery(port).apply { start() }

            isRunning = true
            FileLogger.log(TAG, "=== 投屏服务完全启动成功 ===")
        } catch (e: SecurityException) {
            FileLogger.error(TAG, "SecurityException: ${e.message}", e)
            isRunning = false
            stopSelf()
        } catch (e: Exception) {
            FileLogger.error(TAG, "Error starting screen capture: ${e.message}", e)
            isRunning = false
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

        // Android 14+ 要求明确指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        FileLogger.log(TAG, "服务销毁")
        isRunning = false
        udpDiscovery?.stop()
        connectionServer?.stop()
        videoEncoder?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
