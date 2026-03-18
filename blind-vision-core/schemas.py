from typing import List, Literal, Optional
from pydantic import BaseModel, Field, field_validator


# ── Type aliases ──────────────────────────────────────────────

ActionType = Literal["adjust_camera", "explore_direction", "final_answer"]
DirectionType = Literal["left", "right", "up", "down", "forward", "backward", "none"]
SessionState = Literal[
    "INIT", "OBSERVE", "EXPLORE", "DECIDE", "FINAL_ANSWER", "EXPIRED"
]

IntentType = Literal[
    "CROSS_ROAD",
    "PATH_CHECK",
    "ENVIRONMENT_SCAN",
    "TRAFFIC_LIGHT_CHECK",
    "OBSTACLE_CHECK",
    "STAIR_CHECK",
    "GENERAL_QUESTION",
]

CrossDirection = Literal["forward", "backward", "left", "right", "unknown"]

RiskLevel = Literal["low", "medium", "high"]
DistanceEstimate = Literal["near", "mid", "far", "none"]


# ── Legacy single-shot models (backward compatible) ──────────


class AnalyzeRequest(BaseModel):
    frames: List[str] = Field(
        ...,
        min_length=1,
        max_length=10,
        description="Base64-encoded image frames extracted from video",
    )

    @field_validator("frames")
    @classmethod
    def validate_frames_not_empty(cls, v: List[str]) -> List[str]:
        for i, frame in enumerate(v):
            if not frame or not frame.strip():
                raise ValueError(f"Frame at index {i} is empty")
        return v


class AnalyzeResponse(BaseModel):
    intersection: bool = Field(..., description="是否为路口")
    traffic_light: Literal["red", "green", "unknown"] = Field(
        ..., description="红绿灯状态"
    )
    stairs: bool = Field(..., description="是否有台阶")
    obstacle: bool = Field(..., description="是否有明显障碍物")
    description: str = Field(..., description="简要环境描述")
    suggestion: str = Field(..., description="行动建议（禁止绝对安全表达）")


# ── Session-based exploration models ─────────────────────────


class SessionFrameRequest(BaseModel):
    frames: List[str] = Field(
        ...,
        min_length=1,
        max_length=10,
        description="Base64-encoded image frames for this observation round",
    )
    frame_ids: Optional[List[str]] = Field(
        None,
        description="Optional unique IDs for each frame (same length as frames)",
    )

    @field_validator("frames")
    @classmethod
    def validate_frames_not_empty(cls, v: List[str]) -> List[str]:
        for i, frame in enumerate(v):
            if not frame or not frame.strip():
                raise ValueError(f"Frame at index {i} is empty")
        return v

    @field_validator("frame_ids")
    @classmethod
    def validate_frame_ids_match(
        cls, v: Optional[List[str]], info
    ) -> Optional[List[str]]:
        if v is None:
            return v
        frames = info.data.get("frames", [])
        if len(v) != len(frames):
            raise ValueError(
                f"frame_ids length ({len(v)}) must match frames length ({len(frames)})"
            )
        return v


class SessionQueryRequest(BaseModel):
    """User asks a question AND provides frames. This enters the explore state machine."""

    query: str = Field(
        ...,
        min_length=1,
        max_length=500,
        description="用户的自然语言问题，如：我能过马路吗？",
    )
    frames: List[str] = Field(
        ...,
        min_length=1,
        max_length=10,
        description="随问题一起发送的图片帧（必填）",
    )
    frame_ids: Optional[List[str]] = Field(
        None,
        description="Optional unique IDs for each frame (same length as frames)",
    )

    @field_validator("query")
    @classmethod
    def validate_query_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("query cannot be empty")
        return v.strip()

    @field_validator("frames")
    @classmethod
    def validate_frames_not_empty(cls, v: List[str]) -> List[str]:
        for i, frame in enumerate(v):
            if not frame or not frame.strip():
                raise ValueError(f"Frame at index {i} is empty")
        return v

    @field_validator("frame_ids")
    @classmethod
    def validate_frame_ids_match(
        cls, v: Optional[List[str]], info
    ) -> Optional[List[str]]:
        if v is None:
            return v
        frames = info.data.get("frames", [])
        if len(v) != len(frames):
            raise ValueError(
                f"frame_ids length ({len(v)}) must match frames length ({len(frames)})"
            )
        return v


class ModelExploreOutput(BaseModel):
    action: ActionType
    direction: DirectionType
    environment_summary: str
    risk_detected: bool
    assistant_message: str
    confidence: float = Field(ge=0.0, le=1.0)
    risk_level: RiskLevel = Field(..., description="风险等级：low/medium/high")
    distance_estimate: DistanceEstimate = Field(
        ..., description="距离估计：near/mid/far/none"
    )
    affects_path: bool = Field(..., description="是否影响通行路径")


class ExploreResponse(BaseModel):
    """Unified response for both /frame and /query — same JSON protocol."""

    session_id: str = Field(..., description="会话 ID")
    round: int = Field(..., description="当前探索轮次 (1-based)")
    max_rounds: int = Field(..., description="最大探索轮次")
    state: SessionState = Field(..., description="当前状态机状态")
    frame_ids: Optional[List[str]] = Field(None, description="本次请求处理的帧 ID 列表")

    # The model output protocol
    action: ActionType = Field(..., description="下一步动作")
    direction: DirectionType = Field(..., description="移动方向")
    environment_summary: str = Field(..., description="环境事实描述")
    risk_detected: bool = Field(..., description="是否存在风险")
    assistant_message: str = Field(..., description="给用户的语音提示")
    confidence: float = Field(..., ge=0.0, le=1.0, description="判断置信度")
    risk_level: RiskLevel = Field(..., description="风险等级：low/medium/high")
    distance_estimate: DistanceEstimate = Field(
        ..., description="距离估计：near/mid/far/none"
    )
    affects_path: bool = Field(..., description="是否影响通行路径")

    # Query context (only present when triggered via /query)
    query: Optional[str] = Field(None, description="用户的原始问题（仅 /query 时返回）")
    intent: Optional[IntentType] = Field(
        None, description="识别的用户意图（仅 /query 时返回）"
    )


class IntentParsed(BaseModel):
    intent: IntentType
    direction: Optional[CrossDirection] = Field(None, description="用户想走的方向")
    confidence: float = Field(ge=0.0, le=1.0)


class SessionStartResponse(BaseModel):
    session_id: str
    state: SessionState
    message: str


class SessionStatusResponse(BaseModel):
    session_id: str
    state: SessionState
    round: int
    max_rounds: int
    created_at: str
    history_count: int
    active_query: Optional[str] = None


class HealthResponse(BaseModel):
    status: str = "ok"


class ErrorResponse(BaseModel):
    detail: str
