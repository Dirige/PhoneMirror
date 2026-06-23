package com.phonemirror.server.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        try {
            // 使用应用专属外部存储目录，无需额外权限
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            logFile = File(logDir, "phonemirror_$date.log")
            
            writer = BufferedWriter(FileWriter(logFile, true))
            log("FileLogger", "日志系统初始化完成，文件路径: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "初始化日志文件失败", e)
        }
    }

    fun log(tag: String, message: String) {
        try {
            writer?.apply {
                val timestamp = dateFormat.format(Date())
                write("[$timestamp] [$tag] $message\n")
                flush()
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "写入日志失败", e)
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        try {
            writer?.apply {
                val timestamp = dateFormat.format(Date())
                write("[$timestamp] [$tag] ERROR: $message\n")
                throwable?.let {
                    write("[$timestamp] [$tag] ${it.stackTraceToString()}\n")
                }
                flush()
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "写入日志失败", e)
        }
    }

    fun close() {
        try {
            writer?.close()
            writer = null
        } catch (e: Exception) {
            android.util.Log.e("FileLogger", "关闭日志文件失败", e)
        }
    }
}
