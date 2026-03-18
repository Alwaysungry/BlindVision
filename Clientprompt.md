**客户端职责**

实时采集视频流

低帧率抽样并压缩

采集语音并识别意图

将帧 + 意图 + 状态发送 API

接收结构化决策结果

通过语音播放结果

根据风险级别动态调整帧率

支持语音播报打断机制

加入状态机同步
动态帧率
打断机制
网络容错
风险优先播报

移动速度估计
利用陀螺仪

利用加速度计

判断是否在移动

如果检测到移动 + 高风险：

优先播报“请暂停移动”

手机姿态检测：API中已经返回信息，提示调整角度，

语音简洁度控制

必须限制：

单条播报 < 5 秒

高风险优先

低风险压缩

连续播报抑制机制

假设前方一直有围挡。

你不能每秒说：

前方有围挡

必须有：

相同风险 3 秒内不重复播报

这叫：

Anti-spam 机制

本地紧急机制

如果：

网络断开

API 超时

多帧未返回

客户端必须进入：

安全模式：
建议暂停移动

不能沉默。

沉默 = 危险。


电量控制

实时摄像头 + 网络 + TTS

极耗电。

必须：

自动降低分辨率

自动降低帧率

后台暂停

否则续航 < 1 小时。

连续 5fps + AI 上传

手机会发热降频。

要有：

长时间高风险时降低帧率

自动节流

连续 5fps + AI 上传

手机会发热降频。

要有：

长时间高风险时降低帧率


自动节流

声音环境干扰

你可能需要：

本地 VAD（语音活动检测）

噪声过滤

否则意图识别会乱

所以必须有：

心跳机制（每 5 秒一个轻提示音）

或持续环境更新

沉默会让用户失去信任。


你是一名资深 HarmonyOS 4.2.0 (HarmonyOS NEXT) 开发工程师。
使用 ArkTS 进行开发。

目标：
开发一个运行在鸿蒙 4.2.0 上的盲人视觉辅助客户端应用。
该客户端对接已完成的 AI 决策 API。

===========================
一、应用核心目标
===========================

本应用服务对象是盲人。
用户完全无法看到屏幕。

应用必须：

1. 实时采集摄像头视频流
2. 低帧率抽样（动态帧率）
3. 压缩图像
4. 采集语音
5. 识别意图
6. 将帧 + 意图 + 状态发送给后端 API
7. 接收结构化决策结果
8. 语音播报
9. 支持打断机制
10. 网络异常时进入安全模式

应用必须优先保证安全。

===========================
二、API返回结构
===========================

后端 API 返回 JSON：

{
  session_id: string,
  round: number,
  max_rounds: number,
  state: string,
  frame_ids?: string[],

  action: string,
  direction: string,
  environment_summary: string,
  risk_detected: boolean,
  assistant_message: string,
  confidence: number,
  risk_level: "low" | "medium" | "high",
  distance_estimate: "near" | "mid" | "far" | "none",
  affects_path: boolean,

  query?: string,
  intent?: string
}

===========================
三、客户端模块架构
===========================

请实现以下模块：

1. CameraController
   - 使用鸿蒙相机能力
   - 实时预览
   - 默认 1fps 抽帧
   - 支持动态帧率调整：
        low -> 1fps
        medium -> 2fps
        high -> 3fps
   - 压缩为 640x480 JPEG

2. MotionMonitor
   - 使用陀螺仪
   - 使用加速度计
   - 判断是否在移动
   - 如果 moving == true 且 risk_level == high
     -> 立即打断播报
     -> 播报："检测到风险，请暂停移动"

3. VoiceController
   - 支持语音识别
   - 支持 VAD
   - 将识别文本转为 intent
   - 支持主动提问模式

4. APIClient
   - 使用 WebSocket 优先
   - 备用 HTTP
   - 发送：
       frame
       session_id
       state
       intent
       frame_id
       timestamp
   - 处理超时

