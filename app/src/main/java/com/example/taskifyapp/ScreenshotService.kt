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
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ScreenshotService : Service() {

    companion object {
        private const val TAG = "ScreenshotService"
        // 定义服务的不同命令
        const val ACTION_START = "ACTION_START"
        const val ACTION_CAPTURE_AND_DUMP_LOCALLY = "ACTION_CAPTURE_AND_DUMP_LOCALLY"
        const val ACTION_CAPTURE_AND_UPLOAD = "ACTION_CAPTURE_AND_UPLOAD"
        const val ACTION_STOP = "ACTION_STOP" // 未来可以用于停止服务
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

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
            ACTION_START -> handleStart(intent)
            ACTION_CAPTURE_AND_DUMP_LOCALLY -> captureAndDumpLocally()
            ACTION_CAPTURE_AND_UPLOAD -> captureAndUploadState()
            ACTION_STOP -> stopAndCleanup()
        }
        // START_NOT_STICKY 表示如果服务在执行任务时被系统因内存不足等原因杀死，不要自动重启服务
        return START_NOT_STICKY
    }

    // 当命令是 "ACTION_START" 时（通常在用户授予截图权限后触发）
    private fun handleStart(intent: Intent) {
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
            mainHandler.postDelayed({ startScreenCaptureSession(projectionData) }, 300)
        } else {
            // 如果没有授权数据，则记录错误并停止服务
            Log.e(TAG, "授权数据为空，无法启动录屏会话")
            stopSelf()
        }
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

    /**
     * [功能] 捕获屏幕截图并获取UI XML，然后将它们都保存到本地文件。
     */
    private fun captureAndDumpLocally() {
        if (imageReader == null) {
            Log.e(TAG, "截图失败，录屏会话未启动")
            mainHandler.post { Toast.makeText(this, "截图服务未就绪", Toast.LENGTH_SHORT).show() }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val image: Image? = try {
                imageReader!!.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "获取图像失败", e)
                null
            }

            if (image == null) {
                Log.e(TAG, "无法获取最新图像")
                mainHandler.post { Toast.makeText(applicationContext, "截图失败", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            val timestamp = System.currentTimeMillis()

            val bitmap = imageToBitmap(image)
            image.close()
            val imagePath = saveBitmapToFile(bitmap, timestamp)

            val layoutXml = TaskifyAccessibilityService.instance?.getLayoutXml()
            val xmlPath = if (layoutXml != null) {
                saveXmlToFile(layoutXml, timestamp)
            } else {
                Log.e(TAG, "获取 UI XML 失败")
                null
            }

            mainHandler.post {
                var message = ""
                message += if (imagePath != null) "截图已保存至 Pictures\n" else "截图保存失败\n"
                message += if (xmlPath != null) "XML 已保存至 Documents" else "XML 保存失败"
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, message.replace("\n", ", "))
            }
        }
    }

    /**
     * [核心功能] 捕获屏幕、获取UI XML、打包并通过API上传到服务器
     */
    private fun captureAndUploadState() {
        if (imageReader == null || mediaProjection == null) {
            Log.e(TAG, "上传失败，截图会话未就绪")
            mainHandler.post { Toast.makeText(this, "截图服务未就绪", Toast.LENGTH_SHORT).show() }
            return
        }

        mainHandler.post { Toast.makeText(applicationContext, "正在采集数据...", Toast.LENGTH_SHORT).show() }

        CoroutineScope(Dispatchers.IO).launch {
            val image: Image? = try {
                imageReader!!.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "获取图像失败", e)
                null
            }

            if (image == null) {
                Log.e(TAG, "无法获取最新图像")
                mainHandler.post { Toast.makeText(applicationContext, "截图失败", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            // 1. 处理截图 -> Base64
            val bitmap = imageToBitmap(image)
            image.close()
            val imageBase64 = bitmapToBase64(bitmap)

            // 2. 获取 UI XML
            val layoutXml = TaskifyAccessibilityService.instance?.getLayoutXml()

            if (layoutXml == null) {
                Log.e(TAG, "获取 UI XML 失败")
                mainHandler.post { Toast.makeText(applicationContext, "获取UI布局失败", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            // 3. 准备上传数据
            val payload = UiStatePayload(imageBase64 = imageBase64, layoutXml = layoutXml)
            Log.d(TAG, "数据采集完毕，准备上传...")
            mainHandler.post { Toast.makeText(applicationContext, "正在上传...", Toast.LENGTH_SHORT).show() }

            // 4. 执行网络请求
            ApiClient.instance.uploadUiState(payload).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    val message = if (response.isSuccessful) {
                        "上报成功！"
                    } else {
                        "上报失败: ${response.code()} - ${response.message()}"
                    }
                    Log.d(TAG, message)
                    mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    val message = "网络请求失败: ${t.message}"
                    Log.e(TAG, message, t)
                    mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
                }
            })
        }
    }

    // 停止并清理所有资源
    private fun stopAndCleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(projectionCallback)
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
        var bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding > 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // 适当压缩以减小体积
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * [功能] 将截图保存到应用的 Pictures 目录
     */
    private fun saveBitmapToFile(bitmap: Bitmap, timestamp: Long): String? {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (dir == null) {
            Log.e(TAG, "无法访问外部存储目录 (Pictures)")
            return null
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "capture_${timestamp}.png")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * [功能] 将 XML 字符串保存到应用的 Documents 目录。
     */
    private fun saveXmlToFile(xmlContent: String, timestamp: Long): String? {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null) {
            Log.e(TAG, "无法访问外部存储目录 (Documents)")
            return null
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "capture_${timestamp}.xml")
        return try {
            file.writeText(xmlContent)
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "ScreenshotChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Screenshot Service Channel", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TaskifyToolkit 服务")
            .setContentText("已就绪，可接收远程指令")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(123, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}