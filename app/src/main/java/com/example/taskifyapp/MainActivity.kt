package com.example.taskifyapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // [新增] 定义一个启动器，用于请求通知权限并处理用户的选择结果
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // 如果用户拒绝，可以给一个提示
            Toast.makeText(this, "需要通知权限才能正常显示服务状态", Toast.LENGTH_SHORT).show()
        }
    }

    // 定义一个启动器，用于请求屏幕捕捉权限并处理结果
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 将授权结果作为 “Extra” 放入 Intent，然后启动服务
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_START
                putExtra(ScreenshotService.EXTRA_RESULT_DATA, result.data)
            }
            startService(serviceIntent)
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // [修正] 在 Activity 创建时，就请求通知权限
        requestNotificationPermission()

        // 按钮1：开启无障碍服务
        findViewById<Button>(R.id.button_request_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 按钮2：启动截图服务并授权
        findViewById<Button>(R.id.button_request_screenshot).setOnClickListener {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        // 按钮3：执行单次截图
        findViewById<Button>(R.id.button_take_screenshot).setOnClickListener {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                action = ScreenshotService.ACTION_CAPTURE
            }
            startService(serviceIntent)
        }

        // 按钮4：测试点击功能
        findViewById<Button>(R.id.button_test_click).setOnClickListener {
            val service = TaskifyAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val targetText = findViewById<EditText>(R.id.edit_text_target).text.toString()
            val success = service.clickByText(targetText)
            Toast.makeText(this, "点击测试: " + if(success) "成功" else "失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 封装了请求通知权限的逻辑
     */
    private fun requestNotificationPermission() {
        // 只在 Android 13 (API 33) 及以上版本需要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 使用我们新定义的 launcher 来请求权限
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}