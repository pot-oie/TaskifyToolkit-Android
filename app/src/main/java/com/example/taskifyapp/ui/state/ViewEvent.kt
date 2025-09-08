package com.example.taskifyapp.ui.state

/**
 * 定义了ViewModel可以向UI层发送的一次性事件。
 * 使用密封类可以确保处理时是类型安全的。
 */
sealed class ViewEvent {
    /** 请求Activity启动屏幕录制权限的流程 */
    object RequestScreenCapture : ViewEvent()

    /** 请求Activity跳转到系统的无障碍设置页面 */
    object OpenAccessibilitySettings : ViewEvent()

    /** 请求显示一个Toast消息 */
    data class ShowToast(val message: String) : ViewEvent()
}