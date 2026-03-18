# BlindVision - 盲人出行视觉辅助系统

> **本项目所有代码均为 AI 生成，仅供学习与研究使用。**

---

## ⚠️ 重要声明

**本项目仅作为技术学习与研究用途，禁止用于任何实际场景。**

### 安全性声明

1. **非生产级系统**：本项目未经过任何安全审计、可靠性测试或临床验证，不具备作为辅助视力设备的技术资质。
2. **无医疗/安全认证**：本系统不满足任何国家或地区的医疗器械、安全辅助设备法规要求，不得用于替代导盲犬、导盲杖或专业视障辅助设备。
3. **使用风险**：AI 视觉分析存在误判可能性，包括但不限于：障碍物漏检、距离估算误差、场景理解偏差等，可能导致严重人身伤害。
4. **责任豁免**：本项目作者及贡献者不对任何因使用本项目而导致的任何形式的损失或伤害承担任何责任。

### 法律合规

本项目仅作为**教育示范**目的开发。任何人不得：
- 将本系统用于商业产品
- 声称本系统可用于实际视障辅助
- 在任何产品或服务中将本系统作为安全关键组件

---

## 项目概述

BlindVision 是一款面向盲人的视觉辅助系统，通过智能手机摄像头采集环境画面，结合 AI 分析技术为用户提供语音导航和风险提示，帮助盲人用户安全出行。

### 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        BlindVision                              │
├──────────────────────────┬──────────────────────────────────────┤
│   客户端 (Android)       │        服务端 (FastAPI)              │
│   blind-vision-android   │        blind-vision-core            │
│                          │                                      │
│  - 摄像头画面采集        │  - 多轮会话管理                      │
│  - 图像压缩传输         │  - 意图解析                          │
│  - 语音输入识别         │  - 视觉分析 (GLM-4V)                 │
│  - 语音播报输出         │  - 状态机决策                        │
│  - 运动状态监测         │  - 环境评估                          │
└──────────────────────────┴──────────────────────────────────────┘
```

## 组件说明

### 1. 客户端 (blind-vision-android)

运行于鸿蒙 4.2.0 的 Android 应用，主要功能：

- 实时摄像头采集，动态帧率（1-3 fps）
- 语音输入与意图识别
- 语音播报，支持打断功能
- 加速度计和陀螺仪运动监测
- 基于风险等级的自适应行为
- 网络异常时自动进入安全模式

详见 [blind-vision-android/README.md](blind-vision-android/README.md)

### 2. 服务端 (blind-vision-core)

FastAPI 后端服务，提供：

- 多轮探索会话
- 状态机决策流程 (INIT → OBSERVE → EXPLORE → DECIDE → FINAL_ANSWER)
- 视觉分析（调用智谱 AI GLM-4V API）
- 手机角度调整引导
- 环境探索指令
- 结构化 JSON 响应，含置信度评分

详见 [blind-vision-core/README.md](blind-vision-core/README.md)

## 快速开始

### 前置要求

- Python 3.10+
- Android Studio
- 智谱 AI API Key

### 服务端启动

```bash
cd blind-vision-core
cp .env.example .env  # 添加 ZHIPU_API_KEY
pip install -r requirements.txt
uvicorn main:app --reload
```

### 客户端构建

在 Android Studio 中打开 `blind-vision-android` 目录并构建。

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/analyze` | POST | 单次分析（兼容旧版） |
| `/session/start` | POST | 创建探索会话 |
| `/session/{id}/status` | GET | 获取会话状态 |
| `/session/{id}/frame` | POST | 提交图像帧 |
| `/session/{id}/query` | POST | 提交图像帧和问题 |

## 许可证

MIT License
