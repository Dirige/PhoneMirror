package com.phonemirror.server.network

import android.util.Log
import com.phonemirror.common.*
import com.phonemirror.server.input.RemoteInputHandler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class ConnectionServer(
    private val port: Int,
    private val codecInfo: VideoCodecInfo
) {
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<ClientHandler>()
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false

    fun start() {
        running = true
        Thread({
            try {
                serverSocket = ServerSocket(port)
                Log.i("ConnServer", "Listening on port $port")
                while (running) {
                    val socket = serverSocket!!.accept()
                    val handler = ClientHandler(socket)
                    clients.add(handler)
                    executor.submit { handler.run() }
                }
            } catch (e: Exception) {
                if (running) Log.e("ConnServer", "Server error", e)
            }
        }, "ConnServer").start()
    }

    fun broadcastVideoFrame(buffer: ByteBuffer, info: android.media.MediaCodec.BufferInfo) {
        val data = ByteArray(info.size)
        buffer.get(data)
        val iter = clients.iterator()
        while (iter.hasNext()) {
            val client = iter.next()
            try { client.sendVideoFrame(data) } catch (_: Exception) { clients.remove(client) }
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        clients.forEach { it.close() }
        clients.clear()
        executor.shutdownNow()
    }

    inner class ClientHandler(private val socket: Socket) {
        private val output = DataOutputStream(socket.getOutputStream())
        private val input = DataInputStream(socket.getInputStream())
        @Volatile var closed = false

        fun run() {
            try {
                val devInfo = DeviceInfo(
                    android.os.Build.MODEL,
                    codecInfo.width, codecInfo.height
                )
                writeMessage(output, Protocol.MSG_DEVICE_INFO, devInfo.serialize())
                writeMessage(output, Protocol.MSG_VIDEO_CODEC, codecInfo.serialize())
                Log.i("ConnServer", "Client connected: ${socket.inetAddress}")

                while (!closed) {
                    val (type, payload) = readMessage(input)
                    when (type) {
                        Protocol.MSG_TOUCH_EVENT -> {
                            val touch = TouchEvent.deserialize(payload)
                            RemoteInputHandler.dispatchTouch(touch)
                        }
                        Protocol.MSG_KEEPALIVE -> {
                            writeMessage(output, Protocol.MSG_KEEPALIVE, ByteArray(0))
                        }
                    }
                }
            } catch (e: Exception) {
                if (!closed) Log.w("ConnServer", "Client disconnected", e)
            } finally { close() }
        }

        fun sendVideoFrame(data: ByteArray) {
            writeMessage(output, Protocol.MSG_VIDEO_FRAME, data)
        }

        fun close() {
            closed = true
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
