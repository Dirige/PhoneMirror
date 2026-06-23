package com.phonemirror.server

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.phonemirror.common.Protocol
import com.phonemirror.server.screen.ScreenCaptureService

class MainActivity : AppCompatActivity() {

    private var serverRunning = false
    private lateinit var btnToggle: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvDiscoveryPort: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnCopyIp: MaterialButton

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data as android.os.Parcelable)
                putExtra("port", Protocol.DEFAULT_STREAM_PORT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            serverRunning = true
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btn_toggle)
        tvStatus = findViewById(R.id.tv_status)
        tvIp = findViewById(R.id.tv_ip)
        tvPort = findViewById(R.id.tv_port)
        tvDiscoveryPort = findViewById(R.id.tv_discovery_port)
        viewStatusDot = findViewById(R.id.view_status_dot)
        btnCopyIp = findViewById(R.id.btn_copy_ip)

        tvIp.text = getLocalIp()
        tvPort.text = Protocol.DEFAULT_STREAM_PORT.toString()
        tvDiscoveryPort.text = Protocol.DISCOVERY_PORT.toString()

        btnToggle.setOnClickListener {
            if (!serverRunning) {
                startScreenCapture()
            } else {
                stopScreenCapture()
            }
        }

        btnCopyIp.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("IP", tvIp.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "IP已复制", Toast.LENGTH_SHORT).show()
        }

        // Entry animation
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 800
        findViewById<View>(R.id.iv_logo)?.startAnimation(fadeIn)
    }

    private fun updateUI() {
        if (serverRunning) {
            tvStatus.text = "投屏中"
            tvStatus.setTextColor(0xFF00E676.toInt())
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online)
            btnToggle.text = "停止投屏"
            btnToggle.setIconResource(R.drawable.ic_stop)
            btnToggle.setBackgroundColor(0xFFFF5252.toInt())
        } else {
            tvStatus.text = "未启动"
            tvStatus.setTextColor(0xFF78909C.toInt())
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline)
            btnToggle.text = "开始投屏"
            btnToggle.setIconResource(R.drawable.ic_play)
            btnToggle.setBackgroundColor(0xFF00E5FF.toInt())
        }
    }

    private fun startScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        serverRunning = false
        updateUI()
    }

    private fun getLocalIp(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            return Formatter.formatIpAddress(ip)
        } catch (e: Exception) {
            return "未知"
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if service is running
        // This is a simplified check
    }
}
