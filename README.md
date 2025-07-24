# TaskifyToolkit (安卓工具箱)

Taskify 项目的Toolkit，利用安卓系统的无障碍服务 (Accessibility Service) 和 MediaProjection API，作为一个运行在设备端的智能代理，实现对手机UI界面的感知与自动化操作。

## 项目架构

**TaskifyToolkit** 是一个安卓端的内部工具，旨在实现远程的 UI 控制和屏幕捕获功能。

* **TaskifyToolkit (本项目)**: 作为运行在手机上的“眼睛”和“手”。
    * **眼睛 (感知)**: 使用 `ScreenshotService` 配合 `MediaProjection` API 捕捉屏幕画面。
    * **手 (行动)**: 使用 `TaskifyAccessibilityService` 模拟用户的点击、滑动、输入等操作。
* **Taskify (后端项目)**: 作为“大脑”，运行在云端。负责接收客户端发来的屏幕信息，通过大语言模型进行分析和决策，并向客户端下发操作指令。

## 核心功能

目前版本已实现以下核心功能：

* **无障碍服务**: 能够稳定运行，并提供了 `clickByText` 等基础API用于模拟用户点击。
* **屏幕捕捉服务**: 采用独立的前台服务 (`ScreenshotService`) 实现，能够在用户授权后，启动一个持久化的录屏会话，并按需（通过 `Intent` 命令）捕获当前屏幕的截图。
* **权限管理**: 包含清晰的用户引导流程，用于请求和开启无障碍服务及屏幕捕捉权限。
* **Websocket**: 完整实现与后端的通信。

## 技术栈

* **语言**: Kotlin
* **核心 API**:
    * `AccessibilityService`: 用于UI自动化操作。
    * `MediaProjection` API: 用于屏幕内容获取。
    * `ForegroundService`: 保证截图服务在后台稳定运行。
* **构建系统**: Gradle

## 环境要求

* **Android Studio 版本：** 2025.1.1（本人使用）
* **最低 SDK (`minSdk`)：** 24 (Android 7.0)
* **目标 SDK (`targetSdk`)：** 36
* **主要测试环境：** 安卓模拟器 - Pixel 7 (API 36)

## 测试步骤

### 1. 环境准备

- **网络环境**: 您的电脑（运行后端服务）和安卓测试设备必须连接到 **同一个局域网 (Wi-Fi)** 下。

### 2. 关键配置

在运行App前，**必须**配置正确的后端WebSocket地址，否则App将无法连接到后端服务。

1.  在 Android Studio 中，找到并打开以下文件：
    `app/src/main/java/com/example/taskifyapp/WebSocketService.kt`

2.  修改文件顶部的 `WEBSOCKET_URL` 常量，将其中的IP地址替换为您**正在运行后端服务的电脑的局域网IP地址**。

    ```kotlin
    // ...
    class WebSocketService : Service() {
    
        private val TAG = "WebSocketService"
        // !!!重要!!! 请将这里替换为您的后端服务器地址
        private val WEBSOCKET_URL = "ws://<你的电脑IP地址>:8080/agent-ws"
    
        // ...
    }
    ```

    * **如何查找电脑IP地址？**
        * **Windows**: 打开CMD（命令提示符），输入 `ipconfig`，查找“无线局域网适配器”下的“IPv4 地址”。
        * **macOS**: 打开“系统设置” -> “网络” -> “Wi-Fi”，可以看到IP地址；或在终端输入 `ifconfig | grep "inet "`。

### 3. 构建与运行

1.  使用 Android Studio 打开本安卓项目。
2.  等待 Gradle 完成项目同步和构建（通常会自动进行）。
3.  连接您的安卓设备或启动安卓模拟器。
4.  点击菜单栏的 **"Run" -> "Run 'app'"** (或使用快捷键 `Shift+F10`) 来安装并运行App。

### 4. 使用与测试流程

为了让安卓端进入“待命”状态以配合后端测试，请在App内完成以下初始化步骤：

1.  **开启无障碍服务**:
    App启动后会显示主界面。请首先点击 **【1. 开启无障碍服务】** 按钮，系统会跳转到设置页面。在列表中找到“Taskify 自动化服务”并开启它。

2.  **启动后台服务并授权**:
    返回App后，点击 **【2. 启动后台服务并授权】** 按钮。系统会弹出“屏幕录制”的授权请求，请点击“立即开始”或“同意”。

3.  **验证服务状态**:
    授权成功后，App会自动在后台以“前台服务”的形式运行。您可以在手机顶部的通知栏看到一个“TaskifyToolkit 后台服务”的常驻通知，这表示安卓端已准备就绪，正在等待后端的指令。

4.  **配合后端测试**:
    此时，安卓端已进入待命状态。后端开发同学可以通过调用 `POST /api/v1/agent/start` 接口来触发任务。您可以在 Android Studio 的 **Logcat** 窗口中，筛选 `WebSocketService` 来观察安卓端接收和执行指令的日志。

## 未来工作

* [ ] 与后端同学配合，接收和解析来自后端的指令。
* [ ] 开发更丰富的UI元素定位和操作序列功能。
* [ ] 完善服务的稳定性和错误处理机制。
* [ ] 将测试范围扩大到物理安卓设备。