"""
In-memory session manager with state machine for multi-round exploration.

State transitions:
    INIT ──(frame/query)──▶ OBSERVE ──(model: adjust/explore)──▶ EXPLORE
                                     ──(model: final_answer)──▶ FINAL_ANSWER
    EXPLORE ──(frame/query)──▶ OBSERVE ──(model: adjust/explore)──▶ EXPLORE
                                       ──(model: final_answer)──▶ FINAL_ANSWER
                                       ──(max rounds reached)──▶ FINAL_ANSWER (forced)
"""

import logging
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from config import MAX_EXPLORE_ROUNDS, SESSION_EXPIRY
from schemas import IntentType, SessionState

logger = logging.getLogger(__name__)


@dataclass
class ConversationRound:
    round_number: int
    frames: List[str]
    user_query: Optional[str] = None
    model_response_raw: str = ""
    model_response: Dict[str, Any] = field(default_factory=dict)
    timestamp: float = field(default_factory=time.time)


@dataclass
class Session:
    session_id: str
    state: SessionState
    current_round: int
    max_rounds: int
    created_at: float
    history: List[ConversationRound] = field(default_factory=list)
    active_query: Optional[str] = None
    active_intent: Optional[IntentType] = None

    def is_expired(self) -> bool:
        return (time.time() - self.created_at) > SESSION_EXPIRY

    def is_terminal(self) -> bool:
        return self.state in ("FINAL_ANSWER", "EXPIRED")

    def can_accept_frame(self) -> bool:
        return self.state in ("INIT", "EXPLORE") and not self.is_expired()


class SessionManager:
    def __init__(self) -> None:
        self._sessions: Dict[str, Session] = {}

    def create_session(self) -> Session:
        self._cleanup_expired()
        session_id = uuid.uuid4().hex[:12]
        session = Session(
            session_id=session_id,
            state="INIT",
            current_round=0,
            max_rounds=MAX_EXPLORE_ROUNDS,
            created_at=time.time(),
        )
        self._sessions[session_id] = session
        logger.info("Created session %s", session_id)
        return session

    def get_session(self, session_id: str) -> Optional[Session]:
        session = self._sessions.get(session_id)
        if session is None:
            return None
        if session.is_expired():
            session.state = "EXPIRED"
            return session
        return session

    def set_active_query(
        self, session: Session, query: str, intent: Optional[IntentType] = None
    ) -> None:
        session.active_query = query
        session.active_intent = intent
        logger.info(
            "Session %s: active_query set to '%s' (intent=%s)",
            session.session_id,
            query[:50],
            intent,
        )

    def begin_observation(
        self, session: Session, frames: List[str], user_query: Optional[str] = None
    ) -> ConversationRound:
        if not session.can_accept_frame():
            raise SessionStateError(
                f"Session {session.session_id} cannot accept frames in state {session.state}"
            )

        session.current_round += 1
        session.state = "OBSERVE"

        round_data = ConversationRound(
            round_number=session.current_round,
            frames=frames,
            user_query=user_query,
        )
        session.history.append(round_data)
        logger.info(
            "Session %s: round %d, OBSERVE with %d frame(s)%s",
            session.session_id,
            session.current_round,
            len(frames),
            f", query='{user_query[:30]}...'" if user_query else "",
        )
        return round_data

    def record_model_response(
        self,
        session: Session,
        round_data: ConversationRound,
        raw_response: str,
        parsed: Dict[str, Any],
    ) -> None:
        round_data.model_response_raw = raw_response
        round_data.model_response = parsed

        action = parsed.get("action", "")
        is_last_round = session.current_round >= session.max_rounds

        if action == "final_answer" or is_last_round:
            session.state = "FINAL_ANSWER"
            if is_last_round and action != "final_answer":
                logger.warning(
                    "Session %s: max rounds reached, forcing FINAL_ANSWER (model wanted %s)",
                    session.session_id,
                    action,
                )
        elif action in ("adjust_camera", "explore_direction"):
            session.state = "EXPLORE"
        else:
            logger.warning(
                "Session %s: unexpected action '%s', treating as EXPLORE",
                session.session_id,
                action,
            )
            session.state = "EXPLORE"

        logger.info(
            "Session %s: round %d done, action=%s, new state=%s",
            session.session_id,
            session.current_round,
            action,
            session.state,
        )

    def get_conversation_messages(self, session: Session) -> List[Dict[str, Any]]:
        from config import MAX_IMAGE_HISTORY_ROUNDS

        messages: List[Dict[str, Any]] = []
        total_rounds = len(session.history)

        for i, round_data in enumerate(session.history):
            is_current_round = i == total_rounds - 1
            is_recent = (total_rounds - i) <= MAX_IMAGE_HISTORY_ROUNDS

            if is_recent or is_current_round:
                content: List[Dict[str, Any]] = []

                # If this round has a user query, include it as context
                if round_data.user_query:
                    content.append(
                        {
                            "type": "text",
                            "text": f"第{round_data.round_number}轮观察（用户问题：{round_data.user_query}）：请分析以下图像。",
                        }
                    )
                else:
                    content.append(
                        {
                            "type": "text",
                            "text": f"第{round_data.round_number}轮观察：请分析以下图像。",
                        }
                    )

                for frame in round_data.frames:
                    if frame.startswith("data:"):
                        image_url = frame
                    else:
                        image_url = f"data:image/jpeg;base64,{frame}"
                    content.append(
                        {"type": "image_url", "image_url": {"url": image_url}}
                    )
                messages.append({"role": "user", "content": content})
            else:
                summary = round_data.model_response.get(
                    "environment_summary", "（无摘要）"
                )
                messages.append(
                    {
                        "role": "user",
                        "content": f"第{round_data.round_number}轮观察摘要：{summary}",
                    }
                )

            if not is_current_round and round_data.model_response_raw:
                messages.append(
                    {
                        "role": "assistant",
                        "content": round_data.model_response_raw,
                    }
                )

        return messages

    def _cleanup_expired(self) -> None:
        now = time.time()
        expired = [
            sid
            for sid, s in self._sessions.items()
            if (now - s.created_at) > SESSION_EXPIRY
        ]
        for sid in expired:
            del self._sessions[sid]
        if expired:
            logger.info("Cleaned up %d expired session(s)", len(expired))


class SessionStateError(Exception):
    pass
