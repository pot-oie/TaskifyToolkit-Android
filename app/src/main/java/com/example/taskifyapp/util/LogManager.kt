package com.example.taskifyapp.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogManager {

    private lateinit var logFile: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 将 logFlow 移至此处进行统一管理
    private val _logFlow = MutableSharedFlow<String>(replay = 10) // 缓存最新的10条日志
    val logFlow = _logFlow.asSharedFlow()

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * 必须在 Application 中调用此方法进行初始化
     */
    fun initialize(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "taskify_log.txt")
    }

    /**
     * 全局的日志记录方法
     * @param message 日志信息
     */
    fun log(message: String) {
        val timestamp = sdf.format(Date())
        val logEntry = "[$timestamp] $message"

        // 1. 发射到UI层
        scope.launch {
            _logFlow.emit(message) // UI层只关心消息本身
        }

        // 2. 写入到本地文件
        scope.launch {
            try {
                logFile.appendText("$logEntry\n")
            } catch (e: Exception) {
                // 在Logcat中打印文件写入错误，避免循环调用
                android.util.Log.e("LogManager", "Failed to write log to file", e)
            }
        }
    }
}