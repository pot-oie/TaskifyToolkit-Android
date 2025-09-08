package com.example.taskifyapp.model

/**
 * 使用密封类 (sealed class) 来定义 WebSocketService 的所有可能状态。
 * 这使得在处理状态时，编译器可以进行详尽的检查（when语句无需else分支）。
 */
sealed class ServiceState {
    /** 服务处于空闲、未启动或已正常停止的状态 */
    object Idle : ServiceState()

    /** 服务正在初始化，例如，正在等待屏幕录制权限 */
    object Initializing : ServiceState()

    /** WebSocket 已成功连接到服务器 */
    data class Connected(val message: String = "已连接") : ServiceState()

    /** 连接已断开，可能是正常的关闭或意外断开 */
    data class Disconnected(val reason: String = "连接已关闭") : ServiceState()

    /** 服务或连接过程中发生错误 */
    data class Error(val message: String, val throwable: Throwable? = null) : ServiceState()

    /** 正在尝试重新连接服务器 */
    object Reconnecting : ServiceState()
}