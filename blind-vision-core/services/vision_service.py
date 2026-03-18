import json
import logging
import re
from typing import Any, Dict, List, Optional

import httpx

from config import API_TIMEOUT, MOCK_MODE, ZHIPU_API_KEY, ZHIPU_API_URL, ZHIPU_MODEL
from schemas import AnalyzeResponse, IntentParsed, ModelExploreOutput

from services.intent_parser import parse_intent

logger = logging.getLogger(__name__)

# ── Legacy single-shot prompt (backward compatible) ──────────

LEGACY_SYSTEM_PROMPT = """你是一个帮助盲人判断前方环境的视觉助手。

请分析这些图像帧，并回答以下问题：

1. 是否存在路口
2. 是否有红绿灯
3. 红绿灯状态
4. 是否存在台阶
5. 是否存在明显障碍物

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
- 禁止使用"绝对安全"
- 可以使用"没有明显障碍"
- 决定权在用户"""

# ── Exploration system prompt (unified for /frame and /query) ────────

EXPLORE_SYSTEM_PROMPT = """你是一个帮助盲人理解周围环境的视觉助手。

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

规则5：最多探索{max_rounds}轮，当前是第{current_round}轮（共{max_rounds}轮）。如果仍不确定，需要给出当前最佳判断。

规则6：禁止说"绝对安全"或类似表达。

规则7：决定权始终在用户。

规则8：尽量像真人帮助一样自然简洁。

规则9：所有方向必须以用户身体为参考（前方、左侧、右侧），禁止使用画面方向。

规则10：如果用户提出了具体问题，你的探索和最终回答必须围绕该问题。
信息足够回答时，action 必须是 final_answer，assistant_message 必须直接回答用户的问题。
信息不足时，继续引导探索，但探索方向要针对用户的问题。

当你需要用户移动手机时，请选择以下动作：

adjust_camera：
用于调整手机角度
方向：left / right / up / down

explore_direction：
用于探索环境
方向：forward / backward / left / right

final_answer：
当信息已经足够时使用
方向：none

必须只输出 JSON，不要输出任何解释文本。格式：

{{
  "action": "adjust_camera 或 explore_direction 或 final_answer",
  "direction": "left / right / up / down / forward / backward / none",
  "environment_summary": "简要环境描述",
  "risk_detected": true或false,
  "assistant_message": "给用户的语音提示",
  "confidence": 0.0到1.0之间的数字,
  "risk_level": "low / medium / high",
  "distance_estimate": "near / mid / far / none",
  "affects_path": true或false
}}"""

# ── Intent guidance templates ─────────────────────────────────

INTENT_GUIDANCE = {
    "CROSS_ROAD": "用户想要过马路（方向：{direction}）。你必须重点分析：1）红绿灯状态（红/绿/黄）2）是否有车辆通过、车辆距离 3）路口结构、是否有斑马线 4）行人信号灯。如果是红灯→不建议通行；如果是绿灯但有车→建议稍等；如果是绿灯无车→可以考虑通行；如果倒计时<5秒→建议等下一次绿灯。",
    "TRAFFIC_LIGHT_CHECK": "用户想知道红绿灯状态。必须明确告知灯色，如有倒计时也要说明。",
    "PATH_CHECK": "用户想知道前方能否通行。必须描述路况、是否有障碍、给出通行建议。",
    "ENVIRONMENT_SCAN": "用户想了解前方环境。描述前方有什么（路口、建筑物等），并给出建议。",
    "OBSTACLE_CHECK": "用户想知道前方是否有障碍物。明确告知是否有障碍物及位置。",
    "STAIR_CHECK": "用户想知道是否有台阶。明确告知是否有台阶。",
    "GENERAL_QUESTION": "用户提出了一个问题。请基于环境信息直接回答。",
}


class VisionServiceError(Exception):
    pass


