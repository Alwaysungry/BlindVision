import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from config import MOCK_MODE
from schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    ErrorResponse,
    ExploreResponse,
    HealthResponse,
    IntentParsed,
    SessionFrameRequest,
    SessionQueryRequest,
    SessionStartResponse,
    SessionStatusResponse,
)
from services.session_manager import SessionManager, SessionStateError
from services.vision_service import VisionService, VisionServiceError
from utils.frame_loader import validate_frames
from services.intent_parser import parse_intent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="盲人出行视觉判断服务",
    description="支持单次分析和多轮探索的视觉辅助 API",
    version="3.0.0",
)

vision_service = VisionService()
session_manager = SessionManager()


# ── Health ────────────────────────────────────────────────────


@app.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    return HealthResponse(status="ok")


# ── Legacy single-shot (backward compatible) ─────────────────


@app.post(
    "/analyze",
    response_model=AnalyzeResponse,
    responses={400: {"model": ErrorResponse}, 500: {"model": ErrorResponse}},
)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    logger.info("POST /analyze: %d frame(s), mock=%s", len(request.frames), MOCK_MODE)

    try:
        cleaned_frames = validate_frames(request.frames)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    try:
        result = vision_service.analyze_frames(cleaned_frames)
        return AnalyzeResponse(**result)
    except VisionServiceError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except Exception as e:
        logger.error("Unexpected error: %s", str(e))
        raise HTTPException(status_code=500, detail="Internal server error")


# ── Session management ─────────────────────────────────────────


@app.post(
    "/session/start",
    response_model=SessionStartResponse,
    responses={500: {"model": ErrorResponse}},
)
async def session_start() -> SessionStartResponse:
    session = session_manager.create_session()
    logger.info("POST /session/start → session_id=%s", session.session_id)
    return SessionStartResponse(
        session_id=session.session_id,
        state=session.state,
        message="会话已创建，请发送图片帧或带问题的图片帧。",
    )


@app.get(
    "/session/{session_id}/status",
    response_model=SessionStatusResponse,
    responses={404: {"model": ErrorResponse}},
)
async def session_status(session_id: str) -> SessionStatusResponse:
    session = session_manager.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    return SessionStatusResponse(
        session_id=session.session_id,
        state=session.state,
        round=session.current_round,
        max_rounds=session.max_rounds,
        created_at=datetime.fromtimestamp(
            session.created_at, tz=timezone.utc
        ).isoformat(),
        history_count=len(session.history),
        active_query=session.active_query,
    )


# ── Core: unified exploration round logic ─────────────────────


def _run_explore_round(
    session_id: str,
    frames: list[str],
    frame_ids: Optional[list[str]] = None,
    user_query: Optional[str] = None,
    intent: Optional[IntentParsed] = None,
) -> ExploreResponse:
    """
    Core state machine logic shared by /frame and /query.
    Both endpoints submit frames and get back the same ExploreResponse format.
    """
    session = session_manager.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    if session.state == "EXPIRED":
        raise HTTPException(status_code=409, detail="会话已过期")
    if session.is_terminal():
        raise HTTPException(status_code=409, detail="会话已结束（已给出最终判断）")

    try:
        cleaned_frames = validate_frames(frames)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    # If this is a query, set the active query on the session
    if user_query and not session.active_query:
        session_manager.set_active_query(
            session, user_query, intent.intent if intent else None
        )

    # Begin observation (state → OBSERVE)
    try:
        round_data = session_manager.begin_observation(
            session, cleaned_frames, user_query=user_query
        )
    except SessionStateError as e:
        raise HTTPException(status_code=409, detail=str(e))

    # Build conversation history & call model
    try:
        conversation_messages = session_manager.get_conversation_messages(session)
        is_forced_final = session.current_round >= session.max_rounds

        raw_response, parsed = vision_service.explore_round(
            conversation_messages=conversation_messages,
            current_round=session.current_round,
            max_rounds=session.max_rounds,
            is_forced_final=is_forced_final,
            user_query=session.active_query,
            intent=intent,
        )

        # Force final_answer if max rounds reached
        if is_forced_final and parsed.get("action") != "final_answer":
            parsed["action"] = "final_answer"
            parsed["direction"] = "none"
            if parsed["confidence"] < 0.5:
                parsed["confidence"] = 0.5
            parsed["assistant_message"] = (
                parsed.get("assistant_message", "")
                + " （已达最大探索次数，给出当前最佳判断。）"
            )

    except (VisionServiceError, Exception) as e:
        # Rollback
        if session.history and session.history[-1] is round_data:
            session.history.pop()
        session.current_round = max(0, session.current_round - 1)
        session.state = "EXPLORE" if session.current_round > 0 else "INIT"
        logger.error(
            "Vision error in session %s (rolled back to round %d): %s",
            session_id,
            session.current_round,
            str(e),
        )
        if isinstance(e, VisionServiceError):
            raise HTTPException(status_code=500, detail=str(e))
        raise HTTPException(status_code=500, detail="Internal server error")

    # Record response & transition state
    session_manager.record_model_response(session, round_data, raw_response, parsed)

    logger.info(
        "Session %s round %d: action=%s, state=%s, confidence=%.2f, query=%s",
        session_id,
        session.current_round,
        parsed.get("action"),
        session.state,
        parsed.get("confidence", 0),
        bool(session.active_query),
    )

    return ExploreResponse(
        session_id=session.session_id,
        round=session.current_round,
        max_rounds=session.max_rounds,
        state=session.state,
        frame_ids=frame_ids,
        action=parsed["action"],
        direction=parsed["direction"],
        environment_summary=parsed["environment_summary"],
        risk_detected=parsed["risk_detected"],
        assistant_message=parsed["assistant_message"],
        confidence=parsed["confidence"],
        risk_level=parsed["risk_level"],
        distance_estimate=parsed["distance_estimate"],
        affects_path=parsed["affects_path"],
        query=session.active_query,
        intent=session.active_intent,
    )


# ── POST /session/{id}/frame — pure exploration ───────────────


@app.post(
    "/session/{session_id}/frame",
    response_model=ExploreResponse,
    responses={
        400: {"model": ErrorResponse},
        404: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def session_submit_frame(
    session_id: str,
    request: SessionFrameRequest,
) -> ExploreResponse:
    """
    Submit frame(s) to an active exploration session.
    If the session has an active_query, the model continues exploring
    with that question as context.
    """
    return _run_explore_round(session_id, request.frames, request.frame_ids)


# ── POST /session/{id}/query — question + frames exploration ──


@app.post(
    "/session/{session_id}/query",
    response_model=ExploreResponse,
    responses={
        400: {"model": ErrorResponse},
        404: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def session_query(
    session_id: str,
    request: SessionQueryRequest,
) -> ExploreResponse:
    """
    User asks a question AND provides frames.

    This enters the same exploration state machine as /frame, but with the
    user's question as context. The model will:
    - If info is insufficient: return adjust_camera or explore_direction
    - If info is sufficient to answer the question: return final_answer

    The user should keep calling /frame (or /query again) with new frames
    until the model returns action=final_answer.
    """
    intent = parse_intent(request.query)
    return _run_explore_round(
        session_id,
        request.frames,
        request.frame_ids,
        user_query=request.query,
        intent=intent,
    )


# ── Error handler ─────────────────────────────────────────────


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
    )
