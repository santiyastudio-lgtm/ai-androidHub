# Windows Hub

`windows-hub` is the first non-Android runnable target in this repository.

It is intentionally a control-plane build with a user-ready Windows bundle:
- Windows local HTTP hub
- GGUF directory scan
- heuristic sequential offload planning
- heuristic LAN partition planning
- manual peer registry
- portable `dist\windows-hub-bundle\` packaging for Windows users

Current scope is honest:
- no fake desktop inference runtime
- no fake libp2p execution path
- no fake native GGUF worker

The bundle is meant for a practical first run:
- put `.gguf` files into `data\models\`
- keep peer definitions in `data\peers.csv`
- start `run-windows-hub.bat`
- inspect the local artifact at `http://127.0.0.1:17860/api/status`

Build output now includes:
- `dist\SantiyaLocalAiHub-WindowsHub.jar`
- `dist\windows-hub-bundle\`
- `dist\SantiyaLocalAiHub-WindowsHub-bundle.zip`

This keeps the repository honest about current runtime limits while giving users a cleaner Windows artifact to launch, inspect, and hand off.
