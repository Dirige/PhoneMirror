package com.phonemirror.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.phonemirror.client.discovery.UdpDiscovery
import com.phonemirror.client.service.FloatingWindowService

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnScan: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionInfo: TextView
    private lateinit var viewStatusDot: View
    private lateinit var cardDevices: MaterialCardView
    private lateinit var llDevices: LinearLayout

    private var isConnected = false
    private var isScanning = false

    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.et_host)
        etPort = findViewById(R.id.et_port)
        btnConnect = findViewById(R.id.btn_connect)
        btnScan = findViewById(R.id.btn_scan)
        tvStatus = findViewById(R.id.tv_status)
        tvConnectionInfo = findViewById(R.id.tv_connection_info)
        viewStatusDot = findViewById(R.id.view_status_dot)
        cardDevices = findViewById(R.id.card_devices)
        llDevices = findViewById(R.id.ll_devices)

        etHost.setText("192.168.")
        etPort.setText("7201")

        btnConnect.setOnClickListener { connectToPhone() }
        btnScan.setOnClickListener { scanDevices() }

        // Entry animation
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 800
        findViewById<View>(R.id.iv_logo)?.startAnimation(fadeIn)

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        }
    }

    private fun connectToPhone() {
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().trim().toIntOrNull() ?: 7201
        if (host.isEmpty()) {
            Toast.makeText(this, "请输入手机IP地址", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            return
        }

        startFloatingService(host, port)
    }

    private fun startFloatingService(host: String, port: Int) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra("host", host)
            putExtra("port", port)
        }
        startService(intent)

        updateConnectionState(true, host, port)
    }

    private fun updateConnectionState(connected: Boolean, host: String? = null, port: Int? = null) {
        isConnected = connected
        if (connected) {
            tvStatus.text = "已连接"
            tvStatus.setTextColor(0xFF00E676.toInt())
            tvConnectionInfo.text = "${host}:${port}"
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online)
            btnConnect.text = "断开连接"
            btnConnect.setBackgroundColor(0xFFFF5252.toInt())
        } else {
            tvStatus.text = "未连接"
            tvStatus.setTextColor(0xFF78909C.toInt())
            tvConnectionInfo.text = ""
            viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline)
            btnConnect.text = "连接投屏"
            btnConnect.setBackgroundColor(0xFF00E5FF.toInt())
        }
    }

    private fun scanDevices() {
        if (isScanning) return
        isScanning = true
        btnScan.text = "扫描中..."
        btnScan.isEnabled = false

        cardDevices.visibility = View.VISIBLE
        llDevices.removeAllViews()

        // Add scanning indicator
        val scanText = TextView(this).apply {
            text = "正在扫描局域网设备..."
            setTextColor(0xFF78909C.toInt())
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        llDevices.addView(scanText)

        UdpDiscovery.scan(this) { devices ->
            runOnUiThread {
                isScanning = false
                btnScan.text = "扫描局域网设备"
                btnScan.isEnabled = true
                llDevices.removeAllViews()

                if (devices.isEmpty()) {
                    val noDevice = TextView(this).apply {
                        text = "未发现设备"
                        setTextColor(0xFF78909C.toInt())
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    llDevices.addView(noDevice)
                } else {
                    for (device in devices) {
                        val deviceView = createDeviceView(device)
                        llDevices.addView(deviceView)
                    }
                }
            }
        }
    }

    private fun createDeviceView(device: com.phonemirror.client.discovery.DiscoveredDevice): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = "📱"
            textSize = 20f
            setPadding(0, 0, 16, 0)
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val name = TextView(this).apply {
            text = device.name
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
        }

        val ip = TextView(this).apply {
            text = device.ip
            setTextColor(0xFF78909C.toInt())
            textSize = 12f
        }

        info.addView(name)
        info.addView(ip)
        container.addView(icon)
        container.addView(info)

        container.setOnClickListener {
            etHost.setText(device.ip)
            cardDevices.visibility = View.GONE
        }

        return container
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                connectToPhone()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能投屏", Toast.LENGTH_LONG).show()
            }
        }
    }
}
