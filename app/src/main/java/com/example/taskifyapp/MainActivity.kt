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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val PORT = 8080
    private var webServer: WebServer? = null

    // UI Elements
    private lateinit var serverStatusTextView: TextView
    private lateinit var toggleServerButton: Button
    private lateinit var targetEditText: TextInputEditText


    // ActivityResultLaunchers for handling permissions
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
            startForegroundService(serviceIntent) // Use startForegroundService for services
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        serverStatusTextView = findViewById(R.id.text_server_status)
        toggleServerButton = findViewById(R.id.button_toggle_server)
        targetEditText = findViewById(R.id.edit_text_target)

        // Request necessary permissions on startup
        requestNotificationPermission()

        // Setup button listeners
        setupButtons()
    }

    private fun setupButtons() {
        // Service and Permission Buttons
        findViewById<Button>(R.id.button_request_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.button_request_screenshot).setOnClickListener {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        // Test Action Buttons
        findViewById<Button>(R.id.button_test_click).setOnClickListener {
            val service = TaskifyAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val targetText = targetEditText.text.toString()
            if (targetText.isNotBlank()) {
                val success = service.clickByText(targetText)
                Toast.makeText(this, "点击测试: " + if (success) "成功" else "失败", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入目标文字", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.button_take_screenshot).setOnClickListener {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_CAPTURE
            }
            startService(serviceIntent)
        }

        // Server Toggle Button
        toggleServerButton.setOnClickListener {
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
            Log.d(TAG, "Server started on port $PORT")
            updateServerStatus(true)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server", e)
            Toast.makeText(this, "启动服务器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        webServer?.stop()
        Log.d(TAG, "Server stopped.")
        updateServerStatus(false)
    }

    private fun updateServerStatus(isRunning: Boolean) {
        runOnUiThread {
            if (isRunning) {
                val ipAddress = getIpAddress()
                serverStatusTextView.text = "服务器运行中\nIP: $ipAddress:$PORT"
                toggleServerButton.text = "停止服务器"
                toggleServerButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))
            } else {
                serverStatusTextView.text = "服务器状态: 已停止"
                toggleServerButton.text = "启动服务器"
                toggleServerButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, theme))
            }
        }
    }

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
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

    // Inner class for the Web Server
    inner class WebServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            // We only accept POST requests to /action
            if (Method.POST == session.method && "/action" == session.uri) {
                val postData = mutableMapOf<String, String>()
                try {
                    session.parseBody(postData)
                    val jsonBody = postData["postData"]
                    if (jsonBody != null) {
                        return handleActionRequest(jsonBody)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing request body", e)
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"status":"error", "message":"Bad Request: ${e.message}"}""")
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }

        private fun handleActionRequest(jsonBody: String): Response {
            return try {
                val json = JSONObject(jsonBody)
                val actionType = json.getString("type")
                var success = false
                var message = ""

                // Use coroutine to run UI-related tasks on the main thread
                val job = CoroutineScope(Dispatchers.Main).launch {
                    when (actionType) {
                        "click" -> {
                            val text = json.optString("text")
                            if (text.isNotEmpty()) {
                                success = TaskifyAccessibilityService.instance?.clickByText(text) ?: false
                                message = if(success) "Click action successful for text: $text" else "Click action failed for text: $text"
                            } else {
                                message = "Missing 'text' parameter for click action"
                            }
                        }
                        // Add more actions like "screenshot", "inputText" here in the future
                        else -> {
                            message = "Unknown action type: $actionType"
                        }
                    }
                }

                // Block until the coroutine is complete to get the result
                runBlocking { job.join() }

                val responseJson = """{"status":"${if(success) "ok" else "error"}", "message":"$message"}"""
                newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing action", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"status":"error", "message":"Internal Server Error: ${e.message}"}""")
            }
        }
    }

    // Need to add this to make runBlocking work
    private fun runBlocking(block: suspend CoroutineScope.() -> Unit) = kotlinx.coroutines.runBlocking(Dispatchers.Main, block)

}
