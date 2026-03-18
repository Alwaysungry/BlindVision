import os
from dotenv import load_dotenv

load_dotenv()

ZHIPU_API_KEY: str = os.getenv("ZHIPU_API_KEY", "")
ZHIPU_API_URL: str = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
ZHIPU_MODEL: str = "glm-4v-plus"

# Request constraints
MAX_FRAMES: int = 10
MIN_FRAMES: int = 1

# API timeout in seconds
API_TIMEOUT: int = 60

# Exploration constraints
MAX_EXPLORE_ROUNDS: int = 5

# Session expiry in seconds (30 minutes)
SESSION_EXPIRY: int = 1800

# Max conversation history rounds to keep images for (older rounds become text-only summaries)
MAX_IMAGE_HISTORY_ROUNDS: int = 3

# Mock mode for local testing without API key
MOCK_MODE: bool = os.getenv("MOCK_MODE", "false").lower() == "true"
