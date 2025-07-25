package com.example.taskifyapp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.taskifyapp.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private val UI_DEBUG_TAG = "UI_DEBUG" // 新增一个专用的TAG

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

    // --- 更新WebSocket状态 ---
    private fun updateWebSocketStatus() {
        val (text, state) = WebSocketService.getCurrentStatus()
        Log.d(UI_DEBUG_TAG, "[ACTIVITY] updateWebSocketStatus (from onResume) - 查询到当前状态: $text, state: $state")
        binding.statusWebsocket.text = text

        when (state) {
            WebSocketService.STATE_ON -> {
                binding.statusWebsocket.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                binding.iconWebsocketStatus.setImageResource(R.drawable.ic_correct)
            }
            WebSocketService.STATE_ERROR -> {
                binding.statusWebsocket.setTextColor(ContextCompat.getColor(this, R.color.status_red))
                binding.iconWebsocketStatus.setImageResource(R.drawable.ic_bigx_red)
            }
            // 默认情况，包括 STATE_OFF, 未启动等
            else -> {
                binding.statusWebsocket.setTextColor(ContextCompat.getColor(this, R.color.status_grey))
                binding.iconWebsocketStatus.setImageResource(R.drawable.ic_bigx_grey)
            }
        }
    }

    // --- 广播接收器 ---
    private val statusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(UI_DEBUG_TAG, "[ACTIVITY] onReceive - 广播已收到！")

            if (intent?.action == WebSocketService.ACTION_STATUS_UPDATE) {
                updateAllUI() // 收到广播后，也更新所有UI
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtonListeners()
        setupTabs()
    }

    override fun onResume() {
        super.onResume()
        Log.d(UI_DEBUG_TAG, "[ACTIVITY] onResume - 准备查询状态并更新UI")

        // --- 注册广播接收器 ---
        val intentFilter = IntentFilter(WebSocketService.ACTION_STATUS_UPDATE)

        // 注册广播
        LocalBroadcastManager.getInstance(this).registerReceiver(statusUpdateReceiver, intentFilter)

        // --- 在注册广播后，立即主动查询并更新一次UI ---
        updateWebSocketStatus()

        // 每次恢复时，主动检查一次无障碍服务状态
        updateAccessibilityStatus()

        // 每次恢复时，都更新UI状态
        updateAllUI()
    }

    override fun onPause() {
        super.onPause()
        // --- 新增：注销广播接收器以防内存泄漏 ---
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusUpdateReceiver)    }

    // --- UI更新入口 ---
    private fun updateAllUI() {
        updateAccessibilityStatus()
        updateWebSocketStatus()
        updateButtonStates() // 关键：更新按钮状态
    }

    // --- 根据服务状态，动态更新按钮 ---
    private fun updateButtonStates() {
        // --- 更新无障碍服务按钮 ---
        val isAccessibilityOn = isAccessibilityServiceEnabled()
        if (isAccessibilityOn) {
            binding.buttonAccessibilityToggle.text = "1. 关闭无障碍服务"
            // 这里可以保持 OutlinedButton 的默认样式，或者自定义一个“关闭”状态的样式
        } else {
            binding.buttonAccessibilityToggle.text = "1. 开启无障碍服务"
        }

        // --- 更新后台服务按钮 ---
        val (_, serviceState) = WebSocketService.getCurrentStatus()
        if (serviceState == WebSocketService.STATE_ON) {
            binding.buttonServiceToggle.text = "2. 关闭后台服务"
            // --- 按钮颜色变为灰色系 ---
            binding.buttonServiceToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_teal))
        } else {
            binding.buttonServiceToggle.text = "2. 启动后台服务"
            // 恢复按钮为主题默认的紫色
            binding.buttonServiceToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_purple))
        }
    }

    // --- 只负责设置点击事件，不关心状态 ---
    private fun setupButtonListeners() {
        // 无障碍服务按钮：无论当前是开启还是关闭，点击都是跳转到设置页
        binding.buttonAccessibilityToggle.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 后台服务按钮：根据当前状态决定是启动还是停止
        binding.buttonServiceToggle.setOnClickListener {
            val (_, serviceState) = WebSocketService.getCurrentStatus()
            if (serviceState == WebSocketService.STATE_ON) {
                // 如果服务正在运行，则停止它
                val serviceIntent = Intent(this, WebSocketService::class.java)
                stopService(serviceIntent)
                Toast.makeText(this, "后台服务已停止", Toast.LENGTH_SHORT).show()
            } else {
                // 如果服务未运行，则启动它
                Toast.makeText(this, "请授予截图权限以启动服务", Toast.LENGTH_LONG).show()
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            }
            // 用户点击后，立即手动延迟刷新一次UI，确保状态的及时反馈
            // 延迟一点时间等待Service状态变更的广播发出
            Handler(Looper.getMainLooper()).postDelayed({
                updateAllUI()
            }, 100)
        }
    }

    private fun setupTabs() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "实时日志"
                1 -> "高级设置"
                else -> "设备信息"
            }
        }.attach()
    }

    // --- 更新无障碍服务状态 ---
    private fun updateAccessibilityStatus() {
        if (isAccessibilityServiceEnabled()) {
            binding.statusAccessibility.text = "已启动"
            binding.statusAccessibility.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.iconAccessibilityStatus.setImageResource(R.drawable.ic_correct)
        } else {
            binding.statusAccessibility.text = "未启动"
            binding.statusAccessibility.setTextColor(ContextCompat.getColor(this, R.color.status_grey))
            binding.iconAccessibilityStatus.setImageResource(R.drawable.ic_bigx_grey)
        }
    }

    // 检查无障碍服务是否开启的辅助函数
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + TaskifyAccessibilityService::class.java.canonicalName
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return accessibilityEnabled == 1 && settingValue?.split(':')?.any { it.equals(service, ignoreCase = true) } == true
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("MainActivity", "检查无障碍服务状态时出错", e)
            return false
        }
    }
}
