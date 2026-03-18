🎯 项目目标

开发一个「盲人出行视觉判断服务 API」。

该服务接收多张图片（从视频抽帧而来），调用多模态大模型 API 进行分析，输出结构化 JSON 判断结果。

此服务只负责“环境理解 + 行动建议”，不涉及移动端开发。

一、技术栈要求

请使用：

Python 3.10+

FastAPI

Uvicorn

Pydantic

Requests 或 httpx

python-dotenv

项目应可直接运行：

uvicorn main:app --reload
二、项目结构

请生成以下结构：

blind-vision-core/

├── main.py
├── config.py
├── schemas.py
├── services/
│   ├── vision_service.py
│
├── utils/
│   ├── frame_loader.py
│
├── .env
├── requirements.txt
三、核心 API 功能
1️⃣ 接口说明
POST /analyze

请求类型：

{
  "frames": ["base64_image1", "base64_image2", "..."]
}

说明：

frames 为从视频抽取的图片

至少 1 张，最多 10 张

2️⃣ 返回格式

必须严格返回：

{
  "intersection": true,
  "traffic_light": "red",
  "stairs": false,
  "obstacle": true,
  "description": "前方是路口，有红灯。",
  "suggestion": "建议等待。"
}

字段定义：

intersection: 是否为路口

traffic_light: red / green / unknown

stairs: 是否有台阶

obstacle: 是否有明显障碍物

description: 简要环境描述

suggestion: 行动建议（禁止绝对安全表达）

四、多模态模型要求

使用：

Zhipu AI GLM-4V API

调用方式：

使用 REST API

从 .env 中读取 API_KEY

环境变量：

ZHIPU_API_KEY=your_api_key_here
五、Prompt 设计（必须严格实现）

构造固定 Prompt：

你是一个帮助盲人判断前方环境的视觉助手。

请分析这些图像帧，并回答以下问题：

1 是否存在路口
2 是否有红绿灯
3 红绿灯状态
4 是否存在台阶
5 是否存在明显障碍物

请只返回 JSON：

{
"intersection": true/false,
"traffic_light": "red/green/unknown",
"stairs": true/false,
"obstacle": true/false,
"description": "简要环境描述",
"suggestion": "行动建议"
}

注意：
- 禁止使用“绝对安全”
- 可以使用“没有明显障碍”
- 决定权在用户
六、Vision Service 实现要求

vision_service.py 需实现：

class VisionService:
    def analyze_frames(self, frames: List[str]) -> dict:
        pass

流程：

构造 prompt

传入多张图片

调用 GLM-4V API

解析返回内容

校验 JSON 格式

如果模型输出非 JSON，进行二次修正

返回标准结构

七、错误处理要求

必须处理：

API 超时

JSON 解析失败

模型返回非法格式

空图片输入

返回 HTTP 400 或 500

八、测试接口

额外提供：

GET /health

返回：

{"status": "ok"}
九、可选功能（加分）

本地测试模式（mock 模型）

日志记录

保存输入帧到 debug 文件夹

十、代码质量要求

使用类型注解

使用 Pydantic 校验请求

所有异常必须捕获

不要写多余功能

代码必须可运行

十一、测试方式

开发完成后，应支持：

curl -X POST http://127.0.0.1:8000/analyze \
-H "Content-Type: application/json" \
-d '{"frames":["base64_string_here"]}'
十二、开发目标

这是一个 Demo。

目标是：

稳定输出结构化判断

便于后续接入 iOS 或 Android

可用于视频调试

不要做前端。

不要做数据库。

不要做用户系统。


开发一个“会话式视觉辅助系统”后端 API。

系统能力：

支持多轮会话

接收图像帧 + 用户文本

根据状态机判断：

是否需要调整手机

是否需要更多信息

是否可以给出最终判断

输出结构化 JSON 响应

维护会话上下文（内存级别即可）

该项目为 Demo，不需要数据库，不需要用户系统。


一、系统核心概念
1️⃣ 会话（Session）

系统必须支持多轮会话。

每个 session 维护：

session_id

