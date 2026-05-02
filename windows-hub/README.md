# SantiyaLocalAiHub Windows Hub

This module is the first real Windows target for `SantiyaLocalAiHub`.

It currently provides:
- local `.gguf` model discovery;
- a local HTTP hub on Windows;
- heuristic GGUF profiling;
- sequential offload planning;
- LAN partition planning from a manual peer registry.

It still does not provide:
- native GGUF inference on Windows;
- remote layer execution across peers;
- libp2p transport or a native worker runtime.

## Quick Start For Windows Users

1. Run `build-windows-hub.bat`.
2. Open `dist\windows-hub-bundle\`.
3. Copy one or more `.gguf` files into `data\models\`.
4. Double-click `run-windows-hub.bat`.
5. Open `http://127.0.0.1:17860/api/status` or double-click `open-windows-hub-status.bat`.

The launcher defaults to `--headless` when you start it without arguments, so double-clicking the batch file starts the service immediately.

## Build Output

`build-windows-hub.bat` produces:

- `dist\SantiyaLocalAiHub-WindowsHub.jar`
- `dist\run-windows-hub.bat`
- `dist\windows-hub-bundle\`
- `dist\SantiyaLocalAiHub-WindowsHub-bundle.zip`

The bundle folder and zip contain:

- `SantiyaLocalAiHub-WindowsHub.jar`
- `run-windows-hub.bat`
- `open-windows-hub-status.bat`
- `README.md`
- `data\models\PUT-GGUF-MODELS-HERE.txt`
- `data\peers.csv`

## Launcher Behavior

`run-windows-hub.bat` now:

- selects a working Java runtime before launch and fails with a clear message otherwise;
- defaults to `--headless` when no flags are provided;
- uses bundle-local `data\models` and `data\peers.csv` when those paths exist;
- creates the local `models` folder if needed;
- prints the active model and peer paths in the console.

You can still override the defaults:

- `--port=17860`
- `--models-dir=C:\path\to\models`
- `--peers-file=C:\path\to\peers.csv`
- `--help`

## Endpoints

- `GET /health`
- `GET /api/status`
- `GET /api/models`
- `GET /api/nodes`
- `GET /api/reload`
- `GET /api/plan?modelId=<id>`

## Peer Registry

`peers.csv` format:

```text
# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary
Peer-1,192.168.0.21,17860,6144,8192,8,10.5,RTX-or-NPU-summary
```
