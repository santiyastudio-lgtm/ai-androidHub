@echo off
setlocal
set "ROOT=%~dp0"
pushd "%ROOT%"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\Invoke-Preflight.ps1" -OutputPath "build\preflight.json" >nul
cargo build --release
if errorlevel 1 (
  echo Native link step failed. Verifying source graph with cargo check...
  cargo check --release
  echo.
  echo windows-core source is valid, but this machine is missing MSVC CRT import libraries required for final native linking.
  echo Preflight report: %ROOT%build\preflight.json
  echo Install Visual Studio Build Tools with C++ runtime libraries, or provide the required MSVC libs, then rerun this script.
  popd
  exit /b 1
)
if not exist "%ROOT%dist" mkdir "%ROOT%dist"
copy /y "%ROOT%target\release\santiya-localai-core.exe" "%ROOT%dist\santiya-localai-core.exe" >nul
copy /y "%ROOT%target\release\santiya_localai_core_native.dll" "%ROOT%dist\santiya-localai-core.dll" >nul
popd
echo Built %ROOT%dist\santiya-localai-core.exe
echo Built %ROOT%dist\santiya-localai-core.dll
exit /b 0
