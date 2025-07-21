# TaskifyApp (安卓客户端)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Taskify 项目的安卓客户端。本项目利用安卓系统的无障碍服务 (Accessibility Service) 和 MediaProjection API，作为一个运行在设备端的智能代理，实现对手机UI界面的感知与自动化操作。

## 项目架构

本客户端是 **Taskify** 智能代理系统的一部分，遵循客户端/服务器 (C/S) 架构：

* **TaskifyApp (本项目)**: 作为运行在手机上的“眼睛”和“手”。
    * **眼睛 (感知)**: 使用 `ScreenshotService` 配合 `MediaProjection` API 捕捉屏幕画面。
    * **手 (行动)**: 使用 `TaskifyAccessibilityService` 模拟用户的点击、滑动、输入等操作。
* **Taskify (后端项目)**: 作为“大脑”，运行在云端。负责接收客户端发来的屏幕信息，通过大语言模型进行分析和决策，并向客户端下发操作指令。

## 核心功能

目前测试版本已实现以下核心功能：

* **无障碍服务**: 能够稳定运行，并提供了 `clickByText` 等基础API用于模拟用户点击。
* **屏幕捕捉服务**: 采用独立的前台服务 (`ScreenshotService`) 实现，能够在用户授权后，启动一个持久化的录屏会话，并按需（通过 `Intent` 命令）捕获当前屏幕的截图。
* **权限管理**: 包含清晰的用户引导流程，用于请求和开启无障碍服务及屏幕捕捉权限。
* **测试面板**: 主界面 (`MainActivity`) 目前作为一个功能完备的测试面板，可以独立验证各项服务的开启和功能调用。

## 技术栈

* **语言**: Kotlin
* **核心 API**:
    * `AccessibilityService`: 用于UI自动化操作。
    * `MediaProjection` API: 用于屏幕内容获取。
    * `ForegroundService`: 保证截图服务在后台稳定运行。
* **构建系统**: Gradle

## 如何开始

#### **环境要求**

* Android Studio Iguana | 2023.2.1 或更高版本。
* 安卓虚拟机 (Emulator) 或物理设备，系统版本建议为 Android 11 (API 30) 或更高。

#### **运行步骤**

1.  克隆本仓库到本地：
    ```bash
    git clone [你的仓库URL]
    ```
2.  使用 Android Studio 打开项目。
3.  等待 Gradle 完成项目同步和依赖下载。
4.  点击工具栏的 **'Run app' (▶️)** 按钮，将应用安装到你的虚拟机或物理设备上。

## 如何测试

应用启动后，你将看到一个测试面板，请按照以下顺序进行测试：

1.  点击 **“1. 开启无障碍服务”** 按钮，在系统设置页面中找到 `TaskifyApp` 并开启权限。
2.  返回 App，点击 **“2. 启动截图服务并授权”** 按钮，在弹出的系统对话框中完成授权。成功后，手机顶部状态栏会出现一个常驻通知。
3.  点击 **“3. 执行单次截图”** 按钮，App 会进行一次截图并保存。请通过 Logcat 或手机文件管理器检查截图是否成功。
4.  在输入框中确认文本后，点击 **“4. 测试点击功能”** 按钮，观察对应的目标按钮是否被成功点击。

## 贡献代码

欢迎为本项目贡献代码！请遵循以下工作流程：

1.  禁止直接向 `main` 分支提交代码。
2.  从 `main` 分支创建你自己的特性分支 (`feature/your-feature-name`)。
3.  完成开发后，向上游仓库的 `main` 分支提交 **Pull Request (PR)**。
4.  PR 经过审查 (Review) 后，将以 **Squash and merge** 的方式合并。

## 未来工作

* [ ] 实现与 `Taskify` 后端项目的网络通信（使用 Retrofit）。
* [ ] 将测试按钮的触发逻辑，替换为接收和解析来自后端的指令。
* [ ] 开发更丰富的UI元素定位和操作序列功能。
* [ ] 完善服务的稳定性和错误处理机制。