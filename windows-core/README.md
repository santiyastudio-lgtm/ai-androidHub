# SantiyaLocalAiHub Windows Core

`windows-core` is the new native-first Windows runtime module for functional parity work.

Current state in this repository:

- implemented as a standalone Rust HTTP runtime source tree;
- mirrors the Android-side contract surface for:
  - runtime registry
  - catalog
  - plugins/tools
  - preferred models
  - external access policy
  - orchestra config
  - LAN nodes
  - RAG install/query
  - TTS/image/chat runtime entrypoints
- exports both a binary target and a DLL target in Cargo metadata;
- uses only the Rust standard library so the source graph can be checked without downloading crates.

Implemented endpoints in the source:

- `GET /health`
- `GET /api/status`
- `GET /api/runtimes`
- `GET /api/models`
- `GET /api/catalog`
- `GET /api/plugins`
- `GET /api/tools`
- `GET /api/tool-state`
- `PUT /api/tool-state`
- `GET /api/preferred-models`
- `PUT /api/preferred-models`
- `GET /api/external-access`
- `PUT /api/external-access`
- `GET /api/orchestra`
- `PUT /api/orchestra`
- `GET /api/lan/nodes`
- `GET /api/plan`
- `GET /api/reload`
- `POST /api/lan/execute`
- `POST /api/chat/generate`
- `POST /api/rag/install`
- `POST /api/rag/query`
- `POST /api/tts/speak`
- `POST /api/image/generate`
- `POST /api/tools/execute`

Practical build status on this machine:

- `cargo check --release`: passes
- `cargo build --release`: reaches final native link step, then fails because the machine does not provide the MSVC CRT import libraries required by `rust-lld`

Use:

1. Run `build-windows-core.bat`
2. If native linking succeeds, artifacts are copied to `dist\`
3. If native linking fails, the script still runs `cargo check --release` and prints the exact toolchain blocker

Preparation helpers:

- `preflight-windows-core.bat`
  Generates `build\preflight.json` and reports whether native link prerequisites are present.
- `vendor-upstreams.bat`
  Populates `third_party\` from the pinned commits in `VENDOR_LOCK.json` when network and git access are available.

Upstream runtime intent:

- `ggml-org/llama.cpp` for GGUF chat and embeddings
- `microsoft/onnxruntime` and `microsoft/onnxruntime-extensions` for ONNX runtime features
- `leejet/stable-diffusion.cpp` for image generation
- `rhasspy/piper` for TTS
- local `neuron-packet` sources for `.neuron` compatibility work
