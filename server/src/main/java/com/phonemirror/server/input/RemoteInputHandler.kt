package com.phonemirror.server.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import com.phonemirror.common.Protocol
import com.phonemirror.common.TouchEvent

object RemoteInputHandler {
    private var accessibilityService: AccessibilityService? = null
    private var screenWidth = 1080
    private var screenHeight = 1920

    fun setService(service: AccessibilityService) {
        accessibilityService = service
    }

    fun setScreenSize(w: Int, h: Int) {
        screenWidth = w
        screenHeight = h
    }

    fun dispatchTouch(event: TouchEvent) {
        val service = accessibilityService ?: run {
            Log.w("RemoteInput", "AccessibilityService not set")
            return
        }
        val x = event.x * screenWidth
        val y = event.y * screenHeight

        when (event.action) {
            Protocol.TOUCH_DOWN, Protocol.TOUCH_MOVE -> {
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                    .build()
                service.dispatchGesture(gesture, null, null)
            }
            Protocol.TOUCH_UP -> {
                // tap gesture ends naturally
            }
        }
    }
}
