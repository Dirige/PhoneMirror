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
    var onVideoFrameReceived: ((ByteArray) -> Unit)? = null
    var onDeviceInfoReceived: ((DeviceInfo) -> Unit)? = null

    fun connect() {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 5000)
                tcpNoDelay = true
                sendBufferSize = 256 * 1024
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
                        onVideoFrameReceived?.invoke(payload)
                    }
                    Protocol.MSG_DEVICE_INFO -> {
                        val info = DeviceInfo.deserialize(payload)
                        onDeviceInfoReceived?.invoke(info)
                    }
                    Protocol.MSG_KEEPALIVE -> { /* ok */ }
                }
            }
        } catch (e: Exception) {
            connected = false
            Log.e("ConnClient", "Connection error", e)
            throw e  // 必须抛出，让调用方感知连接失败
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
