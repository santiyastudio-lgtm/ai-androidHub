# Local HTTP API

The local HTTP API is intentionally disabled by default.

Planned endpoints:

```text
GET  /api/tags
POST /api/pull
POST /api/generate
POST /api/chat
POST /api/vision/detect
POST /api/images/generate
```

Security requirements:
- Bind to `127.0.0.1` by default.
- Require a generated token for every request.
- Show a foreground notification while the server is enabled.
- Let the user disable the server immediately from the developer screen.
- Never expose arbitrary filesystem paths or model files.

Current build:
- A `LocalHttpApiController` exists and reports disabled state.
- No listening socket is started yet.
- Use AIDL for real execution until the HTTP server is implemented and tested.
