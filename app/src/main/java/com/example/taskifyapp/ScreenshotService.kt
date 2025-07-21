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
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ScreenshotService : Service() {

    companion object {
        private const val TAG = "ScreenshotService"
        // 定义服务的不同命令
        const val ACTION_START = "ACTION_START"
        const val ACTION_CAPTURE = "ACTION_CAPTURE"
        const val ACTION_STOP = "ACTION_STOP" // 未来可以用于停止服务
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // MediaProjection 的状态回调
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection 被用户停止")
            // 当用户从通知栏等地方停止屏幕共享时，清理资源并停止服务
            stopAndCleanup()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 根据从 MainActivity 传来的 Intent 中的 Action (命令)，执行不同的操作
        when (intent?.action) {
            // 当命令是 "ACTION_START" 时（通常在用户授予截图权限后触发）
            ACTION_START -> {
                // 从 Intent 中安全地取出授权数据 (result.data)
                // 这里需要做版本判断，因为从 Android 13 (Tiramisu) 开始，获取 Parcelable Extra 的方法有了变化
                val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") // 抑制旧方法的弃用警告
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                // 确保授权数据不为空
                if (projectionData != null) {
                    // 将服务提升为前台服务，并显示一个常驻通知
                    startForegroundWithNotification()
                    // 延迟一小会（300毫秒）确保服务已完全启动，然后开始录屏会话
                    Handler(Looper.getMainLooper()).postDelayed({
                        startScreenCaptureSession(projectionData)
                    }, 300)
                } else {
                    // 如果没有授权数据，则记录错误并停止服务
                    Log.e(TAG, "授权数据为空，无法启动录屏会话")
                    stopSelf()
                }
            }
            // 当命令是 "ACTION_CAPTURE" 时（由“执行单次截图”按钮触发）
            ACTION_CAPTURE -> {
                // 直接调用截图方法，执行一次截图操作
                captureAndSave()
            }
            // 当命令是 "ACTION_STOP" 时（未来可以添加一个停止按钮来调用）
            ACTION_STOP -> {
                // 清理所有资源并停止服务
                stopAndCleanup()
            }
        }
        // START_NOT_STICKY 表示如果服务在执行任务时被系统因内存不足等原因杀死，不要自动重启服务。
        return START_NOT_STICKY
    }

    // 启动录屏会话，并保持状态
    private fun startScreenCaptureSession(data: Intent) {
        if (mediaProjection != null) return // 如果已在运行，则不重复创建
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
        if (mediaProjection == null) {
            Log.e(TAG, "无法获取 MediaProjection")
            stopSelf(); return
        }

        // 在创建 VirtualDisplay 之前，注册回调
        mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        val metrics = Resources.getSystem().displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenshotSession",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        Log.d(TAG, "录屏会话已启动并保持")
    }

    // 按需执行单次截图
    private fun captureAndSave() {
        if (imageReader == null) {
            Log.e(TAG, "截图失败，录屏会话未启动")
            return
        }
        val image: Image? = try {
            imageReader!!.acquireLatestImage()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (image != null) {
            val bitmap = imageToBitmap(image)
            image.close()
            Log.d(TAG, "单次截图成功！")
            saveBitmapToFile(bitmap)
            Toast.makeText(this, "截图成功并已保存！", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "无法获取最新图像")
        }
    }

    // 停止并清理所有资源
    private fun stopAndCleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- 以下是辅助方法 ---

    override fun onDestroy() {
        super.onDestroy()
        stopAndCleanup()
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun startForegroundWithNotification() {
        val channelId = "ScreenshotChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Screenshot Service Channel", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TaskifyApp 截图服务")
            .setContentText("正在执行截图...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(123, notification)
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(path, "screenshot_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "截图已保存至: ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}