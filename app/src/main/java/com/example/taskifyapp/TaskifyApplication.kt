package com.example.taskifyapp

import android.app.Application
import com.example.taskifyapp.service.ForegroundServiceManager
import com.example.taskifyapp.util.LogManager

class TaskifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 在应用启动时初始化日志管理器
        LogManager.initialize(this)

        // 在应用启动时创建前台服务的通知渠道
        ForegroundServiceManager.createNotificationChannel(this)
    }
}