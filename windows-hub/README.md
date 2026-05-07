# SantiyaLocalAiHub Windows Hub

This module is the first real Windows target for `SantiyaLocalAiHub`.

It currently provides:
- local `.gguf` model discovery;
- a local HTTP hub on Windows;
- heuristic GGUF profiling;
- sequential offload planning;
- LAN partition planning from a manual peer registry;
- Android node discovery on the local network;
- authenticated prompt forwarding from Windows to Android phone nodes.

It still does not provide:
- native GGUF inference on Windows;
- remote layer execution across peers;
- libp2p transport or a native worker runtime.

## Quick Start For Windows Users

1. Run `build-windows-hub.bat`.
2. Open `dist\native\SantiyaLocalAiHub Windows Hub\`.
3. Start `SantiyaLocalAiHub Windows Hub.exe`.
4. On the Android phone, enable LAN mode in the app settings.
5. Use the same pairing token on both sides.
6. Copy one or more `.gguf` files into `data\models\`.
7. Open `http://127.0.0.1:17860/` from the app with `Open Dashboard`, or open it manually in a browser.

The desktop EXE is now the main interactive path. The batch launchers remain useful when you want a headless service or a scriptable LAN setup.

## Build Output

`build-windows-hub.bat` produces:

- `dist\SantiyaLocalAiHub-WindowsHub.jar`
- `dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe`
- `dist\run-windows-hub-lan.bat`
- `dist\run-windows-hub.bat`
- `dist\windows-hub-bundle\`
- `dist\SantiyaLocalAiHub-WindowsHub-bundle.zip`

The bundle folder and zip contain:

- `SantiyaLocalAiHub-WindowsHub.jar`
- `run-windows-hub-lan.bat`
- `run-windows-hub.bat`
- `open-windows-hub-status.bat`
- `README.md`
- `data\models\PUT-GGUF-MODELS-HERE.txt`
- `data\peers.csv`

The native app image contains:

- `SantiyaLocalAiHub Windows Hub.exe`
- `app\`
- `runtime\`
- `data\models\PUT-GGUF-MODELS-HERE.txt`
- `data\peers.csv`
- `README.md`

## Native EXE Behavior

`SantiyaLocalAiHub Windows Hub.exe` now:

- launches a native Swing desktop window instead of only a console launcher;
- stores its settings in `%LOCALAPPDATA%\SantiyaLocalAiHub\windows-hub\desktop.properties`;
- auto-generates a pairing token if none is configured yet;
- starts the local hub from the desktop UI;
- opens the dashboard in a browser with one click;
- keeps the same LAN relay and planning endpoints as the headless path.

## Launcher Behavior

`run-windows-hub.bat` now:

- selects a working Java runtime before launch and fails with a clear message otherwise;
- defaults to `--headless` when no flags are provided;
- uses bundle-local `data\models` and `data\peers.csv` when those paths exist;
- forwards `HUB_PAIRING_TOKEN` into `--pairing-token=...` when the variable is set;
- forwards `ANDROID_NODE_PORT` into `--android-node-port=...` when the variable is set;
- forwards `WINDOWS_CORE_URL` into `--core-url=...` when the variable is set;
- creates the local `models` folder if needed;
- prints the active model and peer paths in the console.

`run-windows-hub-lan.bat` adds a simpler LAN-first path:

- prompts for `HUB_PAIRING_TOKEN` if it is not already defined;
- defaults `ANDROID_NODE_PORT` to `17888`;
- forwards into `run-windows-hub.bat` after printing the dashboard URL.

`run-windows-stack.bat` is the prepared migration path for the combined stack:

- starts `..\windows-core\dist\santiya-localai-core.exe` when it exists;
- sets `WINDOWS_CORE_URL=http://127.0.0.1:17861` by default;
- forwards the current command line into `run-windows-hub.bat`.

You can still override the defaults:

- `--port=17860`
- `--models-dir=C:\path\to\models`
- `--peers-file=C:\path\to\peers.csv`
- `--pairing-token=shared-lan-token`
- `--android-node-port=17888`
- `--core-url=http://127.0.0.1:17861`
- `--help`

## Endpoints

- `GET /`
- `GET /health`
- `GET /api/status`
- `GET /api/models`
- `GET /api/nodes`
- `GET /api/reload`
- `GET /api/plan?modelId=<id>`
- `POST /api/lan/execute`

## Pairing Token

The Android node API requires a shared token.

On Windows you can either:

- pass `--pairing-token=shared-lan-token` manually; or
- set `HUB_PAIRING_TOKEN=shared-lan-token` before launching `run-windows-hub.bat`.

The dashboard will still open without a token, but LAN execution requests will be rejected until the token is configured.

## Native Core Compatibility

When `windows-core` is running, `windows-hub` can proxy the expanded API surface through:

- `--core-url=http://127.0.0.1:17861`
- environment variable `WINDOWS_CORE_URL=http://127.0.0.1:17861`

In this mode the Java hub keeps the existing dashboard and EXE shell, while `/api/status`, `/api/models`, `/api/nodes`, `/api/runtimes`, `/api/catalog`, `/api/plugins`, `/api/tools`, `/api/tool-state`, `/api/preferred-models`, `/api/external-access`, `/api/orchestra`, `/api/chat/generate`, `/api/rag/install`, `/api/rag/query`, `/api/tts/speak`, and `/api/image/generate` are proxied into the native core.

## Dashboard

The root page at `http://127.0.0.1:17860/` now provides:

- live hub status;
- discovered Android nodes on the local network;
- installed model overview;
- a form to forward prompts into the Android runtime over LAN.

## Peer Registry

`peers.csv` format:

```text
# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary
Peer-1,192.168.0.21,17860,6144,8192,8,10.5,RTX-or-NPU-summary
```
