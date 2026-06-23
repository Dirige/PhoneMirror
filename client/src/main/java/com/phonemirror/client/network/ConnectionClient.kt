package com.phonemirror.client.network

import android.util.Log
import com.phonemirror.common.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionClient(private val host: String, private val port: Int) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    @Volatile var connected = false
        private set

    var onVideoCodecReceived: ((VideoCodecInfo) -> Unit)? = null
    var onVideoCodecConfigReceived: ((ByteArray) -> Unit)? = null
    var onVideoFrameReceived: ((ByteArray) -> Unit)? = null
    var onDeviceInfoReceived: ((DeviceInfo) -> Unit)? = null

    fun connect() {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 5000)
                tcpNoDelay = true
                sendBufferSize = 256 * 1024
                soTimeout = 60000 // 60s read timeout to detect dead connections
            }
            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())
            connected = true
            Log.i("ConnClient", "Connected to $host:$port")

            while (connected) {
                val (type, payload) = readMessage(input!!)
                when (type) {
                    Protocol.MSG_VIDEO_CODEC -> {
                        val info = VideoCodecInfo.deserialize(payload)
                        onVideoCodecReceived?.invoke(info)
                    }
                    Protocol.MSG_VIDEO_FRAME -> {
                        // Check if this is a codec config frame (SPS/PPS)
                        if (payload.size > 4 && payload[0] == 0.toByte() && payload[1] == 0.toByte() 
                            && payload[2] == 0.toByte() && payload[3] == 1.toByte()) {
                            // Check NAL unit type - SPS is type 7, PPS is type 8
                            val nalType = payload[4].toInt() and 0x1F
                            if (nalType == 7 || nalType == 8) {
                                onVideoCodecConfigReceived?.invoke(payload)
                            } else {
                                onVideoFrameReceived?.invoke(payload)
                            }
                        } else {
                            onVideoFrameReceived?.invoke(payload)
                        }
                    }
                    Protocol.MSG_DEVICE_INFO -> {
                        val info = DeviceInfo.deserialize(payload)
                        onDeviceInfoReceived?.invoke(info)
                    }
                    Protocol.MSG_KEEPALIVE -> { /* ok */ }
                }
            }
        } catch (e: Exception) {
            if (connected) Log.e("ConnClient", "Connection error", e)
            connected = false
        }
    }

    fun sendTouchEvent(event: com.phonemirror.common.TouchEvent) {
        try {
            if (connected && output != null) {
                writeMessage(output!!, Protocol.MSG_TOUCH_EVENT, event.serialize())
            }
        } catch (e: Exception) {
            Log.e("ConnClient", "Send touch error", e)
        }
    }

    fun sendKeepAlive() {
        try {
            if (connected && output != null) {
                writeMessage(output!!, Protocol.MSG_KEEPALIVE, ByteArray(0))
            }
        } catch (_: Exception) {}
    }

    fun disconnect() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
    }
}
