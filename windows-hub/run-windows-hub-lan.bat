@echo off
setlocal
set "ROOT=%~dp0"

for %%A in (%*) do (
  if /I "%%~A"=="--help" goto launch
  if /I "%%~A"=="-h" goto launch
  if /I "%%~A"=="/?" goto launch
)

if not defined HUB_PAIRING_TOKEN (
  echo Enter the shared LAN token configured on the Android node.
  set /p HUB_PAIRING_TOKEN=HUB_PAIRING_TOKEN: 
)

if "%HUB_PAIRING_TOKEN%"=="" (
  echo HUB_PAIRING_TOKEN is required for Android LAN relay.
  exit /b 1
)

if not defined ANDROID_NODE_PORT set "ANDROID_NODE_PORT=17888"

echo Starting Windows Hub in LAN mode...
echo Pairing token: configured
echo Android node port: %ANDROID_NODE_PORT%
echo Dashboard: http://127.0.0.1:17860/

:launch
call "%ROOT%run-windows-hub.bat" %*
exit /b %errorlevel%
