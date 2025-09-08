package com.example.taskifyapp.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.core.content.edit
import com.example.taskifyapp.service.WebSocketService
import com.example.taskifyapp.model.ServiceState
import com.example.taskifyapp.model.Settings
import com.example.taskifyapp.util.AccessibilityUtils
import com.example.taskifyapp.util.IpValidator
import com.example.taskifyapp.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 一次性返回所有状态的快照
data class CurrentStatus(
    val wsState: ServiceState,
    val isAccessibilityOn: Boolean
)

/**
 * 应用的唯一数据来源 (Single Source of Truth)。
 * 负责管理所有数据，包括本地设置和后台服务状态，并向上层(ViewModel)提供干净的数据流。
 */
class AgentRepository(private val context: Context) {

    // --- 数据源和内部状态 ---

    private val prefs: SharedPreferences = context.getSharedPreferences("TaskifySettings", Context.MODE_PRIVATE)

    // 使用 StateFlow 来持有和广播当前的设置。当设置变化时，UI会自动更新。
    private val _settingsFlow = MutableStateFlow(loadSettingsFromPrefs())
    val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    // --- 公开的数据流 (供ViewModel使用) ---
    val webSocketState: StateFlow<ServiceState> = WebSocketService.serviceState
    val logFlow = LogManager.logFlow

    /**
     * 命令式的状态获取方法，直接查询并返回包含所有当前状态的快照。
     * 这是“安全模式”的核心，避免了复杂的响应式流可能带来的初始化问题。
     */
    fun getCurrentStatusSnapshot(): CurrentStatus {
        return CurrentStatus(
            wsState = webSocketState.value,
            isAccessibilityOn = AccessibilityUtils.isAccessibilityServiceEnabled(context)
        )
    }

    // --- 公开的业务方法 (供ViewModel调用) ---
    /**
     * 检查无障碍服务是否启用。
     * 直接调用我们之前创建的工具类。
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityUtils.isAccessibilityServiceEnabled(context)
    }

    /**
     * 保存用户输入的新设置。
     * @param newIp 用户输入的新IP地址
     * @param newAutoReconnect 用户选择的是否自动重连
     */
    fun saveSettings(newIp: String, newAutoReconnect: Boolean) {
        if (!IpValidator.isValidIp(newIp)) {
            Toast.makeText(context, "请输入有效的IP地址", Toast.LENGTH_SHORT).show()
            return
        }

        val fullUrl = buildFullUrl(newIp)
        prefs.edit {
            putString("server_url", fullUrl)
            putBoolean("auto_reconnect", newAutoReconnect)
        }

        // 更新 StateFlow，任何监听它的地方都会收到新值
        _settingsFlow.value = Settings(fullUrl, newAutoReconnect, newIp)
        Toast.makeText(context, "新的IP地址已保存", Toast.LENGTH_SHORT).show()
    }

    /**
     * 在获得屏幕录制权限后，正式启动后台服务。
     * @param permissionData 包含屏幕录制授权的 Intent
     */
    fun startAgentServiceWithPermission(permissionData: Intent) {
        val settings = _settingsFlow.value
        val serviceIntent = Intent(context, WebSocketService::class.java).apply {
            action = WebSocketService.ACTION_START
            // 将配置信息通过 Intent 传递给 Service
            putExtra(WebSocketService.EXTRA_RESULT_DATA, permissionData)
            putExtra("EXTRA_URL", settings.serverUrl)
            putExtra("EXTRA_RECONNECT", settings.autoReconnect)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        Toast.makeText(context, "后台服务已启动", Toast.LENGTH_SHORT).show()
    }

    /**
     * 停止后台服务。
     */
    fun stopAgentService() {
        val serviceIntent = Intent(context, WebSocketService::class.java)
        context.stopService(serviceIntent)
        Toast.makeText(context, "后台服务已停止", Toast.LENGTH_SHORT).show()
    }


    // --- 私有辅助方法 ---

    /**
     * 从 SharedPreferences 加载设置。
     */
    private fun loadSettingsFromPrefs(): Settings {
        val url = prefs.getString("server_url", DEFAULT_WEBSOCKET_URL)!!
        val ip = url.substringAfter("ws://").substringBefore(":")
        val reconnect = prefs.getBoolean("auto_reconnect", true)
        return Settings(url, reconnect, ip)
    }

    /**
     * 根据IP地址拼接完整的WebSocket URL。
     */
    private fun buildFullUrl(ip: String): String {
        // 将固定的部分定义为常量，方便管理
        val wsProtocol = "ws://"
        val serverPort = ":8080"
        val wsEndpoint = "/agent-ws"
        return "$wsProtocol$ip$serverPort$wsEndpoint"
    }

    companion object {
        private const val DEFAULT_WEBSOCKET_URL = "ws://192.168.3.15:8080/agent-ws"
    }
}