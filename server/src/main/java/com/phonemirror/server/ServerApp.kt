package com.phonemirror.server

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

class ServerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 写入日志文件
            try {
                val dir = getExternalFilesDir(null) ?: Environment.getExternalStorageDirectory()
                val logFile = File(dir, "crash_log.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(Date())
                PrintWriter(logFile).use { pw ->
                    pw.println("=== Crash at $timestamp ===")
                    pw.println("Thread: ${thread.name}")
                    throwable.printStackTrace(pw)
                    pw.println()
                }
                Log.e("CrashHandler", "Crash written to ${logFile.absolutePath}", throwable)
            } catch (e: Exception) {
                Log.e("CrashHandler", "Failed to write crash log", e)
            }
            // 调用默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
