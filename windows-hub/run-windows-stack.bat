@echo off
setlocal
set "HUB_ROOT=%~dp0"
set "CORE_ROOT=%HUB_ROOT%..\windows-core"
set "CORE_EXE=%CORE_ROOT%\dist\santiya-localai-core.exe"

if not defined WINDOWS_CORE_PORT set "WINDOWS_CORE_PORT=17861"

if defined WINDOWS_CORE_URL goto launch_hub

if exist "%CORE_EXE%" (
  set "WINDOWS_CORE_URL=http://127.0.0.1:%WINDOWS_CORE_PORT%"
  echo Starting windows-core on %WINDOWS_CORE_URL%
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$exe='%CORE_EXE%';" ^
    "$args=@('--port=%WINDOWS_CORE_PORT%');" ^
    "if($env:HUB_PAIRING_TOKEN){ $args += '--pairing-token=' + $env:HUB_PAIRING_TOKEN };" ^
    "if($env:WINDOWS_CORE_HOME){ $args += '--app-home=' + $env:WINDOWS_CORE_HOME };" ^
    "if($env:WINDOWS_CORE_MODELS_DIR){ $args += '--models-dir=' + $env:WINDOWS_CORE_MODELS_DIR };" ^
    "if($env:WINDOWS_CORE_PEERS_FILE){ $args += '--peers-file=' + $env:WINDOWS_CORE_PEERS_FILE };" ^
    "Start-Process -FilePath $exe -ArgumentList $args -WindowStyle Hidden"
  if errorlevel 1 exit /b 1
) else (
  echo windows-core EXE not found at "%CORE_EXE%".
  echo Build windows-core first if you want the full native-core + hub stack.
)

:launch_hub
call "%HUB_ROOT%run-windows-hub.bat" %*
exit /b %errorlevel%