class VisionService:
    def __init__(self) -> None:
        if not MOCK_MODE and not ZHIPU_API_KEY:
            logger.warning("ZHIPU_API_KEY is not set. API calls will fail.")

    # ── Legacy single-shot analysis (backward compatible) ─────

    def analyze_frames(self, frames: List[str]) -> Dict[str, Any]:
        if MOCK_MODE:
            return self._mock_analyze_response()

        if not ZHIPU_API_KEY:
            raise VisionServiceError("ZHIPU_API_KEY is not configured")

        payload = self._build_legacy_payload(frames)
        raw_content = self._call_api(payload)
        result = self._parse_and_validate(raw_content, AnalyzeResponse)
        return result

    # ── Unified exploration round (used by both /frame and /query) ────

    def explore_round(
        self,
        conversation_messages: List[Dict[str, Any]],
        current_round: int,
        max_rounds: int,
        is_forced_final: bool = False,
        user_query: Optional[str] = None,
        intent: Optional[IntentParsed] = None,
    ) -> tuple[str, Dict[str, Any]]:
        """
        Run one exploration round.

        When user_query is provided, the model explores with the question as context
        and gives final_answer only when it has enough info to answer that question.
        """
        if MOCK_MODE:
            return self._mock_explore_response(current_round, max_rounds, user_query)

        if not ZHIPU_API_KEY:
            raise VisionServiceError("ZHIPU_API_KEY is not configured")

        system_prompt = EXPLORE_SYSTEM_PROMPT.format(
            max_rounds=max_rounds,
            current_round=current_round,
        )

        # Inject intent-specific guidance when there's a user query
        if intent and intent.intent in INTENT_GUIDANCE:
            direction = intent.direction or "前方"
            guidance = INTENT_GUIDANCE[intent.intent].format(direction=direction)
            system_prompt += f"\n\n【用户意图】\n用户问题：「{user_query}」\n{guidance}"
        elif user_query:
            system_prompt += f"\n\n【用户意图】\n用户问题：「{user_query}」\n请围绕这个问题进行分析，信息足够时在 assistant_message 中直接回答。"

        if is_forced_final:
            conversation_messages.append(
                {
                    "role": "user",
                    "content": "这是最后一轮，请给出你的最终判断。action 必须是 final_answer。",
                }
            )

        messages = [
            {"role": "system", "content": system_prompt},
            *conversation_messages,
        ]

        payload = {
            "model": ZHIPU_MODEL,
            "messages": messages,
            "temperature": 0.1,
            "top_p": 0.7,
        }

        raw_content = self._call_api(payload)
        parsed = self._parse_and_validate(raw_content, ModelExploreOutput)

        return raw_content, parsed

    # ── Payload builders ──────────────────────────────────────

    def _build_legacy_payload(self, frames: List[str]) -> Dict[str, Any]:
        content: List[Dict[str, Any]] = [{"type": "text", "text": LEGACY_SYSTEM_PROMPT}]

        for frame in frames:
            if frame.startswith("data:"):
                image_url = frame
            else:
                image_url = f"data:image/jpeg;base64,{frame}"
            content.append({"type": "image_url", "image_url": {"url": image_url}})

        return {
            "model": ZHIPU_MODEL,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0.1,
            "top_p": 0.7,
        }

    # ── API call ──────────────────────────────────────────────

    def _call_api(self, payload: Dict[str, Any]) -> str:
        headers = {
            "Authorization": f"Bearer {ZHIPU_API_KEY}",
            "Content-Type": "application/json",
        }

        try:
            with httpx.Client(timeout=API_TIMEOUT) as client:
                response = client.post(
                    ZHIPU_API_URL,
                    headers=headers,
                    json=payload,
                )

            if response.status_code != 200:
                error_body = response.text
                logger.error(
                    "Zhipu API returned status %d: %s", response.status_code, error_body
                )
                raise VisionServiceError(
                    f"API request failed with status {response.status_code}: {error_body}"
                )

            resp_json = response.json()
            choices = resp_json.get("choices", [])
            if not choices:
                raise VisionServiceError("API response contains no choices")

            content = choices[0].get("message", {}).get("content", "")
            if not content:
                raise VisionServiceError("API response content is empty")

            return content

        except httpx.TimeoutException:
            logger.error("Zhipu API request timed out after %ds", API_TIMEOUT)
            raise VisionServiceError(
                f"API request timed out after {API_TIMEOUT} seconds"
            )
        except httpx.RequestError as e:
            logger.error("Network error calling Zhipu API: %s", str(e))
            raise VisionServiceError(f"Network error: {str(e)}")
        except VisionServiceError:
            raise
        except Exception as e:
            logger.error("Unexpected error calling Zhipu API: %s", str(e))
            raise VisionServiceError(f"Unexpected error: {str(e)}")

    # ── JSON parsing ──────────────────────────────────────────

    def _parse_and_validate(
        self, raw_content: str, schema_class: type
    ) -> Dict[str, Any]:
        parsed = self._try_parse_json(raw_content)

        if parsed is None:
            json_match = re.search(
                r"```(?:json)?\s*\n?(.*?)\n?```", raw_content, re.DOTALL
            )
            if json_match:
                parsed = self._try_parse_json(json_match.group(1))

        if parsed is None:
            parsed = self._extract_json_object(raw_content)

        if parsed is None:
            logger.error(
                "Failed to parse JSON from model output: %s", raw_content[:500]
            )
            raise VisionServiceError(
                "Model returned non-JSON response that could not be parsed"
            )

        try:
            validated = schema_class(**parsed)
            return validated.model_dump()
        except Exception as e:
            logger.error("Response validation failed: %s. Raw: %s", str(e), parsed)
            raise VisionServiceError(f"Response validation failed: {str(e)}")

    @staticmethod
    def _try_parse_json(text: str) -> Dict[str, Any] | None:
        try:
            text = text.strip()
            result = json.loads(text)
            if isinstance(result, dict):
                return result
            return None
        except (json.JSONDecodeError, ValueError):
            return None

    def _extract_json_object(self, text: str) -> Dict[str, Any] | None:
        start = text.find("{")
        if start == -1:
            return None
        brace_count = 0
        for i, char in enumerate(text[start:]):
            if char == "{":
                brace_count += 1
            elif char == "}":
                brace_count -= 1
                if brace_count == 0:
                    json_str = text[start : start + i + 1]
                    return self._try_parse_json(json_str)
        return None

    # ── Mock responses ────────────────────────────────────────

    @staticmethod
    def _mock_analyze_response() -> Dict[str, Any]:
        return AnalyzeResponse(
            intersection=True,
            traffic_light="red",
            stairs=False,
            obstacle=True,
            description="前方是路口，有红灯，右侧有障碍物。",
            suggestion="建议在原地等待绿灯，注意右侧障碍物。",
        ).model_dump()

    @staticmethod
    def _mock_explore_response(
        current_round: int,
        max_rounds: int,
        user_query: Optional[str] = None,
    ) -> tuple[str, Dict[str, Any]]:
        """
        Mock for exploration mode.
        - With user_query: round 1 explore, round 2 final_answer with query-specific answer
        - Without user_query: round 1 adjust, round 2 explore, round 3+ final_answer
        """
        if user_query:
            # Query mode: faster convergence, answer oriented
            if current_round == 1:
                data = {
                    "action": "adjust_camera",
                    "direction": "up",
                    "environment_summary": "画面主要是地面，无法判断前方环境。",
                    "risk_detected": False,
                    "assistant_message": "请把手机稍微抬高一点，我看看前方路口的情况。",
                    "confidence": 0.2,
                    "risk_level": "low",
                    "distance_estimate": "none",
                    "affects_path": False,
                }
            else:
                # Give final answer addressing the user's query
                data = {
                    "action": "final_answer",
                    "direction": "none",
                    "environment_summary": "前方是十字路口，有红绿灯，当前红灯。左侧有斑马线，右侧有停放的车辆。",
                    "risk_detected": True,
                    "assistant_message": "前方是路口，现在是红灯，不建议通行。建议等待绿灯再通过，右侧有停放的车辆，注意避让。",
                    "confidence": 0.85,
                    "risk_level": "high",
                    "distance_estimate": "near",
                    "affects_path": True,
                }
        else:
            # Pure exploration mode
            if current_round == 1:
                data = {
                    "action": "adjust_camera",
                    "direction": "up",
                    "environment_summary": "画面主要是地面，无法判断前方环境。",
                    "risk_detected": False,
                    "assistant_message": "请把手机稍微抬高一点，我看看前方。",
                    "confidence": 0.2,
                    "risk_level": "low",
                    "distance_estimate": "none",
                    "affects_path": False,
                }
            elif current_round == 2:
                data = {
                    "action": "explore_direction",
                    "direction": "left",
                    "environment_summary": "前方似乎有路口，但左侧画面不完整。",
                    "risk_detected": False,
                    "assistant_message": "请慢慢向左转一下手机，我看看左边的情况。",
                    "confidence": 0.4,
                    "risk_level": "low",
                    "distance_estimate": "mid",
                    "affects_path": False,
                }
            else:
                data = {
                    "action": "final_answer",
                    "direction": "none",
                    "environment_summary": "前方是十字路口，有红绿灯，当前红灯。左侧有人行道，右侧有停放的车辆。",
                    "risk_detected": True,
                    "assistant_message": "前方是路口，现在是红灯，建议等待绿灯再通过。右侧有停放的车辆，注意避让。",
                    "confidence": 0.85,
                    "risk_level": "high",
                    "distance_estimate": "near",
                    "affects_path": True,
                }

        raw = json.dumps(data, ensure_ascii=False)
        return raw, data
