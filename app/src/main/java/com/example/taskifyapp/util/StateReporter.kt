package com.example.taskifyapp.util

import android.util.Log
import com.example.taskifyapp.service.TaskifyAccessibilityService
import org.json.JSONObject

/**
 * 单例对象，负责采集屏幕状态（截图+XML）并准备数据载荷。
 */
object StateReporter {

    private const val TAG = "StateReporter"

    /**
     * 采集屏幕状态并准备要发送的JSON数据包。
     * @param lastActionResult 上一个动作的执行结果，可以为null。
     * @return 准备好的JSONObject数据包，如果任何一步失败则返回null。
     */
    fun captureAndPreparePayload(lastActionResult: String?): JSONObject? {
        LogManager.log("开始采集屏幕信息...")

        // 1. 获取截图
        val originalBitmap = ScreenCaptureManager.captureCurrentImage()
        if (originalBitmap == null) {
            Log.e(TAG, "采集屏幕图像失败")
            return null
        }

        // 2. 获取无障碍服务实例
        val accessibilityService = TaskifyAccessibilityService.instance
        if (accessibilityService == null) {
            Log.e(TAG, "采集失败: 无障碍服务未连接")
            return null
        }

        // 3. 处理数据并构建JSON
        return try {
            val resizedBitmap = ImageUtils.resizeBitmapByWidth(originalBitmap, 720)
            val imageBase64 = ImageUtils.bitmapToBase64(resizedBitmap)
            val layoutXml = accessibilityService.getLayoutXml() ?: ""

            Log.d(TAG, "Base64 长度: ${imageBase64.length}, XML 长度: ${layoutXml.length}")

            JSONObject().apply {
                put("imageBase64", imageBase64)
                put("layoutXml", layoutXml)
                if (lastActionResult != null) {
                    put("lastActionResult", lastActionResult)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "数据处理时发生严重错误!", e)
            null
        }
    }
}