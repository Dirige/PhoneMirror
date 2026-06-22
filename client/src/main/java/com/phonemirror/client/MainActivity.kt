package com.phonemirror.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phonemirror.client.discovery.UdpDiscovery
import com.phonemirror.client.service.FloatingWindowService

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvDevices: TextView

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
        tvDevices = findViewById(R.id.tv_devices)

        etHost.setText("192.168.")
        etPort.setText("7201")

        btnConnect.setOnClickListener { connectToPhone() }
        btnScan.setOnClickListener { scanDevices() }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        tvStatus.text = "已连接"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun scanDevices() {
        tvDevices.text = "扫描中..."
        UdpDiscovery.scan(this) { devices ->
            runOnUiThread {
                if (devices.isEmpty()) {
                    tvDevices.text = "未发现设备，请确认手机端已开启投屏"
                } else {
                    tvDevices.text = devices.joinToString("
") { "  " + it.name + " (" + it.ip + ")" }
                    if (devices.isNotEmpty()) {
                        etHost.setText(devices[0].ip)
                        etPort.setText(devices[0].port.toString())
                    }
                }
            }
        }
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
