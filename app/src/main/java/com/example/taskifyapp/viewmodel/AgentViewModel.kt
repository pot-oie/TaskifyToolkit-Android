package com.example.taskifyapp.viewmodel

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskifyapp.R
import com.example.taskifyapp.model.ServiceState
import com.example.taskifyapp.repository.AgentRepository
import com.example.taskifyapp.ui.state.AgentUiState
import com.example.taskifyapp.ui.state.ViewEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App的核心ViewModel，负责：
 * 1. 从 AgentRepository 获取数据。
 * 2. 将数据转换为UI可以直接消费的 AgentUiState。
 * 3. 接收UI事件，并调用Repository执行相应的业务逻辑。
 * 4. 管理所有与UI相关的状态和业务逻辑，Activity/Fragment 只负责展示和转发事件。
 */
class AgentViewModel(private val repository: AgentRepository) : ViewModel() {

    // 私有的、可变的 StateFlow，用于持有UI状态
    private val _uiState = MutableStateFlow(AgentUiState())
    // 公开的、只读的 StateFlow，供UI层观察
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    // 私有的、可变的 SharedFlow，用于发送一次性UI事件
    private val _events = MutableSharedFlow<ViewEvent>()
    // 公开的、只读的 SharedFlow，供UI层观察
    val events = _events.asSharedFlow()

    // 刷新触发器，不带值的SharedFlow
    private val refreshTrigger = MutableSharedFlow<Unit>()

    init {
        // ViewModel初始化时，构建一个完整的、健壮的响应式数据流
        observeAllDataSources()
    }

    private fun observeAllDataSources() {
        viewModelScope.launch {
            // 将 refreshTrigger 合并进来。
            // combine 会在任何一个源 flow 发射数据时重新计算。
            combine(
                repository.webSocketState,
                repository.logFlow.scan(emptyList<String>()) { acc, value -> acc + value }, // 将日志流转换为列表
                repository.settingsFlow,
                refreshTrigger.onStart { emit(Unit) } // onStart 会让 combine 在开始时立即触发一次
            ) { wsState, logs, settings, _ ->
                // 在这里获取非响应式的快照数据
                val isAccessibilityOn = repository.isAccessibilityServiceEnabled()

                // --- 状态映射逻辑 ---
                val accessibilityStatusText = if (isAccessibilityOn) "已启动" else "未启动"
                val accessibilityStatusColor = if (isAccessibilityOn) R.color.status_green else R.color.status_grey
                val accessibilityStatusIcon = if (isAccessibilityOn) R.drawable.ic_correct else R.drawable.ic_bigx_grey
                val accessibilityButtonText = if (isAccessibilityOn) "1. 无障碍服务正常" else "1. 开启无障碍服务"

                val webSocketStatusText: String
                val webSocketStatusColor: Int
                val webSocketStatusIcon: Int
                val serviceButtonText: String
                val serviceButtonColor: Int

                when (wsState) {
                    is ServiceState.Connected -> {
                        webSocketStatusText = "已连接"
                        webSocketStatusColor = R.color.status_green
                        webSocketStatusIcon = R.drawable.ic_correct
                        serviceButtonText = "2. 关闭后台服务"
                        serviceButtonColor = R.color.accent_teal
                    }
                    else -> {
                        webSocketStatusText = when (wsState) {
                            is ServiceState.Initializing -> "正在初始化..."
                            is ServiceState.Reconnecting -> "尝试重连中..."
                            is ServiceState.Disconnected -> "连接已关闭"
                            is ServiceState.Error -> wsState.message
                            else -> "未启动"
                        }
                        webSocketStatusColor = if (wsState is ServiceState.Error) R.color.status_red else R.color.status_grey
                        webSocketStatusIcon = if (wsState is ServiceState.Error) R.drawable.ic_bigx_red else R.drawable.ic_bigx_grey
                        serviceButtonText = "2. 启动后台服务"
                        serviceButtonColor = R.color.primary_purple
                    }
                }

                // --- 返回完整的UI State ---
                AgentUiState(
                    webSocketStatusText = webSocketStatusText,
                    webSocketStatusColor = webSocketStatusColor,
                    webSocketStatusIcon = webSocketStatusIcon,
                    accessibilityStatusText = accessibilityStatusText,
                    accessibilityStatusColor = accessibilityStatusColor,
                    accessibilityStatusIcon = accessibilityStatusIcon,
                    accessibilityButtonText = accessibilityButtonText,
                    serviceButtonText = serviceButtonText,
                    serviceButtonColor = serviceButtonColor,
                    settings = settings,
                    logs = logs // 直接使用从logFlow转换来的列表
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * 触发刷新。
     */
    fun refreshStates() {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    // --- 响应UI事件的方法 ---

    /** 当后台服务按钮被点击时调用 */
    fun onServiceToggleClicked() {
        if (repository.webSocketState.value is ServiceState.Connected) {
            repository.stopAgentService()
        } else {
            if (!repository.isAccessibilityServiceEnabled()) {
                viewModelScope.launch { _events.emit(ViewEvent.ShowToast("请先开启无障碍服务")) }
                return
            }
            // 通知UI去请求截图权限
            viewModelScope.launch { _events.emit(ViewEvent.RequestScreenCapture) }
        }
    }

    /** 当无障碍服务按钮被点击时调用 */
    fun onAccessibilityToggleClicked() {
        viewModelScope.launch { _events.emit(ViewEvent.OpenAccessibilitySettings) }
    }

    /** 当设置页面的保存按钮被点击时调用 */
    fun onSaveSettingsClicked(ip: String, autoReconnect: Boolean) {
        repository.saveSettings(ip, autoReconnect)
    }

    /** Activity返回屏幕录制权限结果时调用 */
    fun onScreenCapturePermissionResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            repository.startAgentServiceWithPermission(data)
        } else {
            viewModelScope.launch { _events.emit(ViewEvent.ShowToast("截图权限被拒绝")) }
        }
    }

    /** 当高级设置页的输入框获得/失去焦点时调用 */
    fun onEditTextFocused(isFocused: Boolean) {
        _uiState.update { it.copy(shouldCollapsePanels = isFocused) }
    }

    /** 当折叠面板的动画执行完毕后，UI层调用此方法来重置事件状态 */
    fun onPanelsCollapsed() {
        _uiState.update { it.copy(shouldCollapsePanels = false) }
    }
}