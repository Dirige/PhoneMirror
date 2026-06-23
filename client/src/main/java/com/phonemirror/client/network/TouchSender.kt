package com.phonemirror.client.network

import com.phonemirror.common.Protocol
import com.phonemirror.common.TouchEvent
import java.util.concurrent.LinkedBlockingQueue

class TouchSender(private val connection: ConnectionClient) {
    private val queue = LinkedBlockingQueue<TouchEvent>(256)
    @Volatile private var running = true
    private val thread: Thread

    init {
        thread = Thread({
            while (running) {
                try {
                    val event = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                    if (event != null && connection.connected) {
                        connection.sendTouchEvent(event)
                    }
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "TouchSender").apply {
            isDaemon = true
            start()
        }
    }

    fun shutdown() {
        running = false
        thread.interrupt()
    }

    fun send(action: Int, x: Float, y: Float) {
        val event = TouchEvent(action, x, y)
        if (queue.remainingCapacity() > 0) {
            queue.offer(event)
        } else {
            queue.poll()
            queue.offer(event)
        }
    }
}
