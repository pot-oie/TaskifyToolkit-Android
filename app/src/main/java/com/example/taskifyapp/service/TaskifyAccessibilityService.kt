package com.example.taskifyapp.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.taskifyapp.util.LayoutXmlParser

class TaskifyAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "TaskifyService"
        /**
         * 使用一个静态实例是在无障碍服务中实现外部调用的常见模式。
         * 虽然在大型应用中可以通过绑定服务(bindService)等更复杂的机制来替代，
         * 但对于当前项目，这种方式最直接且有效，因为只有WebSocketService这一个客户端。
         * 关键在于，这个实例的访问被很好地隔离在后台服务层，UI层和ViewModel对此无感知。
         */
        var instance: TaskifyAccessibilityService? = null
    }

    // 持有一个操作执行器的实例
    private lateinit var actionPerformer: AccessibilityActionPerformer

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        actionPerformer = AccessibilityActionPerformer(this)
        Log.d(TAG, "无障碍服务已连接！")
    }

    // --- 所有方法都委托给对应的管理器或执行器 ---

    fun getLayoutXml(): String? = LayoutXmlParser.getLayoutXml(this)

    fun clickByText(text: String): Boolean = actionPerformer.clickByText(text)

    fun inputTextByText(findText: String, contentToInput: String): Boolean =
        actionPerformer.inputTextByText(findText, contentToInput)

    fun longClickByText(text: String): Boolean = actionPerformer.longClickByText(text)

    fun scrollByText(text: String, direction: Int): Boolean =
        actionPerformer.scrollByText(text, direction)

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500) =
        actionPerformer.performSwipe(startX, startY, endX, endY, duration)

    fun performGlobalBack(): Boolean = actionPerformer.performGlobalBack()

    // --- 服务生命周期方法 ---
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

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