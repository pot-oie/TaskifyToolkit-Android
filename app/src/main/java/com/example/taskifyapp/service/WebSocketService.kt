package com.example.taskifyapp.service

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.taskifyapp.ui.dialog.CompletionDialogActivity
import com.example.taskifyapp.model.ServiceState
import com.example.taskifyapp.util.LogManager
import com.example.taskifyapp.util.ScreenCaptureListener
import com.example.taskifyapp.util.ScreenCaptureManager
import com.example.taskifyapp.util.StateReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketService : Service(), ScreenCaptureListener {

    private val TAG = "WebSocketService"

    // --- 内部配置和状态 ---
    private var webSocketUrl = "" // 将由Intent在启动时配置
    private var shouldAutoReconnect = true // 同上
    private lateinit var okHttpClient: OkHttpClient
    private var webSocket: WebSocket? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // 与 Service 生命周期绑定的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 状态标志位
    @Volatile private var isUpdateAlreadyTriggered = false

    // 重连机制
    private val RECONNECT_INTERVAL = 5000L
    private val reconnectRunnable = Runnable {
        _serviceState.value = ServiceState.Reconnecting
        startWebSocket()
    }

    companion object {
        // --- 使用Flow来向外部(Repository)报告状态和日志 ---
        private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
        val serviceState = _serviceState.asStateFlow() // 只读 StateFlow

        // --- Intent Action 和 Extra 常量 ---
        const val ACTION_START = "ACTION_START"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_RECONNECT = "EXTRA_RECONNECT"
    }

    private inner class AgentWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _serviceState.value = ServiceState.Connected() // 更新状态
            mainHandler.removeCallbacks(reconnectRunnable) // 成功连接，移除重连任务
            LogManager.log("[SUCCESS] WebSocket 连接已建立!")
            ForegroundServiceManager.updateNotification(applicationContext, "已连接到服务器，等待指令...")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "从服务器收到指令: $text")
            LogManager.log("[RECEIVE] 收到指令: $text")
            handleCommand(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _serviceState.value = ServiceState.Disconnected(reason) // 更新状态
            LogManager.log("[CLOSED] WebSocket 连接已关闭: $reason")
            ForegroundServiceManager.updateNotification(applicationContext, "与服务器断开连接，尝试重连...")
            if (shouldAutoReconnect) {
                mainHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _serviceState.value = ServiceState.Error("连接失败", t) // 更新状态
            Log.e(TAG, "WebSocket 连接失败", t)
            LogManager.log("[FAIL] WebSocket 连接失败")
            ForegroundServiceManager.updateNotification(applicationContext, "连接失败，请检查网络或服务器")
            if (shouldAutoReconnect) {
                mainHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL)
            }
        }
    }

    // --- 服务生命周期方法 ---

    override fun onCreate() {
        super.onCreate()
        // 不再读取 SharedPreferences，等待 Intent 传入配置
        okHttpClient = OkHttpClient.Builder()
            .pingInterval(120, TimeUnit.SECONDS)
            .build()
        // 在服务创建时初始化屏幕采集管理器
        ScreenCaptureManager.initialize(this)
    }

    /**
     * 服务启动入口，从 Intent 中获取所有必需的配置
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _serviceState.value = ServiceState.Initializing
        ForegroundServiceManager.startForeground(this, "服务正在初始化...")

        if (intent?.action == ACTION_START) {
            // 从 Intent 获取配置
            webSocketUrl = intent.getStringExtra(EXTRA_URL) ?: ""
            shouldAutoReconnect = intent.getBooleanExtra(EXTRA_RECONNECT, true)

            if (webSocketUrl.isEmpty()){
                Log.e(TAG, "服务启动失败：URL为空。")
                stopAndCleanup()
                return START_NOT_STICKY
            }

            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (projectionData != null && !ScreenCaptureManager.isCaptureSessionActive) {
                ScreenCaptureManager.startCapture(Activity.RESULT_OK, projectionData, this)
            } else if (projectionData == null) {
                Log.e(TAG, "服务启动失败：缺少截图授权数据。")
                stopAndCleanup()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopAndCleanup()
    }

    /**
     * 在主线程上显示Toast的辅助方法
     */
    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWebSocket() {
        if (_serviceState.value is ServiceState.Connected) return
        Log.d(TAG, "正在连接 WebSocket...")
        // --- 在尝试连接前，先清除旧的重连任务，避免重复执行 ---
        mainHandler.removeCallbacks(reconnectRunnable)
        // --- 使用从配置加载的URL ---
        val request = Request.Builder().url(webSocketUrl).build()
        webSocket = okHttpClient.newWebSocket(request, AgentWebSocketListener())
    }

    /**
     * [功能] 统一的清理方法
     */
    private fun stopAndCleanup() {
        Log.d(TAG, "正在停止服务并清理所有资源...")
        // --- 在服务清理时，确保移除所有待处理的重连任务 ---
        mainHandler.removeCallbacks(reconnectRunnable)

        // 停止屏幕采集
        ScreenCaptureManager.stopCapture()

        // 清理 WebSocket 资源
        webSocket?.close(1000, "Service shutting down")
        webSocket = null

        // 停止前台服务并移除通知
        _serviceState.value = ServiceState.Idle // 更新最终状态
        stopForeground(true)
        stopSelf()
    }

    // --- 核心逻辑方法 ---

    private fun handleCommand(commandJson: String) {
        // 将整个操作放入主线程执行，确保线程安全
        serviceScope.launch {
            try {
                val json = JSONObject(commandJson)
                val actionType = json.getString("actionType")
                val accessibilityService = TaskifyAccessibilityService.instance

                if (actionType == "FINISH_TASK") {
                    val message = "任务完成！"
                    LogManager.log("[INFO] $message")
                    showToast(message)
                    ForegroundServiceManager.updateNotification(applicationContext, message)
                    val dialogIntent = Intent(
                        applicationContext,
                        CompletionDialogActivity::class.java
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(dialogIntent)
                    return@launch
                }

                if (accessibilityService == null) {
                    val errorMsg = "[ERROR] 处理指令 '$actionType' 失败: 无障碍服务未连接"
                    LogManager.log(errorMsg)
                    showToast("无障碍服务未连接")
                    reportError("无障碍服务未连接")
                    return@launch
                }

                var result = false
                var shouldReportAfterExecution = false

                if (actionType == "CAPTURE_AND_REPORT") {
                    showToast("正在采集屏幕信息...")
                    LogManager.log("[EXEC] 执行指令: 采集并上报")
                    val payload = StateReporter.captureAndPreparePayload(null)
                    if (payload != null) {
                        if (webSocket?.send(payload.toString()) == true) {
                            showToast("屏幕信息已上报")
                        } else {
                            Log.e(TAG, "[Error] WebSocket 发送失败")
                        }
                    } else {
                        _serviceState.value = ServiceState.Error("采集或处理数据失败")
                    }
                    return@launch
                }

                when (actionType) {
                    "CLICK" -> {
                        val targetText = json.getString("targetText")
                        showToast("正在点击: '$targetText'")
                        ForegroundServiceManager.updateNotification(applicationContext, "正在点击: '$targetText'")
                        LogManager.log("[EXEC] 执行指令: 点击 '$targetText'")
                        result = accessibilityService.clickByText(targetText)
                        shouldReportAfterExecution = true
                    }
                    "INPUT_TEXT" -> {
                        val targetText = json.getString("targetText")
                        val textToInput = json.getString("textToInput")
                        showToast("正在输入文本...")
                        ForegroundServiceManager.updateNotification(applicationContext, "正在输入文本...")
                        LogManager.log("[EXEC] 执行指令: 在 '$targetText' 中输入文本")
                        result = accessibilityService.inputTextByText(targetText, textToInput)
                        shouldReportAfterExecution = true
                    }
                    "LONG_CLICK" -> {
                        val targetText = json.getString("targetText")
                        showToast("正在长按: '$targetText'")
                        ForegroundServiceManager.updateNotification(applicationContext, "正在长按: '$targetText'")
                        LogManager.log("[EXEC] 执行指令: 长按 '$targetText'")
                        result = accessibilityService.longClickByText(targetText)
                        shouldReportAfterExecution = true
                    }
                    "SWIPE" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val startX = json.getDouble("startX").toFloat()
                            val startY = json.getDouble("startY").toFloat()
                            val endX = json.getDouble("endX").toFloat()
                            val endY = json.getDouble("endY").toFloat()
                            val duration = json.optLong("duration", 400)
                            LogManager.log("[EXEC] 执行指令: 滑动操作")
                            showToast("正在执行滑动...")
                            ForegroundServiceManager.updateNotification(applicationContext, "正在执行滑动...")
                            accessibilityService.performSwipe(
                                startX,
                                startY,
                                endX,
                                endY,
                                duration
                            )
                            result = true // 滑动操作没有直接的布尔返回值，假定成功
                            shouldReportAfterExecution = true
                        } else {
                            LogManager.log("[ERROR] 滑动操作需要 Android 7.0 (API 24) 或更高版本")
                            result = false
                        }
                    }
                    "SCROLL" -> {
                        val targetText = json.getString("targetText")
                        val directionStr = json.getString("direction")
                        val direction =
                            if (directionStr.equals("FORWARD", ignoreCase = true)) 1 else -1
                        LogManager.log("[EXEC] 执行指令: 滚动 '$targetText' (方向: $directionStr)")
                        showToast("正在滚动页面...")
                        ForegroundServiceManager.updateNotification(applicationContext, "正在滚动页面...")
                        result = accessibilityService!!.scrollByText(targetText, direction)
                        shouldReportAfterExecution = true
                    }
                    "GO_BACK" -> {
                        LogManager.log("[EXEC] 执行指令: 返回上一页")
                        showToast("正在执行返回...")
                        ForegroundServiceManager.updateNotification(applicationContext, "正在执行返回...")
                        result = accessibilityService!!.performGlobalBack()
                        shouldReportAfterExecution = true
                    }
                }
                if (result) {
                    LogManager.log("[SUCCESS] 指令 '$actionType' 执行成功")
                } else {
                    LogManager.log("[FAIL] 指令 '$actionType' 执行失败或未找到目标")
                }

                if (shouldReportAfterExecution && result) {
                    isUpdateAlreadyTriggered = false // 重置标志位，准备接收下一次触发

                    // 启动新的子协程来专门处理延时任务
                    serviceScope.launch {
                        delay(1500) // 非阻塞地等待1.5秒

                        // 如果事件尚未触发，则由延时协程来触发
                        if (!isUpdateAlreadyTriggered) {
                            isUpdateAlreadyTriggered = true // 标记延时已触发
                            LogManager.log("[AUTO-FALLBACK] 事件未在1.5秒内到达，由延时协程触发上报...")
                            showToast("延时触发上报...")
                            ForegroundServiceManager.updateNotification(applicationContext, "延时触发上报...")
                            val payload = StateReporter.captureAndPreparePayload("Action '$actionType' succeeded.")
                            if (payload != null) {
                                if (webSocket?.send(payload.toString()) == true) {
                                    showToast("上报成功！")
                                } else {
                                    Log.e(TAG, "[Error] WebSocket 发送失败")
                                }
                            } else {
                                _serviceState.value = ServiceState.Error("采集或处理数据失败")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "处理指令失败: ${e.message}"
                showToast(errorMsg)
                ForegroundServiceManager.updateNotification(applicationContext, errorMsg)
                LogManager.log("[ERROR] $errorMsg")
                _serviceState.value = ServiceState.Error(errorMsg, e)
            }
        }
    }

    /**
     * [功能] 向后端报告错误信息
     */
    private fun reportError(errorMessage: String) {
        if (_serviceState.value !is ServiceState.Connected) return
        try {
            val errorPayload = JSONObject().apply {
                put("type", "error")
                put("message", errorMessage)
            }
            webSocket?.send(errorPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "上报错误信息失败", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- 实现 ScreenCaptureListener 接口的回调方法 ---

    override fun onCaptureStarted() {
        Log.d(TAG, "采集会话已启动，准备连接 WebSocket...")
        // 在截图会话完全就绪后，再启动 WebSocket 连接
        startWebSocket()
    }

    override fun onCaptureStopped() {
        Log.w(TAG, "采集会话由外部停止，正在清理服务...")
        mainHandler.post { Toast.makeText(applicationContext, "后台服务已停止", Toast.LENGTH_SHORT).show() }
        stopAndCleanup()
    }

    override fun onCaptureFailed(reason: String) {
        Log.e(TAG, "采集会话启动失败: $reason")
        _serviceState.value = ServiceState.Error(reason)
        stopAndCleanup()
    }
}