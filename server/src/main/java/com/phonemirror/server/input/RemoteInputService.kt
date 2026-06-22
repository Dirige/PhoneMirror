package com.phonemirror.server.input

import android.accessibilityservice.AccessibilityService
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class RemoteInputService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        RemoteInputHandler.setService(this)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        RemoteInputHandler.setScreenSize(metrics.widthPixels, metrics.heightPixels)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // not used
    }

    override fun onInterrupt() {
        // not used
    }
}
