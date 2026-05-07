# SantiyaLocalAiHub Desktop App

`desktop-app` is the new Android-first Windows client built with Compose Desktop.

Current scope:

- separate `desktop-app/` Gradle module;
- Android-style primary flow:
  - `Guide`
  - `Terms`
  - `Setup`
  - `Home`
  - `Store`
  - `Live AI`
  - `Files`
  - `Settings`
- utility windows:
  - `Node Farm`
  - `Sessions`
  - `Logs`
  - `Developer/API`
  - `Runtime Monitor`
- backend bridge to:
  - `windows-core` first
  - `windows-hub` as compatibility fallback

Important behavior:

- if no backend is reachable, the shell stays honest and shows setup/disconnected states;
- `Setup` uses `GET /api/setup/recommendation` when available;
- model, LAN, orchestra and tool-calling states are visible in the primary shell, not hidden in a legacy admin dashboard.
- the desktop shell uses Compose resources for `Manrope` / `Maple Mono` and carries its own branded Windows icon;
- on startup it first tries to connect to `windows-core`, then `windows-hub`, and in repo/bundle scenarios it also attempts a best-effort local backend bootstrap from known launcher paths.

Build:

1. Run `build-desktop-app.bat`
2. If installer packaging is blocked by system prerequisites, use the app image produced by `:desktop-app:createDistributable`

Run in development:

1. Run `run-desktop-app.bat`
2. The script tries to start `windows-core` first, then falls back to the native `windows-hub` EXE when available
3. The script launches the packaged desktop EXE/app image instead of `gradlew :desktop-app:run`, so normal startup does not depend on a local JVM dev setup

Current packaged outputs:

- `desktop-app/build/compose/binaries/main/app/SantiyaLocalAiHub/SantiyaLocalAiHub.exe`
- `desktop-app/build/compose/binaries/main-release/exe/SantiyaLocalAiHub-1.0.0.exe`
- `desktop-app/build/compose/binaries/main-release/msi/SantiyaLocalAiHub-1.0.0.msi`
