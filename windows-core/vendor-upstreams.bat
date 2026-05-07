@echo off
setlocal
set "ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\Populate-VendorTree.ps1" %*
exit /b %errorlevel%
