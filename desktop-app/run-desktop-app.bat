@echo off
setlocal
set "SELF=%~dp0"
set "ROOT=%SELF%.."
set "JAVA17=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
if exist "%JAVA17%\bin\java.exe" (
  set "JAVA_HOME=%JAVA17%"
  set "ORG_GRADLE_JAVA_HOME=%JAVA17%"
  set "PATH=%JAVA17%\bin;%PATH%"
)
set "JAVA_TOOL_OPTIONS="
set "JDK_JAVA_OPTIONS="
set "APP_EXE=%SELF%build\compose\binaries\main\app\SantiyaLocalAiHub\SantiyaLocalAiHub.exe"
set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if exist "%POWERSHELL_EXE%" (
  "%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -Command "$coreReady = Test-NetConnection -ComputerName 127.0.0.1 -Port 17861 -InformationLevel Quiet -WarningAction SilentlyContinue; $hubReady = Test-NetConnection -ComputerName 127.0.0.1 -Port 17860 -InformationLevel Quiet -WarningAction SilentlyContinue; if (-not $coreReady -and -not $hubReady) { if (Test-Path '%ROOT%\windows-core\dist\santiya-localai-core.exe') { Start-Process -WindowStyle Hidden '%ROOT%\windows-core\dist\santiya-localai-core.exe' } elseif (Test-Path '%ROOT%\windows-hub\dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe') { Start-Process '%ROOT%\windows-hub\dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe' } }"
) else if exist "%ROOT%\windows-core\dist\santiya-localai-core.exe" (
  start "" /min "%ROOT%\windows-core\dist\santiya-localai-core.exe"
) else if exist "%ROOT%\windows-hub\dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe" (
  start "" "%ROOT%\windows-hub\dist\native\SantiyaLocalAiHub Windows Hub\SantiyaLocalAiHub Windows Hub.exe"
)

pushd "%SELF%"
call build-desktop-app.bat
set "BUILD_EXIT=%errorlevel%"
popd
if not "%BUILD_EXIT%"=="0" exit /b %BUILD_EXIT%

if exist "%APP_EXE%" (
  "%APP_EXE%"
  exit /b %errorlevel%
)

echo Failed to prepare packaged desktop app.
exit /b 1
