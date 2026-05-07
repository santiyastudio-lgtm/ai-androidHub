# Windows Hub

`windows-hub` is the first non-Android runnable target in this repository.

It is a Windows-side control plane for local orchestration across a desktop and Android phones on the same LAN.

## What It Does Now

- scans a local GGUF models directory on Windows;
- profiles models heuristically for planning;
- exposes a local HTTP hub on Windows;
- keeps support for manual peer registry entries;
- scans the local network for Android nodes exposing the LAN node API;
- serves a browser dashboard at `http://127.0.0.1:17860/`;
- forwards authenticated prompts from Windows to the Android runtime.

## Honest Current Scope

- Windows still does not run native GGUF inference itself;
- distributed layer execution is still planning-oriented, not a finished pipeline runtime;
- libp2p transport is still planned, not active;
- the current live execution path is Windows -> Android HTTP over LAN with a shared pairing token.

## Practical First Run

1. Build the module with `windows-hub\build-windows-hub.bat`.
2. Open `dist\native\SantiyaLocalAiHub Windows Hub\`.
3. Start `SantiyaLocalAiHub Windows Hub.exe`.
4. On Android, enable LAN mode and make sure the app stays reachable on the same local network.
5. Configure the same pairing token on both sides.
6. Put one or more `.gguf` files into `data\models\`.
7. Open `http://127.0.0.1:17860/`.

The native EXE is now the main interactive Windows path. The batch launchers remain available when you specifically want headless startup.

Prepared launcher paths:

- `run-windows-hub.bat`: Java hub only
- `run-windows-hub-lan.bat`: LAN-first Java hub path
- `run-windows-stack.bat`: starts `windows-core` first when its EXE exists, then launches the hub with `WINDOWS_CORE_URL`

## Pairing Token

The Android LAN node requires authentication.

You can pass the token to Windows Hub in either form:

- `--pairing-token=shared-lan-token`
- environment variable `HUB_PAIRING_TOKEN=shared-lan-token`
- `--core-url=http://127.0.0.1:17861`
- environment variable `WINDOWS_CORE_URL=http://127.0.0.1:17861`

Optional port override:

- `--android-node-port=17888`
- environment variable `ANDROID_NODE_PORT=17888`

## Main Endpoints

- `GET /`
- `GET /health`
- `GET /api/status`
- `GET /api/models`
- `GET /api/nodes`
- `GET /api/reload`
- `GET /api/plan?modelId=<id>`
- `POST /api/lan/execute`

When `WINDOWS_CORE_URL` is configured, the Java hub also proxies the expanded native-core endpoints for runtimes, catalog, plugins, tools, preferred models, external access, orchestra, chat, RAG, TTS, and image generation.

## Bundle Output

Build output includes:

- `dist\SantiyaLocalAiHub-WindowsHub.jar`
- `dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe`
- `dist\windows-hub-bundle\`
- `dist\SantiyaLocalAiHub-WindowsHub-bundle.zip`

This keeps the repository honest: the Windows target is already useful as a real LAN control plane, while unfinished distributed runtime pieces remain explicitly marked as unfinished.
