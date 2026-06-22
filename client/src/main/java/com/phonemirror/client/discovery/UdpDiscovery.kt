package com.phonemirror.client.discovery

import android.content.Context
import com.phonemirror.common.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val port: Int
)

object UdpDiscovery {
    private val executor = Executors.newSingleThreadExecutor()

    fun scan(context: Context, callback: (List<DiscoveredDevice>) -> Unit) {
        executor.execute {
            val devices = mutableListOf<DiscoveredDevice>()
            try {
                val socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 3000
                }
                val data = Protocol.DISCOVERY_MAGIC.toByteArray()
                val addresses = getBroadcastAddresses()

                for (addr in addresses) {
                    val packet = DatagramPacket(data, data.size, addr, Protocol.DISCOVERY_PORT)
                    socket.send(packet)
                }

                val buf = ByteArray(256)
                val deadline = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val response = DatagramPacket(buf, buf.size)
                        socket.receive(response)
                        val msg = String(response.data, 0, response.length)
                        if (msg.startsWith(Protocol.DISCOVERY_RESPONSE)) {
                            val parts = msg.split(":")
                            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 7201 else 7201
                            devices.add(DiscoveredDevice(
                                name = response.address.hostName ?: "未知设备",
                                ip = response.address.hostAddress ?: "",
                                port = port
                            ))
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            callback(devices)
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                for (addr in ni.interfaceAddresses) {
                    val broadcast = addr.broadcast
                    if (broadcast != null) {
                        addresses.add(broadcast)
                    }
                }
            }
        } catch (_: Exception) {}
        if (addresses.isEmpty()) {
            addresses.add(InetAddress.getByName("255.255.255.255"))
        }
        return addresses
    }
}
