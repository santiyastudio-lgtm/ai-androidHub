@echo off
setlocal
set "ROOT=%~dp0.."
set "JAVA17=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
if exist "%JAVA17%\bin\java.exe" (
  set "JAVA_HOME=%JAVA17%"
  set "ORG_GRADLE_JAVA_HOME=%JAVA17%"
  set "PATH=%JAVA17%\bin;%PATH%"
)
set "JAVA_TOOL_OPTIONS="
set "JDK_JAVA_OPTIONS="
pushd "%ROOT%"
call gradlew.bat :desktop-app:compileKotlin :desktop-app:createDistributable
if errorlevel 1 (
  popd
  exit /b 1
)
call gradlew.bat :desktop-app:packageReleaseDistributionForCurrentOS
if errorlevel 1 (
  echo.
  echo Native installer packaging did not finish. The app image from :desktop-app:createDistributable is still available.
  echo Check whether WiX or current-OS packaging prerequisites are missing.
  popd
  exit /b 0
)
popd
echo Desktop app build complete.
exit /b 0
