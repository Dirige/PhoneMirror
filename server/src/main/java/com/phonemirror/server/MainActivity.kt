package com.phonemirror.server

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import java.net.NetworkInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.phonemirror.common.Protocol
import com.phonemirror.server.screen.ScreenCaptureService
import com.phonemirror.server.util.FileLogger
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private var serverRunning = false
    private lateinit var btnToggle: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvDiscoveryPort: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnCopyIp: MaterialButton

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        FileLogger.log("MainActivity", "授权回调: resultCode=${result.resultCode}, hasData=${result.data != null}")
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 使用静态变量传递，避免 Intent 序列化问题
            ScreenCaptureService.projectionResultCode = result.resultCode
            ScreenCaptureService.projectionData = result.data

            val intent = Intent(this, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // 延迟检查服务是否真正启动
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!ScreenCaptureService.isRunning) {
                    serverRunning = false
                    updateUI()
                    Toast.makeText(this, "投屏服务启动失败，请查看日志文件", Toast.LENGTH_LONG).show()
                    FileLogger.error("MainActivity", "服务未运行")
                } else {
                    serverRunning = true
                    updateUI()
                    Toast.makeText(this, "投屏服务已启动", Toast.LENGTH_SHORT).show()
                    FileLogger.log("MainActivity", "投屏服务已启动")
                }
            }, 2000)
        } else {
            FileLogger.error("MainActivity", "授权被拒绝或data为null")
            Toast.makeText(this, "投屏授权被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化文件日志
        FileLogger.init(this)
        FileLogger.log("MainActivity", "App 启动")

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

        // 请求运行时权限
        requestPermissions()

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

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                FileLogger.log("MainActivity", "需要请求通知权限")
            }
        }

        if (permissions.isNotEmpty()) {
            FileLogger.log("MainActivity", "请求权限: ${permissions.joinToString()}")
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            FileLogger.log("MainActivity", "所有权限已授予")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isNotEmpty()) {
                FileLogger.error("MainActivity", "权限被拒绝: ${deniedPermissions.joinToString()}")
                Toast.makeText(this, "部分权限被拒绝，可能影响投屏功能", Toast.LENGTH_LONG).show()
            } else {
                FileLogger.log("MainActivity", "所有权限已授予")
            }
        }
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
        FileLogger.log("MainActivity", "开始启动屏幕录制")
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
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var mobileIp: String? = null

            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue

                val name = ni.name.lowercase()
                val addresses = ni.interfaceAddresses
                for (addr in addresses) {
                    val inetAddr = addr.address
                    if (inetAddr.isLoopbackAddress || inetAddr is java.net.Inet6Address) continue
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

            return mobileIp ?: "未知"
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
