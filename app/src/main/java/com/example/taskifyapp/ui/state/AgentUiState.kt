package com.example.taskifyapp.ui.state

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.taskifyapp.R
import com.example.taskifyapp.model.Settings

/**
 * 封装了主界面所有UI元素所需状态的数据类。
 * ViewModel 将会持有这个类的一个实例，并在状态变化时更新它。
 * UI层（Activity/Fragment）只需观察这一个对象，即可获得所有需要展示的数据。
 */
data class AgentUiState(
    // WebSocket 服务状态
    val webSocketStatusText: String = "未启动",
    @ColorRes val webSocketStatusColor: Int = R.color.status_grey,
    @DrawableRes val webSocketStatusIcon: Int = R.drawable.ic_bigx_grey,

    // 无障碍服务状态
    val accessibilityStatusText: String = "未启动",
    @ColorRes val accessibilityStatusColor: Int = R.color.status_green,
    @DrawableRes val accessibilityStatusIcon: Int = R.drawable.ic_correct,

    // 核心操作按钮的状态
    val accessibilityButtonText: String = "1. 开启无障碍服务",
    val serviceButtonText: String = "2. 启动后台服务",
    @ColorRes val serviceButtonColor: Int = R.color.primary_purple,

    // "高级设置" 页面的状态
    val settings: Settings? = null,

    // "实时日志" 页面的状态
    val logs: List<String> = emptyList(),

    // 用于触发UI动画的单次事件状态
    val shouldCollapsePanels: Boolean = false
)