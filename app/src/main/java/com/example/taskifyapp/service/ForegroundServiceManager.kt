package com.example.taskifyapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.taskifyapp.R

object ForegroundServiceManager {

    private const val NOTIFICATION_CHANNEL_ID = "WebSocketChannel"
    private const val NOTIFICATION_ID = 1

    /**
     * 创建前台服务所需的通知渠道。
     * 理想情况下，此方法应在 Application.onCreate() 中调用一次。
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Taskify 连接服务",
                NotificationManager.IMPORTANCE_LOW // 使用 LOW，避免通知发出声音
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 将一个服务提升为前台服务。
     * @param service 要提升的 Service 实例。
     * @param contentText 通知上要显示的文本。
     */
    fun startForeground(service: Service, contentText: String) {
        val notification = NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TaskifyToolkit 后台服务")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // 使通知不可清除
            .build()
        service.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 更新现有前台服务的通知文本。
     * @param context Context 对象。
     * @param contentText 新的通知文本。
     */
    fun updateNotification(context: Context, contentText: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TaskifyToolkit 后台服务")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}