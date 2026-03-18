import base64
import logging
from typing import List

logger = logging.getLogger(__name__)


def validate_base64_image(data: str) -> bool:
    """Validate that a string is valid base64-encoded data."""
    try:
        # Strip potential data URI prefix
        raw = strip_data_uri(data)
        decoded = base64.b64decode(raw, validate=True)
        # Minimum size check: a valid image should be at least a few bytes
        if len(decoded) < 8:
            return False
        return True
    except Exception:
        return False


def strip_data_uri(data: str) -> str:
    """Remove data URI prefix if present (e.g., 'data:image/png;base64,')."""
    if "," in data and data.startswith("data:"):
        return data.split(",", 1)[1]
    return data


def validate_frames(frames: List[str]) -> List[str]:
    """
    Validate and clean a list of base64 image frames.
    Returns cleaned base64 strings (without data URI prefix).
    Raises ValueError if any frame is invalid.
    """
    cleaned: List[str] = []
    for i, frame in enumerate(frames):
        if not validate_base64_image(frame):
            raise ValueError(f"Frame at index {i} is not valid base64 image data")
        cleaned.append(strip_data_uri(frame))
    return cleaned
