package com.example.taskifyapp.util

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 一个监听器接口，用于将屏幕采集的状态回调给调用方 (例如 WebSocketService)
 */
interface ScreenCaptureListener {
    fun onCaptureStarted()
    fun onCaptureStopped()
    fun onCaptureFailed(reason: String)
}

/**
 * 单例对象，专门负责管理 MediaProjection 屏幕采集会话
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var listener: ScreenCaptureListener? = null

    val isCaptureSessionActive: Boolean
        get() = mediaProjection != null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection 被用户或系统停止")
            listener?.onCaptureStopped()
        }
    }

    /**
     * 初始化管理器
     */
    fun initialize(context: Context) {
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * 启动屏幕采集会话
     * @param resultCode 授权结果码
     * @param resultData 包含授权信息的 Intent
     * @param listener 状态回调监听器
     */
    fun startCapture(resultCode: Int, resultData: Intent, listener: ScreenCaptureListener) {
        this.listener = listener
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            Log.e(TAG, "无法获取 MediaProjection 实例")
            listener.onCaptureFailed("无法获取 MediaProjection 实例")
            return
        }

        mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AgentScreen", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )

        Log.d(TAG, "屏幕采集会话已成功启动")
        listener.onCaptureStarted()
    }

    /**
     * 停止屏幕采集会话并清理资源
     */
    fun stopCapture() {
        if (!isCaptureSessionActive) return
        Log.d(TAG, "正在停止屏幕采集并清理资源...")
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        listener = null
    }

    /**
     * 捕获当前的屏幕图像并返回一个 Bitmap
     * @return 捕获到的 Bitmap，如果失败则返回 null
     */
    fun captureCurrentImage(): Bitmap? {
        if (!isCaptureSessionActive || imageReader == null) {
            Log.e(TAG, "采集失败: 采集会话未激活或 imageReader 为空")
            return null
        }
        return try {
            val image = imageReader!!.acquireLatestImage()
            if (image == null) {
                Log.e(TAG, "采集失败: 无法获取最新图像 (image is null)")
                null
            } else {
                val bitmap = ImageUtils.imageToBitmap(image)
                image.close() // 转换后立刻关闭，释放资源
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "acquireLatestImage 失败", e)
            listener?.onCaptureFailed("获取图像失败")
            null
        }
    }
}