conversation_history（文本摘要）

last_environment_summary

last_risk_detected

turn_count

state

状态定义：

INIT
NEED_MORE_INFO
ADJUSTING_CAMERA
READY_TO_ANSWER
FINISHED

使用内存字典存储 session。

四、API 设计
1️⃣ 创建会话
POST /session/create

返回：

{
  "session_id": "uuid"
}
2️⃣ 会话交互
POST /session/{session_id}/interact

请求：

{
  "frames": ["base64_image1", "..."],
  "user_message": "前面安全吗？"
}

说明：

frames 可为空（允许纯文本轮）

最多 5 张图片

返回格式（必须严格）
{
  "action": "adjust_camera | ask_for_more | final_answer",
  "assistant_message": "语音输出内容",
  "environment_summary": "环境事实描述",
  "risk_detected": true,
  "session_state": "当前状态"
}
五、状态机逻辑（必须实现）

在 state_machine.py 中实现核心决策逻辑。

决策流程

如果没有 frames：
→ action = ask_for_more

如果图像明显构图异常：

地面过多

天空过多

严重倾斜
→ action = adjust_camera

调用视觉模型分析

如果模型判断信息不足：
→ action = ask_for_more

如果识别到风险：
→ action = final_answer

如果没有明显风险：
→ action = final_answer

六、视觉模型服务

使用：

Zhipu AI GLM-4V

从 .env 读取：

ZHIPU_API_KEY=your_key_here
七、Prompt 构建规则（必须严格实现）

在 prompt_builder.py 中实现。

系统 Prompt
你是一个帮助盲人理解周围环境的视觉助手。

目标：
1. 分析当前图像
2. 判断环境结构
3. 判断是否存在风险
4. 如果信息不足，说明原因
5. 如果构图不合理，建议如何移动手机
6. 输出结构化 JSON

必须：
- 先描述环境
- 再给建议
- 禁止使用“绝对安全”
- 决定权在用户
模型输出格式（强约束）

模型必须输出：

{
  "environment_summary": "...",
  "intersection": true/false,
  "traffic_light": "red/green/unknown",
  "stairs": true/false,
  "obstacle": true/false,
  "composition_issue": "none | too_low | too_high | tilted",
  "info_sufficient": true/false,
  "suggestion": "..."
}

如果模型输出非法 JSON：

自动重试一次

再失败则返回 500

八、构图判断规则（简单版）

在状态机中增加逻辑：

如果模型返回：

composition_issue != "none"

则：

action = adjust_camera

assistant_message 示例：

too_low → "请抬高手机一点。"

too_high → "请稍微降低手机。"

tilted → "请保持手机水平。"

九、最终回答规则

当：

info_sufficient == true

生成 assistant_message：

格式：

{environment_summary}，{suggestion}

例如：

前方是路口，红灯，建议等待。

如果没有风险：

前方人行道，没有明显障碍，可以继续前进。
十、会话记忆机制

每一轮：

保存 environment_summary

保存 risk_detected

递增 turn_count

如果 turn_count > 5 且仍未 final_answer：

强制输出当前最佳判断。

十一、错误处理

必须处理：

无效 session_id

模型 API 失败

JSON 解析错误

超时

所有异常必须返回 JSON 错误信息。

十二、日志要求

使用 logging：

记录：

session_id

action

model latency

risk_detected

十三、测试接口

提供：

GET /health

返回：

{
  "status": "ok"
}
十四、代码质量要求

全部类型注解

模块化清晰

不写多余功能

可直接运行

所有 API 有示例注释

十五、运行目标

完成后必须支持：

POST /session/create
POST /session/{id}/interact

并支持多轮测试。

🎯 开发目标总结

这是一个：

多轮视觉对话系统核心服务。

它不是图像识别接口。

它是：

会话式

有状态

可引导

可判断

可持续交互

---------------------------------------------------------------------

升级一下这个系统

系统目标：

像真人一样探索环境

引导用户调整手机

获取足够信息

给出环境信息 + 行动建议

但必须限制：

不允许无限探索

最多 5轮探索

