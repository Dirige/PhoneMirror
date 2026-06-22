package com.phonemirror.client.network

import com.phonemirror.common.Protocol
import com.phonemirror.common.TouchEvent
import java.util.concurrent.LinkedBlockingQueue

class TouchSender(private val connection: ConnectionClient) {
    private val queue = LinkedBlockingQueue<TouchEvent>(256)

    init {
        Thread({
            while (true) {
                try {
                    val event = queue.take()
                    connection.sendTouchEvent(event)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "TouchSender").start()
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
