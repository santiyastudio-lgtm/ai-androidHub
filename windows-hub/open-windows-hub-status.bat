@echo off
setlocal
set "PORT=17860"
if not "%~1"=="" set "PORT=%~1"
start "" "http://127.0.0.1:%PORT%/api/status"
