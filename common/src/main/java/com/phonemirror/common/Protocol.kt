package com.phonemirror.common

import java.io.DataInputStream
import java.io.DataOutputStream

object Protocol {
    const val DISCOVERY_PORT = 7200
    const val DEFAULT_STREAM_PORT = 7201
    const val DISCOVERY_MAGIC = "PHONEMIRROR_DISCOVER"
    const val DISCOVERY_RESPONSE = "PHONEMIRROR_HERE"
    const val MSG_VIDEO_CODEC = 1
    const val MSG_VIDEO_FRAME = 2
    const val MSG_TOUCH_EVENT = 3
    const val MSG_DEVICE_INFO = 4
    const val MSG_KEEPALIVE = 5
    const val TOUCH_DOWN = 0
    const val TOUCH_UP = 1
    const val TOUCH_MOVE = 2
}

data class VideoCodecInfo(
    val width: Int,
    val height: Int,
    val fps: Int = 30,
    val bitrate: Int = 4000000
) {
    fun serialize(): ByteArray {
        val buf = ByteArray(16)
        var o = 0
        putInt(buf, o, width); o += 4
        putInt(buf, o, height); o += 4
        putInt(buf, o, fps); o += 4
        putInt(buf, o, bitrate)
        return buf
    }
    companion object {
        fun deserialize(data: ByteArray): VideoCodecInfo {
            var o = 0
            val w = getInt(data, o); o += 4
            val h = getInt(data, o); o += 4
            val f = getInt(data, o); o += 4
            val b = getInt(data, o)
            return VideoCodecInfo(w, h, f, b)
        }
    }
}

data class TouchEvent(
    val action: Int,
    val x: Float,
    val y: Float,
    val pointerId: Int = 0
) {
    fun serialize(): ByteArray {
        val buf = ByteArray(16)
        var o = 0
        putInt(buf, o, action); o += 4
        putFloat(buf, o, x); o += 4
        putFloat(buf, o, y); o += 4
        putInt(buf, o, pointerId)
        return buf
    }
    companion object {
        fun deserialize(data: ByteArray): TouchEvent {
            var o = 0
            val a = getInt(data, o); o += 4
            val x = getFloat(data, o); o += 4
            val y = getFloat(data, o); o += 4
            val p = getInt(data, o)
            return TouchEvent(a, x, y, p)
        }
    }
}

data class DeviceInfo(
    val name: String,
    val screenWidth: Int,
    val screenHeight: Int
) {
    fun serialize(): ByteArray {
        val nb = name.toByteArray(Charsets.UTF_8)
        val buf = ByteArray(12 + nb.size)
        var o = 0
        putInt(buf, o, nb.size); o += 4
        System.arraycopy(nb, 0, buf, o, nb.size); o += nb.size
        putInt(buf, o, screenWidth); o += 4
        putInt(buf, o, screenHeight)
        return buf
    }
    companion object {
        fun deserialize(data: ByteArray): DeviceInfo {
            var o = 0
            val nl = getInt(data, o); o += 4
            val n = String(data, o, nl, Charsets.UTF_8); o += nl
            val sw = getInt(data, o); o += 4
            val sh = getInt(data, o)
            return DeviceInfo(n, sw, sh)
        }
    }
}

fun writeMessage(out: DataOutputStream, type: Int, payload: ByteArray) {
    out.writeByte(type)
    out.writeInt(payload.size)
    out.write(payload)
    out.flush()
}

fun readMessage(input: DataInputStream): Pair<Int, ByteArray> {
    val type = input.readUnsignedByte()
    val len = input.readInt()
    val payload = ByteArray(len)
    input.readFully(payload)
    return Pair(type, payload)
}

private fun putInt(b: ByteArray, o: Int, v: Int) {
    b[o] = (v shr 24).toByte(); b[o+1] = (v shr 16).toByte()
    b[o+2] = (v shr 8).toByte(); b[o+3] = v.toByte()
}
private fun getInt(b: ByteArray, o: Int): Int {
    return (b[o].toInt() and 0xFF shl 24) or (b[o+1].toInt() and 0xFF shl 16) or
           (b[o+2].toInt() and 0xFF shl 8) or (b[o+3].toInt() and 0xFF)
}
private fun putFloat(b: ByteArray, o: Int, v: Float) = putInt(b, o, java.lang.Float.floatToIntBits(v))
private fun getFloat(b: ByteArray, o: Int): Float = java.lang.Float.intBitsToFloat(getInt(b, o))
