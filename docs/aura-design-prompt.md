# Aura Prompt for SantiyaLocalAiHub

Create a premium Android mobile app design for **SantiyaLocalAiHub**, a local AI model hub for Android.

The app lets users download, run, and manage open-source AI models directly on-device, then connect those local models from other Android apps through AIDL SDK, Intent API, and localhost HTTP API.

Design direction:
- Dark, technical, high-contrast interface.
- Animated glowing pill buttons inspired by Aura gradient CTA components.
- Glass-like cards with subtle borders, compact spacing, and visible runtime metrics.
- No marketing landing page. First screen must be the actual app dashboard.
- Use a polished AI operations feel: model marketplace, runtime state, downloads, device capability, API integration.

Core screens:
- Dashboard: active model, memory usage, running backend, quick actions.
- Model Store: task filters for chat, object detection, segmentation, image generation, TTS, RAG, video, 3D.
- Downloads: progress, pause/resume, checksum, storage use.
- Runtime Monitor: loaded model, tokens/sec, image generation steps, ONNX latency, thermal warning.
- Developer/API: AIDL SDK, Intent API, Local HTTP API, token state, app allowlist, copyable snippets.
- Model Manifest Import: JSON preview, compatibility warnings, license, required RAM.

Interaction details:
- Primary action button: animated dark gradient pill with rose/red moving glow.
- Secondary action: glass pill with thin bottom light beam and icon.
- Cards should be 8dp radius or less, dense, readable, and optimized for repeated use.
- Experimental video/3D capabilities must be visually marked as experimental, not as fully ready.

Accessibility:
- Text must fit on small Android screens.
- Status and errors must be explicit: unsupported device, not enough RAM, missing backend, gated model, token required.
- Avoid decorative clutter that hides model state or API instructions.
