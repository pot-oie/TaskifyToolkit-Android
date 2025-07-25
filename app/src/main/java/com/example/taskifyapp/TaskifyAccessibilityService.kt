package com.example.taskifyapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

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
     * [功能] 获取当前活动窗口的 UI 布局，并将其序列化为 XML 字符串。
     * @return 返回包含 UI 布局的 XML 字符串，如果失败则返回 null。
     */
    fun getLayoutXml(): String? {
        val rootNode = getRootInActiveWindow() ?: return null
        try {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()
            val doc = docBuilder.newDocument()

            val rootElement = doc.createElement("hierarchy")
            doc.appendChild(rootElement)

            // 开始递归构建 XML 树
            dumpNodeToXml(rootNode, doc, rootElement)

            // 将 Document 对象转换为格式化的 XML 字符串
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))

            return writer.buffer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "生成 UI XML 时出错", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 递归辅助函数，将 AccessibilityNodeInfo 转换为 XML 元素。
     */
    private fun dumpNodeToXml(node: AccessibilityNodeInfo?, doc: Document, parentElement: Element) {
        if (node == null) return

        val element = doc.createElement(node.className?.toString() ?: "node")
        // 添加节点的各种属性
        element.setAttribute("index", node.childCount.toString())
        element.setAttribute("text", node.text?.toString() ?: "")
        element.setAttribute("resource-id", node.viewIdResourceName ?: "")
        element.setAttribute("class", node.className?.toString() ?: "")
        element.setAttribute("package", node.packageName?.toString() ?: "")
        element.setAttribute("content-desc", node.contentDescription?.toString() ?: "")
        element.setAttribute("checkable", node.isCheckable.toString())
        element.setAttribute("checked", node.isChecked.toString())
        element.setAttribute("clickable", node.isClickable.toString())
        element.setAttribute("editable", node.isEditable.toString())
        element.setAttribute("enabled", node.isEnabled.toString())
        element.setAttribute("focusable", node.isFocusable.toString())
        element.setAttribute("focused", node.isFocused.toString())
        element.setAttribute("scrollable", node.isScrollable.toString())
        element.setAttribute("long-clickable", node.isLongClickable.toString())
        element.setAttribute("password", node.isPassword.toString())
        element.setAttribute("selected", node.isSelected.toString())

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        element.setAttribute("bounds", bounds.toShortString())

        parentElement.appendChild(element)

        // 递归处理所有子节点
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            dumpNodeToXml(childNode, doc, element)
            childNode?.recycle() // 回收子节点
        }
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
     * [功能] 根据文本找到节点并输入内容
     */
    fun inputTextByText(findText: String, contentToInput: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(findText)
        rootNode.recycle()

        // 找到第一个可编辑的节点
        nodes?.firstOrNull { it.isEditable }?.let { node ->
            Log.d(TAG, "通过文本找到并输入内容: $findText -> $contentToInput")
            val result = inputText(node, contentToInput) // inputText 是您已有的方法
            node.recycle()
            nodes.forEach { it.recycle() } // 回收所有找到的节点
            return result
        }
        Log.w(TAG, "未找到可输入的节点: $findText")
        nodes?.forEach { it.recycle() }
        return false
    }

    /**
     * [功能] 根据文本找到节点并执行长按
     */
    fun longClickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        rootNode.recycle()

        // 找到第一个可长按的节点
        nodes?.firstOrNull { it.isLongClickable }?.let { node ->
            Log.d(TAG, "通过文本找到并长按节点: $text")
            val result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            node.recycle()
            nodes.forEach { it.recycle() }
            return result
        }
        Log.w(TAG, "未找到可长按的文本节点: $text")
        nodes?.forEach { it.recycle() }
        return false
    }

    /**
     * [功能] 根据文本找到节点并执行滚动
     * @param text 要查找的可滚动节点的文本
     * @param direction 滚动方向, 1 为向前(下/右), -1 为向后(上/左)
     */
    fun scrollByText(text: String, direction: Int): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        rootNode.recycle()

        val action = if (direction > 0) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        // 找到第一个可滚动的节点
        nodes?.firstOrNull { it.isScrollable }?.let { node ->
            Log.d(TAG, "通过文本找到并滚动节点: $text, direction: $direction")
            val result = node.performAction(action)
            node.recycle()
            nodes.forEach { it.recycle() }
            return result
        }
        Log.w(TAG, "未找到可滚动的节点: $text")
        nodes?.forEach { it.recycle() }
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