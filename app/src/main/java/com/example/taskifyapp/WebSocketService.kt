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
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {

    private val TAG = "WebSocketService"

    // !!!重要!!! 请将这里替换为您的后端服务器地址（这是我的）
    private val WEBSOCKET_URL = "ws://192.168.3.15:8080/agent-ws"

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

    companion object {
        const val ACTION_START = "ACTION_START"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "WebSocketChannel"
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
            mainHandler.post { updateNotification("已连接到服务器，等待指令...") }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "从服务器收到指令: $text")
            handleCommand(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isWebSocketConnected = false
            Log.i(TAG, "WebSocket 连接已关闭: $reason")
            mainHandler.post { updateNotification("与服务器断开连接，尝试重连...") }
            // 可以在这里添加重连逻辑
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isWebSocketConnected = false
            Log.e(TAG, "WebSocket 连接失败", t)
            mainHandler.post { updateNotification("连接失败，请检查网络或服务器") }
        }
    }

    // --- 服务生命周期方法 ---

    override fun onCreate() {
        super.onCreate()
        // 初始化 OkHttpClient 和 MediaProjectionManager
        okHttpClient = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS) // 保持长连接
            .build()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须先将服务设置为前台服务
        startForegroundWithNotification("服务正在初始化...")

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
        val request = Request.Builder().url(WEBSOCKET_URL).build()
        webSocket = okHttpClient.newWebSocket(request, AgentWebSocketListener())
    }

    private fun handleCommand(commandJson: String) {
        try {
            val json = JSONObject(commandJson)
            when (json.getString("actionType")) {
                "CAPTURE_AND_REPORT" -> {
                    mainHandler.post { Toast.makeText(applicationContext, "收到指令: 采集并上报", Toast.LENGTH_SHORT).show() }
                    captureAndReportState()
                }
                "CLICK" -> {
                    val targetText = json.getString("targetText")
                    mainHandler.post { Toast.makeText(applicationContext, "收到指令: 点击 '$targetText'", Toast.LENGTH_SHORT).show() }
                    TaskifyAccessibilityService.instance?.clickByText(targetText)
                }
                "FINISH_TASK" -> {
                    mainHandler.post { Toast.makeText(applicationContext, "任务完成！", Toast.LENGTH_LONG).show() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理指令失败", e)
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