@echo off
setlocal
set "ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\Invoke-Preflight.ps1" -OutputPath "build\preflight.json" %*
exit /b %errorlevel%
