# TaskifyToolkit (安卓工具箱)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Taskify 项目的Toolkit，利用安卓系统的无障碍服务 (Accessibility Service) 和 MediaProjection API，作为一个运行在设备端的智能代理，实现对手机UI界面的感知与自动化操作。

## 项目架构

**TaskifyToolkit** 是一个安卓端的内部工具，旨在实现远程的 UI 控制和屏幕捕获功能。

**当前状态：** 开发版本。主要目的是在安卓模拟器上测试核心权限的设置流程，以及各项基础功能的手动操作。

* **TaskifyToolkit (本项目)**: 作为运行在手机上的“眼睛”和“手”。
    * **眼睛 (感知)**: 使用 `ScreenshotService` 配合 `MediaProjection` API 捕捉屏幕画面。
    * **手 (行动)**: 使用 `TaskifyAccessibilityService` 模拟用户的点击、滑动、输入等操作。
* **Taskify (后端项目)**: 作为“大脑”，运行在云端。负责接收客户端发来的屏幕信息，通过大语言模型进行分析和决策，并向客户端下发操作指令。

## 核心功能

目前测试版本已实现以下核心功能：

* **无障碍服务**: 能够稳定运行，并提供了 `clickByText` 等基础API用于模拟用户点击。
* **屏幕捕捉服务**: 采用独立的前台服务 (`ScreenshotService`) 实现，能够在用户授权后，启动一个持久化的录屏会话，并按需（通过 `Intent` 命令）捕获当前屏幕的截图。
* **权限管理**: 包含清晰的用户引导流程，用于请求和开启无障碍服务及屏幕捕捉权限。
* **测试面板**: 主界面 (`MainActivity`) 目前作为一个功能完备的测试面板，可以独立验证各项服务的开启和功能调用（http连接还未实现）。

## 技术栈

* **语言**: Kotlin
* **核心 API**:
    * `AccessibilityService`: 用于UI自动化操作。
    * `MediaProjection` API: 用于屏幕内容获取。
    * `ForegroundService`: 保证截图服务在后台稳定运行。
* **构建系统**: Gradle

## 如何开始

#### **环境要求**

* **Android Studio 版本：** 2025.1.1（本人使用）
* **最低 SDK (`minSdk`)：** 24 (Android 7.0)
* **目标 SDK (`targetSdk`)：** 36
* **主要测试环境：** 安卓模拟器 - Pixel 7 (API 36)

#### **运行步骤**

1.  克隆本仓库到本地：
    ```bash
    git clone https://github.com/pot-oie/Taskify-Android.git
    ```
2.  使用 Android Studio 打开项目。
3.  等待 Gradle 完成项目同步和依赖下载。
4.  点击工具栏的 **'Run app' (▶️)** 按钮，将应用安装到你的虚拟机或物理设备上。

## 如何测试

应用启动后，你将看到一个测试面板，请按照以下顺序进行测试：

1. 点击 **“1. 开启无障碍服务”** ，在系统设置页面中找到 `TaskifyToolkit` 并开启权限。

2. 返回 App，点击 **“2. 启动截图服务”** ，在弹出的系统对话框中完成授权（选择 `Share entire screen` ）。成功后，手机顶部状态栏会出现一个常驻通知。

3. 在输入框中确认文本后，点击 **“3. 测试点击功能”** ，观察对应的目标按钮是否被成功点击。

4. 点击 **“4. 执行单次截图”** ，App 会进行一次截图并保存。请通过 Logcat 或手机文件管理器检查截图是否成功。

   `/storage/emulated/0/Android/data/com.example.taskifyapp/files/Pictures/screenshot.png`

## 贡献代码

欢迎为本项目贡献代码！请遵循以下工作流程：

1.  禁止直接向 `main` 分支提交代码。
2.  从 `main` 分支创建你自己的特性分支 (`feature/your-feature-name`)。
3.  完成开发后，向上游仓库的 `main` 分支提交 **Pull Request (PR)**。
4.  PR 经过审查 (Review) 后，将以 **Squash and merge** 的方式合并。

## 未来工作

* [ ] 实现与 `Taskify` 后端项目的网络通信（使用 Retrofit or NanoHTTPD）。**Important**
* [ ] 将测试按钮的触发逻辑，替换为接收和解析来自后端的指令。
* [ ] 开发更丰富的UI元素定位和操作序列功能。
* [ ] 完善服务的稳定性和错误处理机制。
* [ ] 当 HTTP 服务器功能在模拟器上稳定后，将测试范围扩大到物理安卓设备。