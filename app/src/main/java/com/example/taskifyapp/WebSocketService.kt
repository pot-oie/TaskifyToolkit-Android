package com.example.taskifyapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {

    private val TAG = "WebSocketService"
    private val UI_DEBUG_TAG = "UI_DEBUG"

    // !!!重要!!! 请将这里替换为您的后端服务器地址（这是我的）
    private var webSocketUrl = "ws://192.168.3.15:8080/agent-ws"
    private var shouldAutoReconnect = true

    private lateinit var okHttpClient: OkHttpClient
    private var webSocket: WebSocket? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // --- MediaProjection 相关变量 ---
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // --- 状态标志位 ---
    @Volatile
    private var isCaptureSessionActive = false
    @Volatile
    private var isWebSocketConnected = false

    // --- 重连机制相关变量 ---
    private val RECONNECT_INTERVAL = 5000L // 重连间隔，5000毫秒 = 5秒
    private val reconnectRunnable = Runnable {
        Log.d(TAG, "正在尝试重新连接...")
        sendStatusBroadcast("尝试重连中...", STATE_ERROR) // 通知UI
        startWebSocket()
    }

    companion object {
        // --- 状态常量 ---
        const val STATE_ON = "STATE_ON"         // 成功连接
        const val STATE_OFF = "STATE_OFF"       // 中性关闭/未启动
        const val STATE_ERROR = "STATE_ERROR"   // 错误或重连中

        const val ACTION_START = "ACTION_START"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "WebSocketChannel"

        // --- 用于广播的常量 ---
        const val ACTION_STATUS_UPDATE = "com.example.taskifyapp.ACTION_STATUS_UPDATE"
        const val EXTRA_STATUS_TEXT = "EXTRA_STATUS_TEXT"
        const val EXTRA_STATUS_STATE = "EXTRA_STATUS_STATE"

        @Volatile
        private var currentStatusText: String = "未启动"
        @Volatile
        private var currentState: String = STATE_OFF

        /**
         * [功能] 允许外部查询当前服务的实时状态（文本和状态类型）
         */
        fun getCurrentStatus(): Pair<String, String> {
            return currentStatusText to currentState
        }

        // --- 日志广播相关的常量 ---
        const val ACTION_LOG_UPDATE = "com.example.taskifyapp.ACTION_LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "EXTRA_LOG_MESSAGE"
    }

    // --- 专门发送日志的函数 ---
    private fun sendLog(logText: String) {
        val intent = Intent(ACTION_LOG_UPDATE).apply {
            putExtra(EXTRA_LOG_MESSAGE, logText)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // --- 发送广播的辅助函数 ---
    private fun sendStatusBroadcast(text: String, state: String) {
        Log.d(UI_DEBUG_TAG, "[SERVICE] 准备更新并发送 LocalBroadcast, status: $text, state: $state")
        currentStatusText = text
        currentState = state

        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_STATUS_TEXT, text)
            putExtra(EXTRA_STATUS_STATE, state)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // 监听用户手动停止屏幕共享的回调
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection 被用户或系统停止")
            mainHandler.post { Toast.makeText(applicationContext, "后台服务已停止", Toast.LENGTH_SHORT).show() }
            stopAndCleanup()
        }
    }

    private inner class AgentWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isWebSocketConnected = true
            Log.i(TAG, "WebSocket 连接已建立!")
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.post{
                sendLog("[SUCCESS] WebSocket 连接已建立!")
                updateNotification("已连接到服务器，等待指令...")
                sendStatusBroadcast("已连接", STATE_ON)
            }
            // --- 连接成功后，移除所有待处理的重连任务 ---
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.post { updateNotification("已连接到服务器，等待指令...") }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "从服务器收到指令: $text")
            sendLog("[RECEIVE] 收到指令: $text")
            handleCommand(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isWebSocketConnected = false
            Log.i(TAG, "WebSocket 连接已关闭: $reason")
            mainHandler.post {
                sendLog("[CLOSED] WebSocket 连接已关闭: $reason")
                updateNotification("与服务器断开连接，尝试重连...")
                sendStatusBroadcast("连接已关闭", STATE_OFF)
            }
            // --- 根据配置决定是否重连 ---
            if (shouldAutoReconnect) {
                mainHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isWebSocketConnected = false
            Log.e(TAG, "WebSocket 连接失败", t)
            mainHandler.post {
                sendLog("[FAIL] WebSocket 连接失败")
                updateNotification("连接失败，请检查网络或服务器")
                sendStatusBroadcast("连接失败", STATE_ERROR)
            }
            // --- 根据配置决定是否重连 ---
            if (shouldAutoReconnect) {
                mainHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL)
            }
        }
    }

    // --- 服务生命周期方法 ---

    override fun onCreate() {
        super.onCreate()
        sendLog("[INFO] 服务正在创建...")
        // --- 在服务创建时，从 SharedPreferences 加载配置 ---
        val prefs = getSharedPreferences("TaskifySettings", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "")
        if (!savedUrl.isNullOrEmpty()) {
            webSocketUrl = savedUrl
        }
        shouldAutoReconnect = prefs.getBoolean("auto_reconnect", true)
        // 初始化 OkHttpClient 和 MediaProjectionManager
        okHttpClient = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS) // 保持长连接
            .build()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须先将服务设置为前台服务
        startForegroundWithNotification("服务正在初始化...")
        // 发送广播
        sendStatusBroadcast("正在初始化...", STATE_OFF)

        if (intent?.action == ACTION_START) {
            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (projectionData != null && !isCaptureSessionActive) {
                // 只有在获取到授权，并且截图会话尚未激活时，才进行初始化
                startScreenCaptureSession(projectionData)
            } else if (projectionData == null) {
                Log.e(TAG, "服务启动失败：缺少截图授权数据。")
                stopSelf() // 如果没有授权，服务无法工作，直接停止
            }
        }

        // 使用 START_REDELIVER_INTENT
        // 如果服务在处理完 onStartCommand 之前被杀死，系统会尝试重新创建服务，并重新传递最后一个 Intent。
        // 这给了我们一次机会来恢复截图会话，比 START_STICKY 更可靠。
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        // 发送广播
        sendStatusBroadcast("服务已停止", STATE_OFF)
        // 在服务销毁时，调用统一的清理方法
        stopAndCleanup()
    }

    // --- 核心逻辑方法 ---

    private fun startScreenCaptureSession(data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
        if (mediaProjection == null) {
            Log.e(TAG, "无法获取 MediaProjection 实例")
            stopAndCleanup()
            return
        }

        // 注册回调
        mediaProjection?.registerCallback(projectionCallback, mainHandler)

        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AgentScreen", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )

        isCaptureSessionActive = true
        Log.d(TAG, "截图会话已成功启动")

        // 在截图会话完全就绪后，再启动 WebSocket 连接
        startWebSocket()
    }

    private fun startWebSocket() {
        if (isWebSocketConnected) return
        Log.d(TAG, "正在连接 WebSocket...")
        // --- 在尝试连接前，先清除旧的重连任务，避免重复执行 ---
        mainHandler.removeCallbacks(reconnectRunnable)
        // --- 使用从配置加载的URL ---
        val request = Request.Builder().url(webSocketUrl).build()
        webSocket = okHttpClient.newWebSocket(request, AgentWebSocketListener())
    }

    private fun handleCommand(commandJson: String) {
        try {
            val json = JSONObject(commandJson)
            val actionType = json.getString("actionType")
            val accessibilityService = TaskifyAccessibilityService.instance
            if (accessibilityService == null) {
                val errorMsg = "[ERROR] 处理指令 '$actionType' 失败: 无障碍服务未连接"
                Log.e(TAG, errorMsg)
                sendLog(errorMsg)
                reportError("无障碍服务未连接")
                return
            }

            var result = false
            when (actionType) {
                "CAPTURE_AND_REPORT" -> {
                    sendLog("[EXEC] 执行指令: 采集并上报")
                    captureAndReportState()
                    return // 此操作为异步，直接返回
                }
                "CLICK" -> {
                    val targetText = json.getString("targetText")
                    sendLog("[EXEC] 执行指令: 点击 '$targetText'")
                    result = accessibilityService.clickByText(targetText)
                }
                "INPUT_TEXT" -> {
                    val targetText = json.getString("targetText")
                    val textToInput = json.getString("textToInput")
                    sendLog("[EXEC] 执行指令: 在 '$targetText' 中输入文本")
                    result = accessibilityService.inputTextByText(targetText, textToInput)
                }
                "LONG_CLICK" -> {
                    val targetText = json.getString("targetText")
                    sendLog("[EXEC] 执行指令: 长按 '$targetText'")
                    result = accessibilityService.longClickByText(targetText)
                }
                "SWIPE" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val startX = json.getDouble("startX").toFloat()
                        val startY = json.getDouble("startY").toFloat()
                        val endX = json.getDouble("endX").toFloat()
                        val endY = json.getDouble("endY").toFloat()
                        val duration = json.optLong("duration", 400)
                        sendLog("[EXEC] 执行指令: 滑动操作")
                        accessibilityService.performSwipe(startX, startY, endX, endY, duration)
                        result = true // 滑动操作没有直接的布尔返回值，我们假定它成功
                    } else {
                        sendLog("[ERROR] 滑动操作需要 Android 7.0 (API 24) 或更高版本")
                        result = false
                    }
                }
                "SCROLL" -> {
                    val targetText = json.getString("targetText")
                    val directionStr = json.getString("direction")
                    val direction = if (directionStr.equals("FORWARD", ignoreCase = true)) 1 else -1
                    sendLog("[EXEC] 执行指令: 滚动 '$targetText' (方向: $directionStr)")
                    result = accessibilityService.scrollByText(targetText, direction)
                }
                "FINISH_TASK" -> {
                    val message = "任务完成！"
                    sendLog("[INFO] $message")
                    mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
                    return
                }
            }
            // 统一发送执行结果日志
            if (result) {
                sendLog("[SUCCESS] 指令 '$actionType' 执行成功")
            } else {
                sendLog("[FAIL] 指令 '$actionType' 执行失败或未找到目标")
            }

        } catch (e: Exception) {
            val errorMsg = "[ERROR] 处理指令失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            sendLog(errorMsg)
        }
    }

    private fun captureAndReportState() {
        Log.d(TAG, "[Debug] 收到采集指令，开始执行...")

        if (!isCaptureSessionActive || imageReader == null) {
            val errorMsg = "采集失败: 截图会话未激活或 imageReader 为空"
            Log.e(TAG, "[Debug] $errorMsg")
            reportError(errorMsg)
            return
        }

        val image = try {
            imageReader!!.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "[Debug] acquireLatestImage 失败", e)
            null
        }

        if (image == null) {
            val errorMsg = "采集失败: 无法获取最新图像 (image is null)"
            Log.e(TAG, "[Debug] $errorMsg")
            reportError(errorMsg)
            return
        }
        Log.d(TAG, "[Debug] 1. 成功获取 Image 对象")

        val accessibilityService = TaskifyAccessibilityService.instance
        if (accessibilityService == null) {
            val errorMsg = "采集失败: 无障碍服务未连接"
            Log.e(TAG, "[Debug] $errorMsg")
            reportError(errorMsg)
            image.close() // 别忘了关闭 image
            return
        }

        try {
            // --- 数据处理和上报 ---
            val originalBitmap = imageToBitmap(image)
            Log.d(TAG, "[Debug] 2. 成功将 Image 转换为原始 Bitmap，尺寸: ${originalBitmap.width}x${originalBitmap.height}")
            image.close() // 在转换后立刻关闭，释放资源

            val resizedBitmap = resizeBitmapByWidth(originalBitmap, 1080)
            Log.d(TAG, "[Debug] 3. 成功缩放 Bitmap，新尺寸: ${resizedBitmap.width}x${resizedBitmap.height}")

            val imageBase64 = bitmapToBase64(resizedBitmap)
            Log.d(TAG, "[Debug] 4. 成功将 Bitmap 编码为 Base64，字符串长度: ${imageBase64.length}")

            val layoutXml = accessibilityService.getLayoutXml() ?: ""
            Log.d(TAG, "[Debug] 5. 成功获取 XML，字符串长度: ${layoutXml.length}")


            val payload = JSONObject().apply {
                put("imageBase64", imageBase64)
                put("layoutXml", layoutXml)
            }
            val payloadString = payload.toString()
            val totalPayloadSizeKB = payloadString.toByteArray().size / 1024.0
            Log.d(TAG, "[Debug] 6. 准备发送最终数据，总大小: %.2f KB".format(totalPayloadSizeKB))

            if (webSocket?.send(payloadString) == true) {
                Log.d(TAG, "[Debug] 7. 数据已成功发送到 WebSocket")
            } else {
                Log.e(TAG, "[Debug] 7. WebSocket 发送失败，webSocket 对象可能为 null 或连接已关闭")
            }

        } catch (e: Exception) {
            // 捕获所有可能的异常，例如 OutOfMemoryError, NullPointerException 等
            Log.e(TAG, "[Debug] 在处理和发送数据过程中发生严重错误!", e)
            reportError("客户端处理数据时发生异常: ${e.message}")
        }
    }

    /**
     * [功能] 向后端报告错误信息
     */
    private fun reportError(errorMessage: String) {
        if (!isWebSocketConnected) return
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

    /**
     * [功能] 统一的清理方法
     */
    private fun stopAndCleanup() {
        Log.d(TAG, "正在停止服务并清理所有资源...")
        // --- 在服务清理时，确保移除所有待处理的重连任务 ---
        mainHandler.removeCallbacks(reconnectRunnable)

        isCaptureSessionActive = false
        isWebSocketConnected = false

        // 清理 MediaProjection 资源
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null

        // 清理 WebSocket 资源
        webSocket?.close(1000, "Service shutting down")
        webSocket = null

        // 停止前台服务并移除通知
        stopForeground(true)
        stopSelf()
    }


    // --- 辅助方法 ---

    override fun onBind(intent: Intent?): IBinder? = null

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        var bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding > 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
        return bitmap
    }

    /**
     * 根据设定的宽度，等比缩放 Bitmap
     * @param source 原始 Bitmap
     * @param targetWidth 目标宽度 (1080)
     * @return 缩放后的新 Bitmap
     */
    private fun resizeBitmapByWidth(source: Bitmap, targetWidth: Int): Bitmap {
        // 如果原始宽度小于或等于目标宽度，则无需缩放，直接返回原图
        if (source.width <= targetWidth) {
            return source
        }
        val targetHeight = (source.height.toFloat() / source.width.toFloat() * targetWidth).toInt()
        Log.d(TAG, "图片尺寸已从 ${source.width}x${source.height} 缩放至 ${targetWidth}x${targetHeight}")
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun startForegroundWithNotification(contentText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Taskify 连接服务", NotificationManager.IMPORTANCE_LOW) // 使用 LOW，避免声音
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TaskifyToolkit 后台服务")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // 使通知不可清除
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TaskifyToolkit 后台服务")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}