package com.phonemirror.client.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.phonemirror.client.MainActivity
import com.phonemirror.client.R
import com.phonemirror.client.decoder.VideoDecoder
import com.phonemirror.client.network.ConnectionClient
import com.phonemirror.client.network.TouchSender
import com.phonemirror.common.Protocol

class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindowService"
        // 静态变量保存连接参数，防止服务重启时 Intent extra 丢失
        var savedHost: String? = null
        var savedPort: Int = Protocol.DEFAULT_STREAM_PORT
    }

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var surfaceView: SurfaceView? = null
    private var tvStatus: TextView? = null
    private var decoder: VideoDecoder? = null
    private var connection: ConnectionClient? = null
    private var touchSender: TouchSender? = null
    private var btnClose: ImageView? = null
    private var btnResize: ImageView? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 优先从 Intent 获取，否则使用静态变量（防止服务重启时 Intent extra 丢失）
            val host = intent?.getStringExtra("host") ?: savedHost
            if (host.isNullOrEmpty()) {
                Log.e(TAG, "Host is null or empty")
                stopSelf()
                return START_NOT_STICKY
            }
            
            // 检查 Intent 是否包含 port 字段，如果没有则使用静态变量
            val port = if (intent?.hasExtra("port") == true) {
                intent.getIntExtra("port", Protocol.DEFAULT_STREAM_PORT)
            } else {
                savedPort
            }
            
            Log.d(TAG, "Connecting to $host:$port (intent has port: ${intent?.hasExtra("port")})")

            if (port <= 0 || port > 65535) {
                Log.e(TAG, "Invalid port: $port, using default")
            }

            // 更新静态变量
            savedHost = host
            savedPort = port

            Log.d(TAG, "Starting foreground notification...")
            startForegroundNotification()
            
            Log.d(TAG, "Creating floating window...")
            createFloatingWindow()
            
            Log.d(TAG, "Connecting to phone...")
            connectToPhone(host, port.coerceIn(1, 65535))
            
            Log.i(TAG, "Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createFloatingWindow() {
        try {
            Log.d(TAG, "Getting WindowManager...")
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager!!.defaultDisplay.getMetrics(dm)
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
            Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}")

            val inflater = LayoutInflater.from(this)
            floatView = inflater.inflate(R.layout.floating_window, null)
            surfaceView = floatView!!.findViewById(R.id.surface_view)
            tvStatus = floatView!!.findViewById(R.id.tv_status)
            btnClose = floatView!!.findViewById(R.id.btn_close)
            btnResize = floatView!!.findViewById(R.id.btn_resize)
            Log.d(TAG, "Views inflated successfully")

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

            val params = WindowManager.LayoutParams(
                (screenWidth * 0.85f).toInt(),
                (screenHeight * 0.7f).toInt(),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenWidth * 0.075f).toInt()
                y = (screenHeight * 0.1f).toInt()
            }

            Log.d(TAG, "Adding view to WindowManager...")
            windowManager!!.addView(floatView, params)
            Log.d(TAG, "Floating window added successfully")
            
            setupTouchHandling()
            setupButtons()
            Log.d(TAG, "Touch handling and buttons setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating window", e)
            throw e  // 重新抛出，让调用方知道失败了
        }
    }

    private fun setupTouchHandling() {
        surfaceView?.setOnTouchListener { v, event ->
            try {
                if (isResizing) return@setOnTouchListener true
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        sendTouchEvent(Protocol.TOUCH_DOWN, event.x, event.y, v)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        sendTouchEvent(Protocol.TOUCH_MOVE, event.x, event.y, v)
                    }
                    MotionEvent.ACTION_UP -> {
                        sendTouchEvent(Protocol.TOUCH_UP, event.x, event.y, v)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Touch handling error", e)
            }
            true
        }
    }

    private fun sendTouchEvent(action: Int, x: Float, y: Float, view: View) {
        try {
            val normalizedX = (x / view.width).coerceIn(0f, 1f)
            val normalizedY = (y / view.height).coerceIn(0f, 1f)
            touchSender?.send(action, normalizedX, normalizedY)
        } catch (e: Exception) {
            Log.e(TAG, "Send touch error", e)
        }
    }

    private fun setupButtons() {
        btnClose?.setOnClickListener { stopSelf() }

        btnResize?.setOnTouchListener { _, event ->
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isResizing = true
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY

                        val lp = floatView!!.layoutParams as WindowManager.LayoutParams
                        val newW = (lp.width + dx.toInt()).coerceIn(300, screenWidth)
                        val ratio = decoder?.let {
                            it.videoWidth.toFloat() / it.videoHeight.toFloat()
                        } ?: (16f / 9f)
                        val newH = (newW / ratio).toInt()
                        lp.width = newW
                        lp.height = newH
                        windowManager!!.updateViewLayout(floatView, lp)
                    }
                    MotionEvent.ACTION_UP -> {
                        isResizing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resize error", e)
            }
            true
        }
    }

    private fun connectToPhone(host: String, port: Int) {
        try {
            connection = ConnectionClient(host, port)
            decoder = VideoDecoder(surfaceView!!)
            touchSender = TouchSender(connection!!)

            connection!!.onVideoCodecReceived = { codecInfo ->
                try {
                    Log.i(TAG, "收到视频编码信息: ${codecInfo.width}x${codecInfo.height}")
                    decoder?.configure(codecInfo.width, codecInfo.height)
                    updateStatus("")  // 连接成功，隐藏状态文字
                } catch (e: Exception) {
                    Log.e(TAG, "Configure decoder error", e)
                    updateStatus("解码器配置失败")
                }
            }
            connection!!.onVideoFrameReceived = { data ->
                try {
                    decoder?.feedData(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Feed data error", e)
                }
            }

            Thread({
                try {
                    Log.i(TAG, "正在连接 $host:$port...")
                    updateStatus("正在连接 $host:$port...")
                    connection!!.connect()
                    Log.i(TAG, "连接已断开")
                    updateStatus("连接已断开")
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error", e)
                    updateStatus("连接失败: ${e.message}")
                }
            }, "ConnectionClient").start()
        } catch (e: Exception) {
            Log.e(TAG, "Connect error", e)
            updateStatus("启动失败: ${e.message}")
        }
    }

    private fun updateStatus(message: String) {
        try {
            tvStatus?.post {
                tvStatus?.text = message
                tvStatus?.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateStatus error", e)
        }
    }

    private fun startForegroundNotification() {
        val channelId = "floating_window"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "镜像投屏", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("手机镜像")
            .setContentText("正在接收投屏...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(2, n)
        }
    }

    override fun onDestroy() {
        try {
            connection?.disconnect()
            decoder?.release()
            floatView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
        super.onDestroy()
    }
}
