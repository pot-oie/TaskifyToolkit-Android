package com.example.taskifyapp.model

/**
 * 数据类，封装和传递应用的配置信息。
 *
 * @property serverUrl 完整的WebSocket服务器地址，例如 "ws://192.168.1.5:8080/agent-ws"
 * @property autoReconnect 是否开启自动重连
 * @property serverIp 仅包含IP地址部分，用于在UI上显示，例如 "192.168.1.5"
 */
data class Settings(
    val serverUrl: String,
    val autoReconnect: Boolean,
    val serverIp: String
)