5. DecisionScheduler（核心调度器）
   - 丢弃过期 frame 响应
   - 高风险优先播报
   - 支持打断
   - Anti-spam：
       相同风险 3 秒内不重复播报
   - 控制动态帧率
   - 管理 session 状态机同步

6. TTSManager
   - 使用系统 TTS
   - 支持 interrupt
   - 单条播报不得超过 5 秒
   - 高风险立即打断

7. SafetyManager
   - 如果：
       WebSocket 断开
       3秒无响应
       API 超时
     -> 进入安全模式
     -> 播报："网络异常，请暂停移动"

8. HeartbeatManager
   - 若 5 秒内无播报
     -> 轻提示音或播报："环境正常"

9. PowerManager
   - 电量低于 20%
     -> 降低分辨率
     -> 降低帧率
   - 长时间 high risk
     -> 自动节流

===========================
四、状态同步机制
===========================

客户端必须维护：

current_session_id
current_state
last_frame_id
last_risk_level
last_spoken_message
last_spoken_time

API 返回 frame_ids 时：
只处理最新 frame_id
丢弃旧响应

===========================
五、异常处理
===========================

1. 摄像头异常
   -> 播报错误
2. 权限未授权
   -> 强制请求权限
3. 网络断开
   -> 安全模式
4. TTS 出错
   -> 重试一次

===========================
六、UI要求（极简）
===========================

因为服务盲人：

UI 极简：

- 大按钮：开始 / 停止
- 当前 risk_level 显示（仅调试）
- 当前 session 状态（调试）

支持全程语音操作。

===========================
七、安全优先原则
===========================

风险等级处理规则：

high:
    立即打断播报
    提升帧率
    若移动 -> 提示暂停

medium:
    正常播报
    2fps

low:
    合并播报
    1fps

===========================
八、性能要求
===========================

目标端到端延迟 < 1.5 秒。

===========================
九、代码要求
===========================

- 使用 ArkTS
- 模块化结构
- 清晰注释
- 可扩展
- 使用异步处理
- 避免阻塞 UI 线程

请输出：

1. 项目目录结构
2. 核心模块代码框架
3. CameraController 示例
4. WebSocket 示例
5. TTS 打断实现示例
6. DecisionScheduler 核心逻辑示例
7. MotionMonitor 示例


🎥 1️⃣ 摄像头

使用：

👉 CameraX

不要直接用 Camera2。

CameraX 优点：

生命周期自动管理

支持实时帧分析

可设置低帧率

易于压缩输出

你需要：

ImageAnalysis + STRATEGY_KEEP_ONLY_LATEST
🌐 2️⃣ 网络通信

如果你 API 是 HTTP：

使用：

👉 Retrofit

如果是实时流式（推荐）：

使用：

👉 OkHttp WebSocket

因为你有：

多轮视觉

实时决策

状态同步

WebSocket 比 HTTP 轮询更合适。

🧠 3️⃣ 状态机实现

用：

sealed class + StateFlow

不要搞复杂框架。

例如：

sealed class VisionState {
    object Exploring
    object Crossing
    object ObstacleCheck
    object HighRisk
}

然后：

val stateFlow = MutableStateFlow<VisionState>()
🎙 4️⃣ 语音播报

使用：

Android 原生：

TextToSpeech

这是最稳定的。

支持：

打断

队列控制

优先级

语言切换

🎤 5️⃣ 语音识别

简单版：

Android 原生：

SpeechRecognizer

进阶版（更稳定）：

用科大讯飞 SDK 或离线识别引擎。

📡 6️⃣ 传感器

使用：

SensorManager

监听：

TYPE_ACCELEROMETER

TYPE_GYROSCOPE

TYPE_ROTATION_VECTOR

你可以判断：

是否移动

手机是否抬起

姿态是否合适

🔥 7️⃣ VAD（语音活动检测）

可以：

简单 RMS 能量判断

或使用 WebRTC VAD 库

否则环境噪声会干扰。

🔋 8️⃣ 性能 & 电量监控

Android Studio 自带：

Profiler 工具

可以监测：

CPU

网络

电量

温度

你这个项目一定会发热。