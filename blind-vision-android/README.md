# BlindVision Android 客户端

> **本项目所有代码均为 AI 生成。**

## 概述

BlindVision Android 客户端是一款面向盲人用户的 Kotlin 移动应用。应用实时采集摄像头画面，发送到后端 API 进行分析，并通过语音合成提供导航辅助。

## 技术栈

- **语言**: Kotlin
- **目标平台**: 鸿蒙 4.2.0 (HarmonyOS NEXT)
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34

## 项目结构

```
blind-vision-android/
├── app/
│   └── src/main/
│       └── java/com/blindvision/client/
│           ├── BlindVisionApp.kt          # Application 类
│           ├── camera/
│           │   └── CameraController.kt     # 摄像头采集与帧提取
│           ├── data/
│           │   ├── model/
│           │   │   └── Models.kt           # 数据模型
│           │   └── remote/
│           │       ├── ApiClient.kt        # HTTP/WebSocket 客户端
│           │       └── VisionApi.kt        # API 接口定义
│           ├── domain/
│           │   └── DecisionScheduler.kt   # 核心决策调度
│           ├── safety/
│           │   └── SafetyManager.kt        # 网络异常安全模式
│           ├── sensor/
│           │   └── MotionMonitor.kt       # 加速度计与陀螺仪
│           ├── ui/
│           │   └── MainActivity.kt        # 主界面
│           └── voice/
│               ├── IntentParser.kt        # 语音意图识别
│               ├── TTSManager.kt           # 语音合成
│               └── VoiceController.kt      # 语音输入处理
├── build.gradle.kts                        # Gradle 构建配置
├── settings.gradle.kts                     # 项目设置
└── gradle/                                 # Gradle 包装器
```

## 核心功能

### 1. 摄像头采集
- 实时摄像头预览
- 动态帧率（根据风险等级 1-3 fps）
- JPEG 压缩（640x480）
- 使用 CameraX 进行生命周期管理

### 2. 运动检测
- 加速度计检测移动状态
- 陀螺仪检测方向
- 高风险 + 检测到移动时自动暂停播报

### 3. 语音输入
- 语音识别
- 用户查询意图解析
- VAD（语音活动检测）支持

### 4. 语音输出（TTS）
- 系统 TextToSpeech 集成
- 支持打断
- 每条消息最长 5 秒
- 高风险消息优先队列

### 5. 决策调度器
- 帧响应去重
- 高风险消息优先
- 防重复：3 秒内不重复播报
- 动态帧率管理
- 会话状态同步

### 6. 安全管理器
- 网络异常检测
- 3 秒超时处理
- 自动进入安全模式
- 语音提示："网络异常，请暂停移动"

### 7. 电源管理
- 低电量（<20%）自动降低配置
- 分辨率缩放
- 高风险时帧率节流

## 风险等级处理

| 等级 | 帧率 | 行为 |
|------|------|------|
| high | 3 fps | 立即打断，最高优先级 |
| medium | 2 fps | 正常语音播报 |
| low | 1 fps | 合并消息，最小化播报 |

## API 通信

### 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/session/start` | POST | 创建新会话 |
| `/session/{id}/frame` | POST | 提交图像帧 |
| `/session/{id}/query` | POST | 提交帧和问题 |
| `/session/{id}/status` | GET | 获取会话状态 |

### 请求格式

```json
{
  "frames": ["base64编码的图像"],
  "frame_ids": ["可选的帧ID"],
  "session_id": "当前会话ID",
  "timestamp": 1234567890
}
```

### 响应格式

```json
{
  "session_id": "uuid",
  "round": 1,
  "max_rounds": 5,
  "state": "EXPLORE",
  "action": "adjust_camera | explore_direction | final_answer",
  "direction": "left | right | up | down | forward | backward | none",
  "environment_summary": "前方是路口",
  "risk_detected": true,
  "assistant_message": "请抬高手机一点。",
  "confidence": 0.85,
  "risk_level": "low | medium | high",
  "distance_estimate": "near | mid | far | none",
  "affects_path": true
}
```

## 构建说明

### 前置要求
- Android Studio Arctic Fox 或更高版本
- JDK 11+
- Gradle 8.0+

### 构建命令

```bash
# 构建调试 APK
./gradlew assembleDebug

# 构建发布 APK
./gradlew assembleRelease

# 运行测试
./gradlew test
```

### APK 位置
- 调试版：`app/build/outputs/apk/debug/app-debug.apk`
- 发布版：`app/build/outputs/apk/release/app-release.apk`

## 权限配置

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## 配置

编辑 `local.properties` 设置 API 端点：

```properties
API_BASE_URL=http://你的服务器IP:8000
```

## 关键实现细节

### 帧率控制

```kotlin
enum class FrameRate(val fps: Int) {
    LOW(1),    // 低风险
    MEDIUM(2), // 中风险
    HIGH(3)    // 高风险
}
```

### 防重复机制

客户端维护：
- `lastSpokenMessage`：上一条消息内容
- `lastSpokenTime`：上一次播报时间戳
- 3 秒内相同风险等级不重复播报

### 安全模式

触发时：
1. 停止当前 TTS 播报
2. 播报："网络异常，请暂停移动"
3. 切换到最低 1 fps 模式
4. 持续尝试重连

## 测试

应用包含：
- 摄像头权限流程
- 网络异常模拟
- 风险等级模拟
- TTS 打断测试

## 开发说明

- 使用 Kotlin 协程处理异步操作
- 使用 StateFlow 进行响应式状态管理
- 使用 CameraX 抽象摄像头操作
- 使用 OkHttp 处理网络请求
- 所有语音提示均为中文
- 为盲人设计（极简 UI，语音优先）
