package com.example.taskifyapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

/**
 * 封装了所有具体的无障碍操作。
 * 它持有 AccessibilityService 的实例来执行底层动作。
 */
class AccessibilityActionPerformer(private val service: TaskifyAccessibilityService) {

    private val TAG = "ActionPerformer"

    /**
     * 递归节点查找函数
     * 它会不区分大小写地、模糊匹配节点的 text, contentDescription, 和 hintText
     */
    private fun findNodeByTextUniversal(root: AccessibilityNodeInfo?, textToFind: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // 检查三个关键属性，使用 contains 进行模糊匹配
            if (node.text?.toString()?.contains(textToFind, ignoreCase = true) == true)
                return node
            if (node.contentDescription?.toString()?.contains(textToFind, ignoreCase = true) == true)
                return node
            // AccessibilityNodeInfo 没有直接的 hintText 属性，但 getHintText() 可以获取
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (node.hintText?.toString()?.contains(textToFind, ignoreCase = true) == true)
                    return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * [功能] 查找并点击包含特定文本的第一个可点击节点
     * 如果节点本身不可点击，则会向上遍历其父节点，直到找到一个可点击的父容器并点击它。
     * @param text 要查找的文本
     * @return 如果找到并成功点击则返回 true，否则返回 false
     */
    fun clickByText(text: String): Boolean {
        // 获取当前活动窗口的根节点
        val rootNode = service.rootInActiveWindow ?: return false
        // 根据文本查找所有匹配的节点
        val targetNode = findNodeByTextUniversal(rootNode, text)

        if (targetNode == null) {
            Log.w(TAG, "通过通用搜索 '$text' 未找到任何匹配节点")
            return false
        }

        var current: AccessibilityNodeInfo? = targetNode
        for (i in 0..4) { // 向上遍历5层寻找可点击的父节点
            if (current == null) break
            if (current.isClickable) {
                Log.d(TAG, "成功找到与 '$text' 关联的可点击节点并执行点击！")
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        Log.w(TAG, "通过文本 '$text' 找到的节点及其父节点中，均未发现可点击的。")
        return false
    }

    /**
     * 更强大的输入方法。它会优先尝试在当前聚焦的节点上输入，
     * 如果没有聚焦的节点，再尝试在整个屏幕上寻找第一个可编辑的节点。
     */
    fun inputTextToFocusedOrFirstEditable(text: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        // 优先查找当前已聚焦的输入框
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            Log.d(TAG, "找到已聚焦的可编辑节点，并输入内容")
            val result = inputText(focusedNode, text)
            if (result) {
                Log.d(TAG, "成功执行输入")
            }
            return result
        }
        // 如果没有已聚焦的，则退而求其次，寻找屏幕上第一个可编辑的节点
        val editableNode = findEditableChildIn(rootNode)
        if (editableNode != null) {
            Log.d(TAG, "未找到已聚焦节点，但在屏幕上找到第一个可编辑节点并输入内容")
            val result = inputText(editableNode, text)
            if (result) {
                Log.d(TAG, "成功执行输入")
            }
            return result
        }
        Log.w(TAG, "在当前屏幕未找到任何已聚焦或可编辑的节点")
        return false
    }

    fun inputTextByText(findText: String, contentToInput: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val targetNode = findNodeByTextUniversal(rootNode, findText)

        if(targetNode != null) {
            var searchContainer: AccessibilityNodeInfo? = targetNode
            for (i in 0..4) {
                if (searchContainer == null) break
                val editableNode = findEditableChildIn(searchContainer)
                if (editableNode != null) {
                    Log.d(TAG, "在'$findText'附近找到可编辑节点并输入内容")
                    return inputText(editableNode, contentToInput)
                }
                searchContainer = searchContainer.parent
            }
        }
        // 如果通过文本找不到输入框，则调用备用方案
        Log.w(TAG, "通过文本 '$findText' 未找到可编辑节点，将尝试通用输入方法...")
        return inputTextToFocusedOrFirstEditable(contentToInput)
    }

    /**
     * 根据文本找到节点并执行长按
     * 如果节点本身不可长按，则向上遍历查找可长按的父容器。
     */
    fun longClickByText(text: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val targetNode = findNodeByTextUniversal(rootNode, text)

        if (targetNode == null) {
            Log.w(TAG, "通过通用搜索 '$text' 未找到任何匹配节点")
            return false
        }

        var current: AccessibilityNodeInfo? = targetNode
        for (i in 0..4) {
            if (current == null) break
            if (current.isLongClickable) {
                Log.d(TAG, "成功找到与文本 '$text' 关联的可长按节点并执行长按！")
                return current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
            current = current.parent
        }
        Log.w(TAG, "通过文本 '$text' 找到的节点及其所有父节点中，均未发现可长按的。")
        return false
    }

    /**
     * 根据文本找到节点并执行滚动
     * 如果节点本身不可滚动，则向上遍历查找可滚动的父容器。
     * @param text 要查找的可滚动节点的文本
     * @param direction 滚动方向, 1 为向前(下/右), -1 为向后(上/左)
     */
    fun scrollByText(text: String, direction: Int): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val targetNode = findNodeByTextUniversal(rootNode, text)
        if (targetNode == null) {
            Log.w(TAG, "通过文本 '$text' 未找到任何节点")
            return false
        }
        val action = if (direction > 0)
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        var current: AccessibilityNodeInfo? = targetNode

        for (i in 0..4) {
            if (current == null) break
            if (current.isScrollable) {
                Log.d(TAG, "成功找到与文本 '$text' 关联的可滚动节点并执行滚动！")
                return current.performAction(action)
            }
            current = current.parent
        }
        Log.w(TAG, "通过文本 '$text' 找到的节点及其所有父节点中，均未发现可滚动的。")
        return false
    }

    /**
     * 在指定的输入框节点中输入文本
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
     * 模拟精确滑动操作
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "滑动操作成功")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "滑动操作被取消")
            }
        }, null)
    }

    /**
     * 执行全局返回操作
     * 相当于用户按下了系统导航栏的“返回”按钮
     * @return 如果操作成功派发则返回 true
     */
    fun performGlobalBack(): Boolean {
        Log.d(TAG, "执行全局返回操作")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * 广度优先搜索，在指定根节点下查找第一个可编辑的子节点
     */
    private fun findEditableChildIn(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(rootNode)
        while (queue.isNotEmpty()) {
            val node = queue.poll()
            if (node != null) {
                if (node.isEditable) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }
        return null
    }
}