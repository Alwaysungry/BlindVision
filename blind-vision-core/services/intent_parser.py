import re
from typing import Optional, Union

from schemas import CrossDirection, IntentParsed, IntentType


INTENT_PATTERNS = {
    "CROSS_ROAD": [
        r"过马路?",
        r"过街",
        r"能不能过",
        r"能过吗",
        r"可以过",
        r"想过",
        r"想过去",
        r"要过马路",
        r"去对面",
        r"到对面",
        r"直走过",
        r"横过",
        r"穿越",
    ],
    "PATH_CHECK": [
        r"能走吗",
        r"可以走吗",
        r"能过去吗",
        r"可以过去吗",
        r"能通行吗",
        r"可以通行吗",
        r"给走吗",
        r"能走不",
    ],
    "ENVIRONMENT_SCAN": [
        r"前面有什么",
        r"前方有什么",
        r"有什么",
        r"路况如何",
        r"什么情况",
        r"环境怎么样",
        r"什么路",
        r"这是哪",
    ],
    "TRAFFIC_LIGHT_CHECK": [
        r"红绿灯",
        r"红灯",
        r"绿灯",
        r"黄灯",
        r"灯是",
        r"什么灯",
        r"灯亮了",
        r"灯变了",
    ],
    "OBSTACLE_CHECK": [
        r"有障碍",
        r"有东西",
        r"堵住",
        r"挡路",
        r"能走吗.*有",
        r"能不能走.*有",
        r"障碍物",
    ],
    "STAIR_CHECK": [
        r"台阶",
        r"楼梯",
        r"坎",
        r"高",
        r"落差",
        r"有没有台阶",
        r"有台阶吗",
        r"楼梯吗",
    ],
}

DIRECTION_PATTERNS = {
    "forward": [r"直走", r"直行", r"往前走", r"向前", r"前进", r"一直走"],
    "backward": [r"往回走", r"后退", r"倒走"],
    "left": [r"向左", r"往左", r"左转", r"左边"],
    "right": [r"向右", r"往右", r"右转", r"右边"],
}


def parse_intent(query: str) -> IntentParsed:
    query = query.strip()
    query_lower = query.lower()

    matched_intent_str = "GENERAL_QUESTION"
    intent_confidence = 0.5

    for intent_name, patterns in INTENT_PATTERNS.items():
        for pattern in patterns:
            if re.search(pattern, query_lower):
                matched_intent_str = intent_name
                intent_confidence = 0.8
                break
        if matched_intent_str != "GENERAL_QUESTION":
            break

    matched_direction_str: Optional[str] = None
    direction_confidence = 0.0

    if matched_intent_str == "CROSS_ROAD":
        matched_direction_str = "forward"
        direction_confidence = 0.5

        for direction_name, patterns in DIRECTION_PATTERNS.items():
            for pattern in patterns:
                if re.search(pattern, query_lower):
                    matched_direction_str = direction_name
                    direction_confidence = 0.7
                    break
            if direction_confidence > 0:
                break

    final_confidence = intent_confidence
    if matched_direction_str:
        final_confidence = intent_confidence * 0.6 + direction_confidence * 0.4

    return IntentParsed(
        intent=matched_intent_str,  # type: ignore
        direction=matched_direction_str,  # type: ignore
        confidence=final_confidence,
    )
