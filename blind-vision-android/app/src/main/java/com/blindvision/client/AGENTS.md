# blind-vision-android

Kotlin Android client for BlindVision assistive system.

## STRUCTURE
```
client/
├── camera/
│   └── CameraController.kt     # Camera capture (201 lines)
├── data/
│   ├── model/                   # Data models
│   └── remote/                  # API clients
├── domain/
│   └── DecisionScheduler.kt     # Core decision engine (374 lines)
├── safety/
│   └── SafetyManager.kt         # Safety checks (107 lines)
├── sensor/
│   └── MotionMonitor.kt         # Motion detection (137 lines)
├── ui/
│   └── MainActivity.kt          # UI entry (207 lines)
└── voice/
    ├── IntentParser.kt          # Voice intent parsing (41 lines)
    ├── TTSManager.kt            # Text-to-speech (84 lines)
    └── VoiceController.kt       # Voice I/O orchestration (125 lines)
```

## WHERE TO LOOK
| Task | File |
|------|------|
| Core logic | domain/DecisionScheduler.kt |
| Camera | camera/CameraController.kt |
| Voice I/O | voice/VoiceController.kt |
| Main UI | ui/MainActivity.kt |

## CONVENTIONS
- MVVM-style with domain/data separation
- Kotlin coroutines for async
- Single Activity architecture (MainActivity)
- Data classes for models

## ANTI-PATTERNS
- No tests - verify manually
- Hardcoded API endpoints in code

## NOTES
- Gradle-based build (gradlew)
- Requires Android SDK
- Output: app/build/outputs/apk/debug/
