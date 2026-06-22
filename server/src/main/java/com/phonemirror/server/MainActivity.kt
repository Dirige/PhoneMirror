package com.phonemirror.server

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
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
            try {
                // 使用静态变量传递，避免 Intent 序列化问题
                ScreenCaptureService.projectionResultCode = result.resultCode
                ScreenCaptureService.projectionData = result.data
                
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("port", Protocol.DEFAULT_STREAM_PORT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                serverRunning = true
                updateUI()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting service", e)
                Toast.makeText(this, "启动投屏服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                Toast.makeText(this, "无法获取屏幕录制服务", Toast.LENGTH_LONG).show()
                return
            }
            
            val intent = projectionManager.createScreenCaptureIntent()
            if (intent == null) {
                Toast.makeText(this, "无法创建屏幕录制请求", Toast.LENGTH_LONG).show()
                return
            }
            
            projectionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "启动屏幕录制失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        serverRunning = false
        updateUI()
    }

    /**
     * 获取本地 IP 地址，优先返回热点 IP
     */
    private fun getLocalIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var mobileIp: String? = null
            
            // 遍历所有网络接口
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                // 跳过回环和未启用的接口
                if (ni.isLoopback || !ni.isUp) continue
                
                val name = ni.name.lowercase()
                val addresses = ni.interfaceAddresses
                for (addr in addresses) {
                    val inetAddr = addr.address
                    // 跳过 IPv6 和回环地址
                    if (inetAddr.isLoopbackAddress || inetAddr is Inet6Address) continue
                    val ip = inetAddr.hostAddress ?: continue
                    // 过滤掉 127.x.x.x
                    if (ip.startsWith("127.")) continue
                    
                    // 优先返回热点相关的接口 IP
                    // wlan0, ap0, swlan0 等通常是热点接口
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) {
                        return ip
                    }
                    
                    // 记录移动网络的 IP 作为备选
                    if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")) {
                        if (mobileIp == null) {
                            mobileIp = ip
                        }
                    }
                }
            }
            
            // 如果没有热点 IP，返回移动网络 IP
            mobileIp ?: "未知"
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
