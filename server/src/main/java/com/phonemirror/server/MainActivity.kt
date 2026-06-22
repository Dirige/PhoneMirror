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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.phonemirror.common.Protocol
import com.phonemirror.server.screen.ScreenCaptureService
import java.net.Inet6Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private var serverRunning = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var etDiscoveryPort: EditText

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "projectionLauncher 回调: resultCode=${result.resultCode}, data=${result.data}")
        
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                if (result.data != null) {
                    try {
                        // 使用静态变量传递，避免 Intent 序列化问题
                        ScreenCaptureService.projectionResultCode = result.resultCode
                        ScreenCaptureService.projectionData = result.data

                        // 读取用户自定义端口
                        val port = etPort.text.toString().toIntOrNull() ?: Protocol.DEFAULT_STREAM_PORT

                        val intent = Intent(this, ScreenCaptureService::class.java).apply {
                            putExtra("port", port)
                        }
                        
                        Log.d("MainActivity", "准备启动前台服务，端口: $port")
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        serverRunning = true
                        updateUI()
                        Toast.makeText(this, "投屏服务已启动", Toast.LENGTH_SHORT).show()
                        Log.d("MainActivity", "投屏服务已启动")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "启动服务失败", e)
                        Toast.makeText(this, "启动投屏服务失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("MainActivity", "授权成功但 data 为 null")
                    Toast.makeText(this, "授权数据异常", Toast.LENGTH_LONG).show()
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.w("MainActivity", "用户取消了授权")
                Toast.makeText(this, "授权已取消", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.e("MainActivity", "授权失败，resultCode: ${result.resultCode}")
                Toast.makeText(this, "授权失败 (code: ${result.resultCode})", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btn_toggle)
        tvStatus = findViewById(R.id.tv_status)
        etIp = findViewById(R.id.et_ip)
        etPort = findViewById(R.id.et_port)
        etDiscoveryPort = findViewById(R.id.et_discovery_port)

        // 自动填充 IP 和端口
        etIp.setText(getLocalIp())
        etPort.setText(Protocol.DEFAULT_STREAM_PORT.toString())
        etDiscoveryPort.setText(Protocol.DISCOVERY_PORT.toString())

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
        // 如果 IP 为空或未知，自动刷新
        val currentIp = etIp.text.toString()
        if (currentIp.isEmpty() || currentIp == "未知") {
            etIp.setText(getLocalIp())
        }
    }

    private fun updateUI() {
        if (serverRunning) {
            tvStatus.text = "● 投屏中"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnToggle.text = "停止投屏"
        } else {
            tvStatus.text = "● 未启动"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            btnToggle.text = "开始投屏"
        }
    }

    private fun startScreenCapture() {
        try {
            Log.d("MainActivity", "开始启动屏幕录制...")
            Toast.makeText(this, "正在启动屏幕录制...", Toast.LENGTH_SHORT).show()

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                Log.e("MainActivity", "无法获取 MediaProjectionManager")
                Toast.makeText(this, "无法获取屏幕录制服务", Toast.LENGTH_LONG).show()
                return
            }

            Log.d("MainActivity", "成功获取 MediaProjectionManager")

            val intent = projectionManager.createScreenCaptureIntent()
            if (intent == null) {
                Log.e("MainActivity", "createScreenCaptureIntent() 返回 null")
                Toast.makeText(this, "无法创建屏幕录制请求", Toast.LENGTH_LONG).show()
                return
            }

            Log.d("MainActivity", "成功创建屏幕录制 Intent，准备启动授权界面...")
            Toast.makeText(this, "请选择'整个屏幕'并授权", Toast.LENGTH_LONG).show()

            projectionLauncher.launch(intent)

            Log.d("MainActivity", "已启动授权 Activity")
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

            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue

                val name = ni.name.lowercase()
                val addresses = ni.interfaceAddresses
                for (addr in addresses) {
                    val inetAddr = addr.address
                    if (inetAddr.isLoopbackAddress || inetAddr is Inet6Address) continue
                    val ip = inetAddr.hostAddress ?: continue
                    if (ip.startsWith("127.")) continue

                    // 优先返回热点相关的接口 IP
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

            mobileIp ?: "未知"
        } catch (e: Exception) {
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