必须逐步收敛

状态机流程：

INIT
↓
OBSERVE
↓
EXPLORE (引导用户移动手机)
↓
OBSERVE
↓
DECIDE
↓
FINAL_ANSWER
二、探索策略（非常关键）

AI 每次只能做 一个动作。

动作优先级：

1️⃣ 如果画面构图不合理
→ 调整手机

2️⃣ 如果关键目标未出现
→ 指挥用户探索

3️⃣ 如果信息足够
→ 输出最终判断

三、允许的动作集合

模型只能输出以下动作：

adjust_camera
explore_direction
final_answer
adjust_camera

用于调整构图。

方向：

up
down
left
right
explore_direction

用于探索环境。

方向：

forward
backward
left
right

例如：

“往前走一步我看看。”

final_answer

给出最终判断。

四、方向枚举

完整方向集合：

left
right
up
down
forward
backward
none
五、探索终止条件

模型必须遵守：

如果满足以下任意条件：

1️⃣ 已识别关键环境（路口/障碍物/台阶）

2️⃣ 已探索 3–5 次

3️⃣ 画面信息基本充分

则必须：

action = final_answer

不允许继续探索。

六、完整输出 JSON 协议

模型 必须只输出 JSON。

格式如下：

{
  "action": "adjust_camera | explore_direction | final_answer",
  "direction": "left | right | up | down | forward | backward | none",
  "environment_summary": "简要环境描述",
  "risk_detected": true,
  "assistant_message": "给用户的语音提示",
  "confidence": 0.0
}

字段说明：

字段	含义
action	下一步动作
direction	移动方向
environment_summary	环境事实
risk_detected	是否存在风险
assistant_message	语音提示
confidence	判断置信度
七、核心系统提示词（System Prompt）

下面是完整提示词。

你可以直接用于模型调用。

System Prompt
你是一个帮助盲人理解周围环境的视觉助手。

你的任务是：
通过分析用户手机拍摄的图像帧，帮助用户了解前方环境，
并在必要时指导用户如何移动手机以获取更多信息。

你的目标是：
1. 判断环境结构
2. 判断是否存在行走风险
3. 在信息不足时，引导用户移动手机获取更多画面
4. 在信息足够时，给出环境信息和行动建议

请遵守以下规则：

规则1：先描述环境，再给建议。

规则2：如果画面构图明显不合理（只看到地面、天空、严重倾斜），
优先要求用户调整手机角度。

规则3：如果环境信息不足，可以引导用户探索环境。

规则4：每次只能要求一个动作。

规则5：最多探索5轮，如果仍不确定，需要给出当前最佳判断。

规则6：禁止说“绝对安全”或类似表达。

规则7：决定权始终在用户。

规则8：尽量像真人帮助一样自然简洁。

例如：

前方是路口，红灯，建议等待。

或者：

前方人行道，没有明显障碍，可以继续前进。
八、动作策略提示

在 prompt 中加入：

当你需要用户移动手机时，请选择以下动作：

adjust_camera：
用于调整手机角度

方向：
left
right
up
down


explore_direction：
用于探索环境

方向：
forward
backward
left
right


final_answer：
当信息已经足够时使用
九、输出格式约束
必须只输出 JSON。
不要输出任何解释文本。
十、输出示例
示例1：需要调整手机
{
  "action": "adjust_camera",
  "direction": "up",
  "environment_summary": "画面主要是地面",
  "risk_detected": false,
  "assistant_message": "请抬高手机一点。",
  "confidence": 0.3
}
示例2：需要探索
{
  "action": "explore_direction",
  "direction": "left",
  "environment_summary": "前方似乎有路口，但画面不完整",
  "risk_detected": false,
  "assistant_message": "请慢慢向左移动手机，我看看周围环境。",
  "confidence": 0.4
}
示例3：最终判断
{
  "action": "final_answer",
  "direction": "none",
  "environment_summary": "前方是路口，有红绿灯",
  "risk_detected": true,
  "assistant_message": "前方是路口，现在是红灯，建议等待。",
  "confidence": 0.85
}