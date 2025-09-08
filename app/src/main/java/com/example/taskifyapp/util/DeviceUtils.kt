package com.example.taskifyapp.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.util.*

object DeviceUtils {

    /**
     * 获取设备型号
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * 获取安卓系统版本号
     */
    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE
    }

    /**
     * 获取设备的IP地址 (从 InfoFragment 移入)
     * @param context Context 对象
     * @return IP地址字符串，或错误/未连接信息
     */
    fun getDeviceIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress == 0) return "未连接到WiFi"
            String.format(
                Locale.getDefault(), "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) {
            "获取失败"
        }
    }
}