package com.phonemirror.server

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.NetworkInterface
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
        } else {
            Toast.makeText(this, "投屏授权被拒绝", Toast.LENGTH_SHORT).show()
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
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
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
                Toast.makeText(this, "部分权限被拒绝，可能影响投屏功能", Toast.LENGTH_LONG).show()
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
