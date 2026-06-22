package com.phonemirror.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.phonemirror.common.Protocol
import com.phonemirror.server.screen.ScreenCaptureService
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

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

    override fun onResume() {
        super.onResume()
        // 每次回到前台刷新 IP
        tvIp.text = getLocalIp()
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

    /**
     * 获取本地 IP 地址，支持 WiFi 连接和热点模式
     */
    private fun getLocalIp(): String {
        return try {
            // 优先通过 NetworkInterface 获取（支持热点模式）
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                // 跳过回环和未启用的接口
                if (ni.isLoopback || !ni.isUp) continue
                val addresses = ni.interfaceAddresses
                for (addr in addresses) {
                    val inetAddr = addr.address
                    // 跳过 IPv6 和回环地址
                    if (inetAddr.isLoopbackAddress || inetAddr is Inet6Address) continue
                    val ip = inetAddr.hostAddress ?: continue
                    // 过滤掉 127.x.x.x
                    if (ip.startsWith("127.")) continue
                    return ip
                }
            }
            "未知"
        } catch (e: Exception) {
            // 降级：尝试 WiFi 方式
            tryGetWifiIp()
        }
    }

    @Suppress("DEPRECATION")
    private fun tryGetWifiIp(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip == 0) "未知" else Formatter.formatIpAddress(ip)
        } catch (e: Exception) {
            "未知"
        }
    }
}
