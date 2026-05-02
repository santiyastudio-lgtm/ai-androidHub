@echo off
setlocal
set "ROOT=%~dp0"
set "SRC=%ROOT%src"
set "BUILD=%ROOT%build"
set "CLASSES=%BUILD%\classes"
set "DIST=%ROOT%dist"
set "BUNDLE=%DIST%\windows-hub-bundle"
set "BUNDLE_ZIP=%DIST%\SantiyaLocalAiHub-WindowsHub-bundle.zip"
set "MAIN_CLASS=com.santiya.localaihub.windows.WindowsHubApp"

if not exist "%BUILD%" mkdir "%BUILD%"
if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
if not exist "%CLASSES%" mkdir "%CLASSES%"
if not exist "%DIST%" mkdir "%DIST%"

where javac >nul 2>nul
if errorlevel 1 (
  echo javac not found in PATH
  exit /b 1
)

where jar >nul 2>nul
if errorlevel 1 (
  echo jar not found in PATH
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$sources = Get-ChildItem -Path '%SRC%' -Recurse -Filter *.java | Select-Object -ExpandProperty FullName;" ^
  "if (-not $sources) { throw 'No Java sources found.' };" ^
  "& javac --release 17 -encoding UTF-8 -d '%CLASSES%' $sources"
if errorlevel 1 exit /b 1

echo Main-Class: %MAIN_CLASS%>"%BUILD%\manifest.mf"
jar cfm "%DIST%\SantiyaLocalAiHub-WindowsHub.jar" "%BUILD%\manifest.mf" -C "%CLASSES%" .
if errorlevel 1 exit /b 1

copy /y "%ROOT%run-windows-hub.bat" "%DIST%\run-windows-hub.bat" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$bundle = '%BUNDLE%';" ^
  "$zip = '%BUNDLE_ZIP%';" ^
  "if (Test-Path $bundle) { Remove-Item -LiteralPath $bundle -Recurse -Force };" ^
  "New-Item -ItemType Directory -Path $bundle | Out-Null;" ^
  "Copy-Item -LiteralPath '%DIST%\SantiyaLocalAiHub-WindowsHub.jar' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%run-windows-hub.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%open-windows-hub-status.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%README.md' -Destination (Join-Path $bundle 'README.md');" ^
  "Copy-Item -Path '%ROOT%bundle\*' -Destination $bundle -Recurse -Force;" ^
  "if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force };" ^
  "Compress-Archive -Path (Join-Path $bundle '*') -DestinationPath $zip"
if errorlevel 1 exit /b 1

echo Built %DIST%\SantiyaLocalAiHub-WindowsHub.jar
echo Prepared bundle %BUNDLE%
echo Packed bundle %BUNDLE_ZIP%
exit /b 0
