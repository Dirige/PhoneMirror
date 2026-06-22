package com.phonemirror.client.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class VideoDecoder(private val surfaceView: SurfaceView) {
    private var decoder: MediaCodec? = null
    var videoWidth = 1920
    var videoHeight = 1080
    private var pendingConfig = false
    private var pendingWidth = 0
    private var pendingHeight = 0

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (pendingConfig) {
                    configureInternal(pendingWidth, pendingHeight)
                    pendingConfig = false
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                release()
            }
        })
    }

    fun configure(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        if (surfaceView.holder.surface != null && surfaceView.holder.surface.isValid) {
            configureInternal(width, height)
        } else {
            pendingConfig = true
            pendingWidth = width
            pendingHeight = height
        }
    }

    private fun configureInternal(width: Int, height: Int) {
        release()
        try {
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, width, height
            )
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, surfaceView.holder.surface, null, 0)
                start()
            }
            Log.i("VideoDecoder", "Configured: ${width}x${height}")
        } catch (e: Exception) {
            Log.e("VideoDecoder", "Configure failed", e)
        }
    }

    fun feedData(data: ByteArray) {
        val dec = decoder ?: return
        try {
            val inputIdx = dec.dequeueInputBuffer(10000)
            if (inputIdx >= 0) {
                val inputBuf = dec.getInputBuffer(inputIdx) ?: return
                inputBuf.clear()
                inputBuf.put(data)
                dec.queueInputBuffer(inputIdx, 0, data.size, System.nanoTime() / 1000, 0)
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIdx = dec.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputIdx >= 0) {
                dec.releaseOutputBuffer(outputIdx, true)
                outputIdx = dec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.e("VideoDecoder", "Decode error", e)
        }
    }

    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null
    }
}
