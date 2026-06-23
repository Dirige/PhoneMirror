package com.phonemirror.client.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.phonemirror.client.MainActivity
import com.phonemirror.client.R
import com.phonemirror.client.decoder.VideoDecoder
import com.phonemirror.client.network.ConnectionClient
import com.phonemirror.client.network.TouchSender
import com.phonemirror.common.Protocol

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var surfaceView: SurfaceView? = null
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
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val host = intent.getStringExtra("host") ?: run { stopSelf(); return START_NOT_STICKY }
        val port = intent.getIntExtra("port", Protocol.DEFAULT_STREAM_PORT)

        startForegroundNotification()
        createFloatingWindow()
        connectToPhone(host, port)
        return START_STICKY
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager!!.defaultDisplay.getMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels

        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.floating_window, null)
        surfaceView = floatView!!.findViewById(R.id.surface_view)
        btnClose = floatView!!.findViewById(R.id.btn_close)
        btnResize = floatView!!.findViewById(R.id.btn_resize)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            (screenWidth * 0.85f).toInt(),
            (screenHeight * 0.7f).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth * 0.075f).toInt()
            y = (screenHeight * 0.1f).toInt()
        }

        windowManager!!.addView(floatView, params)
        setupTouchHandling()
        setupButtons()
    }

    private fun setupTouchHandling() {
        surfaceView!!.setOnTouchListener { v, event ->
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
            true
        }
    }

    private fun sendTouchEvent(action: Int, x: Float, y: Float, view: View) {
        val normalizedX = (x / view.width).coerceIn(0f, 1f)
        val normalizedY = (y / view.height).coerceIn(0f, 1f)
        touchSender?.send(action, normalizedX, normalizedY)
    }

    private fun setupButtons() {
        btnClose?.setOnClickListener { stopSelf() }

        btnResize?.setOnTouchListener { _, event ->
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
                    val ratio = decoder?.let { it.videoWidth.toFloat() / it.videoHeight.toFloat() } ?: (16f / 9f)
                    val newH = (newW / ratio).toInt()
                    lp.width = newW
                    lp.height = newH
                    windowManager!!.updateViewLayout(floatView, lp)
                }
                MotionEvent.ACTION_UP -> {
                    isResizing = false
                }
            }
            true
        }
    }

    private fun connectToPhone(host: String, port: Int) {
        connection = ConnectionClient(host, port)
        decoder = VideoDecoder(surfaceView!!)
        touchSender = TouchSender(connection!!)

        connection!!.onVideoCodecReceived = { codecInfo ->
            decoder!!.configure(codecInfo.width, codecInfo.height)
        }
        connection!!.onVideoFrameReceived = { data ->
            decoder!!.feedData(data)
        }

        Thread({ connection!!.connect() }, "ConnectionClient").start()
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
        startForeground(2, n)
    }

    override fun onDestroy() {
        connection?.disconnect()
        decoder?.release()
        floatView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }
}
