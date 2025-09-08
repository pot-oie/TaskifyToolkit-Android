package com.example.taskifyapp.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

object LayoutXmlParser {
    private const val TAG = "LayoutXmlParser"

    /**
     * [功能] 获取当前活动窗口的 UI 布局，并将其序列化为 XML 字符串。
     * @param service AccessibilityService 的实例，用于获取窗口信息。
     * @return 返回包含 UI 布局的 XML 字符串，如果失败则返回 null。
     */
    fun getLayoutXml(service: AccessibilityService): String? {
        val windows: List<AccessibilityWindowInfo> = service.windows
        if (windows.isEmpty()) {
            Log.e(TAG, "getLayoutXml: FAILED because getWindows() returned an empty list.")
            return null
        }

        // 寻找我们最关心的那个窗口：通常是类型为“应用”且处于“活动”状态的窗口
        val targetWindow = windows.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            ?: windows.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } // 第一个应用窗口
            ?: windows.last() // 最后一个窗口作为备用

        val rootNode = targetWindow.root
        if (rootNode == null) {
            Log.e(TAG, "getLayoutXml: FAILED because the root node of the target window is null.")
            return null
        }
        try {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()
            val doc = docBuilder.newDocument()

            val rootElement = doc.createElement("hierarchy")
            doc.appendChild(rootElement)

            // 递归构建 XML 树
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
        }
    }

    /**
     * 判断节点是否“重要”
     */
    private fun isImportantNode(node: AccessibilityNodeInfo): Boolean {
        // 如果节点可见且满足以下任一条件，则为重要节点：
        // 1. 可交互 (clickable, longClickable, editable, scrollable)
        // 2. 有文本标签 (text or contentDescription)
        // 3. 有资源ID (resource-id)
        return node.isVisibleToUser &&
                (node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable ||
                        !node.text.isNullOrEmpty() ||
                        !node.contentDescription.isNullOrEmpty() ||
                        !node.viewIdResourceName.isNullOrEmpty())
    }

    /**
     * 递归辅助函数，将 AccessibilityNodeInfo 转换为 XML 元素
     * 跳过不重要的纯布局节点，只保留“重要”节点
     */
    private fun dumpNodeToXml(node: AccessibilityNodeInfo?, doc: Document, parentElement: Element) {
        if (node == null) return
        // 只添加“重要”节点到XML树中
        if (isImportantNode(node)) {
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
            // 继续递归处理它的所有子节点，并将新创建的 element作为这些子节点的父级
            for (i in 0 until node.childCount) {
                dumpNodeToXml(node.getChild(i), doc, element)
            }
        } else {
            // 如果当前节点是“不重要的”（纯布局容器），则跳过它
            // 直接递归处理它的所有子节点，并将这些子节点挂在原来的父节点上
            for (i in 0 until node.childCount) {
                dumpNodeToXml(node.getChild(i), doc, parentElement)
            }
        }
    }
}