# TaskifyToolkit (安卓智能代理)

**TaskifyToolkit** 是一个功能完备的安卓设备端智能代理，它利用安卓系统的 **无障碍服务 (Accessibility Service)** 和 **MediaProjection API**，实现对手机UI界面的深度感知与精准的远程自动化操作。

<img src="./images/log_center.png" width="32%"><img src="./images/settings_page.png" width="32%"><img src="./images/info_page.png" width="32%">



## 项目简介

作为大型自动化项目 **Taskify** 的核心执行端，**TaskifyToolkit** 旨在将一台安卓设备转变为一个能够被远程“大脑”（如云端大语言模型）驱动的智能机器人。它负责执行“大脑”下发的指令，并将设备屏幕上的视觉信息回传，形成一个完整的“感知-决策-执行”自动化闭环。



## 项目架构

- **TaskifyToolkit (本项目)**: 作为运行在手机上的“眼睛”和“手”。
  - **眼睛 (感知)**: 使用 `MediaProjection` API 实时捕捉屏幕画面，并结合无障碍服务深度解析UI布局的XML结构。
  - **手 (行动)**: 使用 `TaskifyAccessibilityService` 精准模拟用户的点击、长按、滑动、输入文本、滚动等各类操作。
- **Taskify (后端项目)**: 作为“大脑”，运行在云端。负责接收客户端发来的屏幕信息，通过AI模型进行分析和决策，并向客户端下发原子或组合操作指令。



## 核心功能

当前版本已实现以下核心功能：

- **多样的UI自动化**: 封装了对UI元素的 **点击、长按、输入文本、滑动、滚动** 等多种原子操作。
- **屏幕与布局捕获**: 能够按指令捕获当前屏幕截图及UI布局XML，并上报至后端。
- **实时双向通信**: 基于 WebSocket 实现与后端的稳定长连接，支持指令的实时下发和结果上报。
- **动态配置与管理**:
  - 支持在App内通过UI动态修改后端服务器地址。
  - 支持通过开关启用/禁用连接断开后的自动重连功能。
  - 提供优雅的开启/关闭服务流程，方便用户完全控制代理的生命周期。
- **完善的状态监控与调试**:
  - 主界面通过图标和颜色实时显示 **无障碍服务** 和 **后台连接** 的状态。
  - 内置 **实时日志中心** 标签页，在手机端即可追踪所有接收到的指令和关键执行日志，极大方便了移动调试。
  - 内置 **设备信息** 标签页，快速查看本机IP地址、设备型号、安卓版本等关键信息。
- **服务持久化**: 采用前台服务 (`ForegroundService`) 保证代理在后台稳定运行，并通过常驻通知栏清晰展示服务状态。



## 技术栈

- **语言**: Kotlin
- **核心 API**:
  - `AccessibilityService`: 用于UI自动化操作。
  - `MediaProjection` API: 用于屏幕内容获取。
  - `ForegroundService`: 保证服务在后台稳定运行。
- **网络通信**: OkHttp (WebSocket)
- **数据持久化**: SharedPreferences
- **UI**: Material Design 3, ViewPager2, TabLayout, Vector Drawable
- **应用内通信**: LocalBroadcastManager
- **构建系统**: Gradle



## 快速开始

### 1. 环境要求

- **Android Studio 版本：** Hedgehog | 2023.1.1 或更高版本
- **最低 SDK (`minSdk`)：** 24 (Android 7.0)
- **目标 SDK (`targetSdk`)：** 34 (Android 14)
- **网络环境**: 您的电脑（运行后端服务）和安卓测试设备必须连接到 **同一个局域网 (Wi-Fi)** 下。



### 2. 关键配置

推荐使用App内置的UI进行配置，这比在代码中硬编码更灵活。

1. 启动App，切换到 **【高级设置】** 标签页。
2. 在“服务器地址”输入框中，填入您后端服务的WebSocket地址 (例如: `ws://<你的电脑IP地址>:8080/agent-ws`)。
3. 点击 **【保存】**。

**如何查找电脑IP地址？**

* **Windows**: 打开CMD（命令提示符），输入 `ipconfig`，查找“无线局域网适配器”下的“IPv4 地址”。
* **macOS**: 打开“系统设置” -> “网络” -> “Wi-Fi”，可以看到IP地址；或在终端输入 `ifconfig | grep "inet "`。



### 3. 构建与运行

1. 使用 Android Studio 打开本安卓项目。
2. 等待 Gradle 完成项目同步和构建。
3. 连接您的安卓设备或启动安卓模拟器。
4. 点击菜单栏的 **"Run" -> "Run 'app'"** (或使用快捷键 `Shift+F10`) 来安装并运行App。



### 4. 使用与测试流程

为了让安卓端进入“待命”状态，请在App内完成以下初始化步骤：

1. **开启无障碍服务**: 点击 **【1. 开启无障碍服务】** 按钮，系统会跳转到设置页面。在列表中找到“Taskify 自动化服务”并开启它。返回App后，您会看到该项状态变为绿色的“已开启”。
2. **启动后台服务并授权**: 点击 **【2. 启动后台服务】** 按钮。系统会弹出“屏幕录制”的授权请求，请点击“立即开始”或“同意”。
3. **验证服务状态**: 授权成功后，App会自动连接您配置的后端地址。主界面的 **“后台连接服务”** 状态应变为绿色的“已连接”。这表示安卓端已准备就绪。
4. **配合后端测试**: 此时，您可以开始通过后端下发指令。所有接收到的指令和执行结果，都会实时显示在App的 **【实时日志】** 标签页中，方便您进行调试。
5. **关闭服务**: 当服务运行时，主界面的按钮会自动变为“关闭”功能。您可以随时点击 **【关闭后台服务】** 或 **【关闭无障碍服务】** 来停止代理。



## 指令集示例 (JSON)

所有支持的指令都以 `actionType` 字段来区分，以下的推荐格式都存放在 `\app\src\main\assets` 。

点击 (CLICK)

```json
{
  "actionType": "CLICK",
  "targetText": "登录"
}
```

输入文本 (INPUT_TEXT)

```json
{
  "actionType": "INPUT_TEXT",
  "targetText": "请输入用户名",
  "textToInput": "my_username"
}
```

长按 (LONG_CLICK)

```json
{
  "actionType": "LONG_CLICK",
  "targetText": "复制"
}
```

滑动 (SWIPE)

```json
{
  "actionType": "SWIPE",
  "startX": 540,
  "startY": 1800,
  "endX": 540,
  "endY": 600,
  "duration": 400
}
```

滚动 (SCROLL)

```json
{
  "actionType": "SCROLL",
  "targetText": "消息列表",
  "direction": "FORWARD"
}
```

捕获并上报 (CAPTURE_AND_REPORT)

```json
{
  "actionType": "CAPTURE_AND_REPORT"
}
```



## 未来工作

- [ ] 完善指令执行后的结果反馈机制，将成功或失败的状态回传给后端。

- [ ] 开发更丰富的UI元素定位方式（如通过resource-id, XPath）。

- [ ] 优化服务的稳定性和内存占用。

- [ ] 将测试范围扩大到更多不同品牌和安卓版本的物理设备。