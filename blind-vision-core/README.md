# BlindVision 服务端

> **本项目所有代码均为 AI 生成，仅供学习与研究使用。**

---

## ⚠️ 重要声明

**本项目仅作为技术学习与研究用途，禁止用于任何实际场景。**

本服务端是 BlindVision 项目的后端组件，提供视觉分析 API。请务必阅读主项目 [README.md](../README.md) 中的完整免责声明。

### 安全特性说明

当前实现的安全措施（**不足以满足实际使用要求**）：

- 多轮会话状态机
- 风险等级评估（low/medium/high）
- 置信度评分
- 环境摘要生成
- 模拟模式（MOCK_MODE）

### 技术限制

- 依赖第三方 AI API（智谱 GLM-4V），存在响应延迟
- 无端到端安全保障
- 无冗余容错机制
- 未经过真实环境测试

---

## 概述

BlindVision 服务端是一个基于 FastAPI 的后端服务，为盲人用户提供环境视觉分析。通过多轮探索会话，引导用户安全穿越复杂环境。

## 技术栈

- **框架**: FastAPI
- **服务器**: Uvicorn
- **数据验证**: Pydantic
- **HTTP 客户端**: httpx
- **环境配置**: python-dotenv

## 项目结构

```
blind-vision-core/
├── main.py                 # FastAPI 应用入口
├── config.py               # 配置和环境变量
├── schemas.py              # Pydantic 请求/响应模型
├── services/
│   ├── vision_service.py   # GLM-4V 视觉分析
│   ├── session_manager.py  # 多轮会话管理
│   └── intent_parser.py    # 用户意图解析
├── utils/
│   └── frame_loader.py     # 图像帧验证和处理
├── .env                    # 环境变量（API 密钥）
├── requirements.txt        # Python 依赖
└── .venv/                  # 虚拟环境
```

## API 接口

### 健康检查

```bash
GET /health
```

响应：
```json
{"status": "ok"}
```

### 单次分析（兼容旧版）

```bash
POST /analyze
Content-Type: application/json

{
  "frames": ["base64_image1", "base64_image2"]
}
```

### 会话管理

#### 创建会话
```bash
POST /session/start
```

响应：
```json
{
  "session_id": "uuid",
  "state": "INIT",
  "message": "会话已创建，请发送图片帧或带问题的图片帧。"
}
```

#### 获取会话状态
```bash
GET /session/{session_id}
```

#### 提交图像帧（探索）
```bash
POST /session/{session_id}/frame
Content-Type: application/json

{
  "frames": ["base64_image"]
}
```

#### 提交问题和图像帧
```bash
POST /session/{session_id}/query
Content-Type: application/json

{
  "frames": ["base64_image"],
  "query": "前面能走吗？"
}
```

## 响应格式

所有探索接口返回：

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

## 状态机

服务端实现状态机，状态转换如下：

```
INIT → OBSERVE → EXPLORE → DECIDE → FINAL_ANSWER
         ↑          ↓
         └──────────┘ (调整手机 / 需要更多信息)
```

### 状态说明

- **INIT**: 会话已创建，等待第一帧图像
- **OBSERVE**: 正在分析当前帧
- **EXPLORE**: 引导用户获取更多信息
- **DECIDE**: 做出最终决策
- **FINAL_ANSWER**: 完成，会话结束
- **EXPIRED**: 会话已过期

## 动作类型

服务端返回三种类型的动作：

1. **adjust_camera**: 用户需要调整手机角度
   - 方向：left, right, up, down
2. **explore_direction**: 用户需要探索环境
   - 方向：forward, backward, left, right
3. **final_answer**: 信息足够，给出最终判断

## 配置

在根目录创建 `.env` 文件：

```bash
ZHIPU_API_KEY=your_api_key_here
MOCK_MODE=false
```

## 安装

```bash
# 创建虚拟环境
python -m venv .venv

# 激活虚拟环境
source .venv/bin/activate  # Linux/Mac
# 或
.venv\Scripts\activate  # Windows

# 安装依赖
pip install -r requirements.txt

# 启动服务
uvicorn main:app --reload
```

服务将在 `http://127.0.0.1:8000` 启动。

## 测试

```bash
# 健康检查
curl http://127.0.0.1:8000/health

# 分析接口
curl -X POST http://127.0.0.1:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{"frames":["base64_string_here"]}'
```

## 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| ZHIPU_API_KEY | 智谱 AI GLM-4V API 密钥 | 是 |
| MOCK_MODE | 启用模拟模式（用于测试） | 否 |
| LOG_LEVEL | 日志级别 (INFO, DEBUG 等) | 否 |

## 错误处理

API 返回适当的 HTTP 状态码：

- **200**: 成功
- **400**: 请求无效（缺少图像帧、格式错误）
- **404**: 会话不存在
- **409**: 会话状态冲突（已过期或已结束）
- **500**: 内部服务器错误

## 开发说明

- 所有代码均有类型注解
- 使用 Pydantic 进行请求/响应验证
- 实现完善的错误处理和日志记录
- 提供模拟模式，可在无 API 密钥情况下测试
