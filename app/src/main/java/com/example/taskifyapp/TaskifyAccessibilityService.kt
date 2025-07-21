package com.example.taskifyapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

class TaskifyAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "TaskifyService"
        var instance: TaskifyAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接！")
    }

    /**
     * [功能] 查找并点击包含特定文本的第一个可点击节点
     * @param text 要查找的文本
     * @return 如果找到并成功点击则返回 true，否则返回 false
     */
    fun clickByText(text: String): Boolean {
        // 获取当前活动窗口的根节点
        val rootNode = getRootInActiveWindow() ?: return false
        // 根据文本查找所有匹配的节点
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        // 及时回收根节点资源
        rootNode.recycle()

        // 查找第一个可点击的节点并执行点击
        nodes?.firstOrNull { it?.isClickable == true }?.let { node ->
            Log.d(TAG, "通过文本找到并点击节点: $text")
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            return result
        }
        Log.w(TAG, "未找到可点击的文本节点: $text")
        return false
    }

    /**
     * [功能] 在指定的输入框节点中输入文本
     */
    fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) {
            Log.w(TAG, "节点不可编辑")
            return false
        }
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        Log.d(TAG, "在节点中输入文本: $text")
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * [功能] 模拟长按操作
     */
    fun longClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isLongClickable) {
            Log.d(TAG, "执行长按操作")
            return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }
        return false
    }

    /**
     * [功能] 模拟精确滑动操作
     * 需要 Android 7.0 (API 24) 或以上
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        dispatchGesture(gestureBuilder.build(), null, null)
        Log.d(TAG, "执行滑动操作")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 为避免内存泄漏，及时回收事件源节点
        event?.source?.recycle()
    }

    /**
     * 当服务被系统中断时调用
     */
    override fun onInterrupt() {
        // 当服务中断时，清空静态实例
        instance = null
        Log.d(TAG, "无障碍服务已中断！")
    }

    /**
     * 当服务被销毁时调用
     */
    override fun onDestroy() {
        super.onDestroy()
        // 当服务销毁时，清空静态实例
        instance = null
        Log.d(TAG, "无障碍服务已销毁。")
    }
}