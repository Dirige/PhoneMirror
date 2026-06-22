package com.phonemirror.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.phonemirror.common.Protocol
import com.phonemirror.server.screen.ScreenCaptureService

class MainActivity : AppCompatActivity() {

    private var serverRunning = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvDiscoveryPort: TextView

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
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
    }

    private fun updateUI() {
        if (serverRunning) {
            tvStatus.text = "投屏中"
            btnToggle.text = "停止投屏"
        } else {
            tvStatus.text = "未启动"
            btnToggle.text = "开始投屏"
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

    @Suppress("DEPRECATION")
    private fun getLocalIp(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            Formatter.formatIpAddress(ip)
        } catch (e: Exception) {
            "未知"
        }
    }
}
