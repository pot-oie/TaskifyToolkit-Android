package com.example.taskifyapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.taskifyapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 用于处理截图权限的 ActivityResultLauncher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 用户授予权限后，启动后台服务，并将授权令牌(result.data)传递过去
            val serviceIntent = Intent(this, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_START
                // 将 MediaProjection 的授权数据放入 Intent
                putExtra(WebSocketService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "后台服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
    }

    private fun setupButtons() {
        binding.buttonRequestAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 点击“启动服务”按钮时，会先弹出系统对话框请求截图权限
        binding.buttonStartService.setOnClickListener {
            Toast.makeText(this, "请授予截图权限以启动服务", Toast.LENGTH_LONG).show()
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
}
