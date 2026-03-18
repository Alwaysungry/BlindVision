# blind-vision-core

Python FastAPI backend for BlindVision assistive system.

## STRUCTURE
```
blind-vision-core/
├── main.py            # FastAPI entry, 303 lines
├── schemas.py         # Pydantic models, 218 lines
├── config.py          # Settings, 27 lines
├── services/
│   ├── vision_service.py   # Core AI vision (425 lines)
│   ├── session_manager.py  # Session handling (232 lines)
│   └── intent_parser.py    # Intent parsing (122 lines)
└── utils/
    └── frame_loader.py     # Image loading (40 lines)
```

## WHERE TO LOOK
| Task | File |
|------|------|
| API routes | main.py (FastAPI routers) |
| Vision pipeline | services/vision_service.py |
| User sessions | services/session_manager.py |
| Data models | schemas.py |

## CONVENTIONS
- Pydantic v2 for all data validation
- Async/await for I/O operations
- No type ignores - strict typing
- Single .venv for virtualenv

## ANTI-PATTERNS
- No type: `as any`, `@ts-ignore`
- No empty catch blocks
- No tests - verify manually

## NOTES
- Requires .env or .env.example for API keys
- Dependencies: fastapi, pydantic, python-multipart
