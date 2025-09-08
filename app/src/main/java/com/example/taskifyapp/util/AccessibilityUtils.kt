package com.example.taskifyapp.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.taskifyapp.service.TaskifyAccessibilityService

/**
 * 封装了与无障碍服务相关的辅助函数
 */
object AccessibilityUtils {
    private const val TAG = "AccessibilityUtils"

    /**
     * 检查本应用的无障碍服务是否已经开启并正在运行。
     * @param context Context 对象，用于访问 ContentResolver 和包名。
     * @return 如果服务已启用则返回 true，否则返回 false。
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = context.packageName + "/" + TaskifyAccessibilityService::class.java.canonicalName
        try {
            // 检查无障碍总开关是否开启
            val accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            if (accessibilityEnabled != 1) {
                return false
            }

            // 检查我们的服务是否在已启用的服务列表中
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            return settingValue?.split(':')?.any { it.equals(serviceName, ignoreCase = true) } == true

        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "检查无障碍服务状态时出错", e)
            return false
        }
    }
}