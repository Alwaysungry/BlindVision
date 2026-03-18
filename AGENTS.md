# PROJECT KNOWLEDGE BASE

**Generated:** 2026-03-18
**Commit:** c61b048
**Branch:** main

## OVERVIEW
BlindVision - assistive technology system for visually impaired. Python FastAPI backend + Kotlin Android client.

## STRUCTURE
```
./
├── blind-vision-core/     # Python FastAPI backend
│   ├── main.py           # Entry point (303 lines)
│   ├── schemas.py        # Pydantic models (218 lines)
│   ├── config.py         # Configuration (27 lines)
│   ├── services/         # Business logic
│   └── utils/            # Utilities
└── blind-vision-android/ # Kotlin Android app
    └── app/src/main/java/com/blindvision/client/
        ├── camera/       # Camera capture
        ├── data/         # Data layer (model, remote)
        ├── domain/       # DecisionScheduler (374 lines)
        ├── safety/       # SafetyManager
        ├── sensor/       # MotionMonitor
        ├── ui/           # MainActivity
        └── voice/        # Voice I/O (TTS, STT)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| API endpoints | blind-vision-core/main.py |
| Vision processing | blind-vision-core/services/vision_service.py |
| Session management | blind-vision-core/services/session_manager.py |
| Android core logic | blind-vision-android/app/src/main/java/com/blindvision/client/domain/ |
| Voice I/O | blind-vision-android/app/src/main/java/com/blindvision/client/voice/ |

## CODE MAP
| Component | Type | Lines | Role |
|-----------|------|-------|------|
| vision_service.py | service | 425 | Core vision processing |
| DecisionScheduler.kt | class | 374 | Android decision engine |
| session_manager.py | service | 232 | User session handling |
| MainActivity.kt | class | 207 | Android UI entry |
| CameraController.kt | class | 201 | Camera capture |
| main.py | entry | 303 | FastAPI app entry |

## CONVENTIONS
- Python: Pydantic v2 for schemas (blind-vision-core/schemas.py)
- Kotlin: MVVM-style with domain/data separation
- No ruff/ESLint config found - default linter behavior

## ANTI-PATTERNS (THIS PROJECT)
- No tests anywhere - treat with care
- No CI/CD workflows configured

## COMMANDS
```bash
# Python backend
cd blind-vision-core && source .venv/bin/activate && python main.py

# Android (requires Android SDK)
cd blind-vision-android && ./gradlew assembleDebug
```

## NOTES
- Two distinct runtimes: Python 3.x + Android (Kotlin)
- Android build outputs in app/build/ - safe to ignore
- .ruff_cache at root - ignore for linting
