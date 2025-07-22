package com.example.taskifyapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.taskifyapp.databinding.ActivityMainBinding
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val PORT = 8081
    private var webServer: WebServer? = null
    private lateinit var binding: ActivityMainBinding

    // 用于处理权限的 ActivityResultLauncher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "需要通知权限才能正常显示服务状态", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_START
                putExtra(ScreenshotService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "截图服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestNotificationPermission()
        setupButtons()
    }

    private fun setupButtons() {
        binding.buttonRequestAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.buttonRequestScreenshot.setOnClickListener {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        binding.buttonTestClick.setOnClickListener {
            val service = TaskifyAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val targetText = binding.editTextTarget.text.toString()
            if (targetText.isNotBlank()) {
                CoroutineScope(Dispatchers.Default).launch {
                    val success = service.clickByText(targetText)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "点击测试: " + if (success) "成功" else "失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "请输入目标文字", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonCaptureAndSaveLocally.setOnClickListener {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_CAPTURE_AND_DUMP_LOCALLY
            }
            startService(serviceIntent)
        }

        binding.buttonCaptureAndUpload.setOnClickListener {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_CAPTURE_AND_UPLOAD
            }
            startService(serviceIntent)
        }

        binding.buttonToggleServer.setOnClickListener {
            if (webServer?.isAlive == true) {
                stopServer()
            } else {
                startServer()
            }
        }
    }

    private fun startServer() {
        try {
            webServer = WebServer()
            webServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "服务器已在端口 $PORT 启动")
            updateServerStatus(true)
        } catch (e: IOException) {
            Log.e(TAG, "启动服务器失败", e)
            runOnUiThread {
                Toast.makeText(this, "启动服务器失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopServer() {
        webServer?.stop()
        Log.d(TAG, "服务器已停止运行")
        updateServerStatus(false)
    }

    private fun updateServerStatus(isRunning: Boolean) {
        runOnUiThread {
            if (isRunning) {
                val ipAddress = getIpAddress()
                binding.textServerStatus.text = "服务器运行中\nIP: $ipAddress:$PORT"
                binding.buttonToggleServer.text = "停止服务器"
            } else {
                binding.textServerStatus.text = "服务器状态: 已停止"
                binding.buttonToggleServer.text = "启动服务器"
            }
        }
    }

    private fun getIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) { "N/A" }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // NanoHTTPD Web服务器: 指令接收的核心
    inner class WebServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            if (Method.POST != session.method || "/action" != session.uri) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"status":"error", "message":"Not Found"}""")
            }

            // 采用更健壮的方式读取请求体
            val bodyFiles = mutableMapOf<String, String>()
            val body: String
            try {
                session.parseBody(bodyFiles)
                // 读取完整的请求体为字符串
                body = bodyFiles[session.queryParameterString] ?: session.queryParameterString ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "解析请求体时出错", e)
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"status":"error", "message":"Bad Request: ${e.message}"}""")
            }

            if (body.isBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"status":"error", "message":"Request body is empty"}""")
            }

            return handleActionRequest(body)
        }

        private fun handleActionRequest(jsonBody: String): Response {
            try {
                val json = JSONObject(jsonBody)
                val actionType = json.getString("type")
                val service = TaskifyAccessibilityService.instance

                if (service == null && actionType != "capture") {
                    return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "application/json", """{"status":"error", "message":"AccessibilityService not connected"}""")
                }

                var responseJson = JSONObject()

                // 使用 runBlocking 确保在返回响应前，无障碍服务操作已完成
                runBlocking(Dispatchers.Main) {
                    when (actionType) {
                        "click" -> {
                            val text = json.getString("text")
                            val success = service!!.clickByText(text)
                            responseJson.put("status", if (success) "ok" else "error")
                            responseJson.put("message", "Click action on text '$text' was ${if (success) "successful" else "failed"}.")
                        }
                        "capture_and_dump_locally" -> {
                            val intent = Intent(this@MainActivity, ScreenshotService::class.java).apply { action = ScreenshotService.ACTION_CAPTURE_AND_DUMP_LOCALLY }
                            startService(intent)
                            responseJson.put("status", "ok")
                            responseJson.put("message", "Capture and dump locally command sent.")
                        }
                        "capture_and_upload" -> {
                            val intent = Intent(this@MainActivity, ScreenshotService::class.java).apply { action = ScreenshotService.ACTION_CAPTURE_AND_UPLOAD }
                            startService(intent)
                            responseJson.put("status", "ok")
                            responseJson.put("message", "Capture and upload command sent.")
                        }
                        // 在此添加更多指令...
                        else -> {
                            responseJson.put("status", "error")
                            responseJson.put("message", "Unknown action type: $actionType")
                        }
                    }
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString())
            } catch (e: Exception) {
                Log.e(TAG, "处理指令时出错", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"status":"error", "message":"Internal Server Error: ${e.message}"}""")
            }
        }
    }
}
