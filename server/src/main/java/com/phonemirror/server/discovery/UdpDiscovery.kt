package com.phonemirror.server.discovery

import android.util.Log
import com.phonemirror.common.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpDiscovery(private val streamPort: Int) {
    private var socket: DatagramSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        Thread({
            try {
                socket = DatagramSocket(Protocol.DISCOVERY_PORT)
                val buf = ByteArray(256)
                Log.i("UdpDiscovery", "Listening on port ${Protocol.DISCOVERY_PORT}")
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket!!.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    if (msg == Protocol.DISCOVERY_MAGIC) {
                        val response = Protocol.DISCOVERY_RESPONSE + ":" + streamPort
                        val respData = response.toByteArray()
                        socket!!.send(DatagramPacket(
                            respData, respData.size,
                            packet.address, packet.port
                        ))
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e("UdpDiscovery", "Error", e)
            }
        }, "UdpDiscovery").start()
    }

    fun stop() {
        running = false
        socket?.close()
    }
}
