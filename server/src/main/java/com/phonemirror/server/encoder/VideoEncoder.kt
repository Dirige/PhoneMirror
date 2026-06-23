package com.phonemirror.server.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.phonemirror.common.VideoCodecInfo
import java.nio.ByteBuffer

class VideoEncoder(private val codecInfo: VideoCodecInfo) {
    private var encoder: MediaCodec? = null
    var onFrameEncoded: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    private var isRunning = false

    fun start(): Surface {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, codecInfo.width, codecInfo.height
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, codecInfo.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, codecInfo.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        val inputSurface = encoder!!.createInputSurface()
        encoder!!.start()
        isRunning = true
        Thread({ drainLoop() }, "Encoder-Drain").start()
        return inputSurface
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        val enc = encoder ?: return
        while (isRunning) {
            val idx = enc.dequeueOutputBuffer(bufferInfo, 10000)
            if (idx >= 0) {
                val buf = enc.getOutputBuffer(idx)
                if (buf != null && bufferInfo.size > 0) {
                    buf.position(bufferInfo.offset)
                    buf.limit(bufferInfo.offset + bufferInfo.size)
                    // FIX: Send ALL frames including codec config (SPS/PPS)
                    onFrameEncoded?.invoke(buf, bufferInfo)
                }
                enc.releaseOutputBuffer(idx, false)
            }
        }
    }

    fun stop() {
        isRunning = false
        try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
        encoder = null
    }
}
