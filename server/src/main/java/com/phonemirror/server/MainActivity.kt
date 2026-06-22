package com.phonemirror.server

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonemirror.common.Protocol
import com.phonemirror.server.network.ConnectionServer
import com.phonemirror.server.screen.ScreenCaptureService

class MainActivity : ComponentActivity() {

    private var serverRunning = mutableStateOf(false)
    private var clientCount = mutableStateOf(0)
    private var localIp = mutableStateOf("获取中...")

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
            serverRunning.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localIp.value = getLocalIp()
        setContent { PhoneMirrorTheme { MainScreen() } }
    }

    @Composable
    fun PhoneMirrorTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF00E5FF),
                onPrimary = Color(0xFF003544),
                primaryContainer = Color(0xFF004D61),
                secondary = Color(0xFF4FC3F7),
                surface = Color(0xFF0A1929),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF132F4C),
                background = Color(0xFF0A1929),
                onBackground = Color(0xFFE0E0E0)
            ),
            content = content
        )
    }

    @Composable
    fun MainScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A1929),
                            Color(0xFF0D2137),
                            Color(0xFF132F4C)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "手机镜像",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "手机端",
                    fontSize = 14.sp,
                    color = Color(0xFF80CBC4),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(48.dp))
                StatusCard()
                Spacer(modifier = Modifier.height(32.dp))
                IpInfoCard()
                Spacer(modifier = Modifier.weight(1f))
                ControlButton()
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    @Composable
    fun StatusCard() {
        val isRunning by serverRunning
        val clients by clientCount
        val statusColor by animateColorAsState(
            if (isRunning) Color(0xFF00E676) else Color(0xFF78909C),
            label = "status"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132F4C))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = if (isRunning) pulseAlpha else 1f))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isRunning) "投屏中" else "未启动",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    if (isRunning) {
                        Text(
                            text = "已连接设备: $clients",
                            fontSize = 13.sp,
                            color = Color(0xFF80CBC4),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun IpInfoCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132F4C).copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi, contentDescription = null,
                        tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接信息", color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("IP 地址", localIp.value)
                InfoRow("端口", Protocol.DEFAULT_STREAM_PORT.toString())
                InfoRow("发现端口", Protocol.DISCOVERY_PORT.toString())
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(label, color = Color(0xFF78909C), fontSize = 13.sp, modifier = Modifier.width(80.dp))
            Text(value, color = Color(0xFFE0E0E0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun ControlButton() {
        val isRunning by serverRunning
        Button(
            onClick = {
                if (!isRunning) {
                    startScreenCapture()
                } else {
                    stopScreenCapture()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFEF5350) else Color(0xFF00E5FF)
            )
        ) {
            Icon(
                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isRunning) "停止投屏" else "开始投屏",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    private fun startScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        serverRunning.value = false
        clientCount.value = 0
    }

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
