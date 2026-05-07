@echo off
setlocal
set "ROOT=%~dp0"
set "SRC=%ROOT%src"
set "ANDROID_RES=%ROOT%..\app\src\main\res"
set "BUILD=%ROOT%build"
set "CLASSES=%BUILD%\classes"
set "APP_INPUT=%BUILD%\jpackage-input"
set "DIST=%ROOT%dist"
set "BUNDLE=%DIST%\windows-hub-bundle"
set "BUNDLE_ZIP=%DIST%\SantiyaLocalAiHub-WindowsHub-bundle.zip"
set "NATIVE_DIR=%DIST%\native"
set "MAIN_CLASS=com.santiya.localaihub.windows.WindowsHubApp"

if not exist "%BUILD%" mkdir "%BUILD%"
if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
if not exist "%CLASSES%" mkdir "%CLASSES%"
if exist "%APP_INPUT%" rmdir /s /q "%APP_INPUT%"
if not exist "%APP_INPUT%" mkdir "%APP_INPUT%"
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

where jpackage >nul 2>nul
if errorlevel 1 (
  echo jpackage not found in PATH
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
copy /y "%DIST%\SantiyaLocalAiHub-WindowsHub.jar" "%APP_INPUT%\SantiyaLocalAiHub-WindowsHub.jar" >nul

copy /y "%ROOT%run-windows-hub.bat" "%DIST%\run-windows-hub.bat" >nul
copy /y "%ROOT%run-windows-hub-lan.bat" "%DIST%\run-windows-hub-lan.bat" >nul
copy /y "%ROOT%run-windows-stack.bat" "%DIST%\run-windows-stack.bat" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$bundle = '%BUNDLE%';" ^
  "$bundleTemplate = '%ROOT%bundle';" ^
  "$zip = '%BUNDLE_ZIP%';" ^
  "if (Test-Path $bundle) { Remove-Item -LiteralPath $bundle -Recurse -Force };" ^
  "New-Item -ItemType Directory -Path $bundle | Out-Null;" ^
  "Copy-Item -LiteralPath '%DIST%\SantiyaLocalAiHub-WindowsHub.jar' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%run-windows-hub.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%run-windows-hub-lan.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%run-windows-stack.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%open-windows-hub-status.bat' -Destination $bundle;" ^
  "Copy-Item -LiteralPath '%ROOT%README.md' -Destination (Join-Path $bundle 'README.md');" ^
  "if (Test-Path $bundleTemplate) { Copy-Item -Path (Join-Path $bundleTemplate '*') -Destination $bundle -Recurse -Force };" ^
  "$dataRoot = Join-Path $bundle 'data';" ^
  "$assetsRoot = Join-Path $bundle 'assets';" ^
  "$fontsDir = Join-Path $assetsRoot 'fonts';" ^
  "$modelsDir = Join-Path $dataRoot 'models';" ^
  "New-Item -ItemType Directory -Path $modelsDir -Force | Out-Null;" ^
  "New-Item -ItemType Directory -Path $fontsDir -Force | Out-Null;" ^
  "$placeholder = Join-Path $modelsDir 'PUT-GGUF-MODELS-HERE.txt';" ^
  "if (-not (Test-Path $placeholder)) { Set-Content -LiteralPath $placeholder -Value 'Place GGUF model files in this folder.' -Encoding Ascii };" ^
  "$peersFile = Join-Path $dataRoot 'peers.csv';" ^
  "if (-not (Test-Path $peersFile)) { Set-Content -LiteralPath $peersFile -Value '# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary' -Encoding Ascii };" ^
  "Copy-Item -LiteralPath '%ANDROID_RES%\font\manrope.ttf' -Destination (Join-Path $fontsDir 'manrope.ttf') -Force;" ^
  "Copy-Item -LiteralPath '%ANDROID_RES%\font\maple_mono.ttf' -Destination (Join-Path $fontsDir 'maple_mono.ttf') -Force;" ^
  "if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force };" ^
  "Compress-Archive -Path (Join-Path $bundle '*') -DestinationPath $zip"
if errorlevel 1 exit /b 1

if exist "%NATIVE_DIR%" powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "try { Remove-Item -LiteralPath '%NATIVE_DIR%' -Recurse -Force } catch { throw ('Cannot rebuild native EXE while it is running. Close SantiyaLocalAiHub Windows Hub.exe and retry. Original error: ' + $_.Exception.Message) }"
if errorlevel 1 exit /b 1
jpackage --type app-image --dest "%NATIVE_DIR%" --name "SantiyaLocalAiHub Windows Hub" --input "%APP_INPUT%" --main-jar "SantiyaLocalAiHub-WindowsHub.jar" --main-class "%MAIN_CLASS%" --vendor "Santiya" --app-version "1.0.0"
if errorlevel 1 exit /b 1

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$nativeRoot = Join-Path '%NATIVE_DIR%' 'SantiyaLocalAiHub Windows Hub';" ^
  "$dataRoot = Join-Path $nativeRoot 'data';" ^
  "$assetsRoot = Join-Path $nativeRoot 'assets';" ^
  "$fontsDir = Join-Path $assetsRoot 'fonts';" ^
  "$modelsDir = Join-Path $dataRoot 'models';" ^
  "New-Item -ItemType Directory -Path $modelsDir -Force | Out-Null;" ^
  "New-Item -ItemType Directory -Path $fontsDir -Force | Out-Null;" ^
  "$placeholder = Join-Path $modelsDir 'PUT-GGUF-MODELS-HERE.txt';" ^
  "if (-not (Test-Path $placeholder)) { Set-Content -LiteralPath $placeholder -Value 'Place GGUF model files in this folder.' -Encoding Ascii };" ^
  "$peersFile = Join-Path $dataRoot 'peers.csv';" ^
  "if (-not (Test-Path $peersFile)) { Set-Content -LiteralPath $peersFile -Value '# name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary' -Encoding Ascii };" ^
  "Copy-Item -LiteralPath '%ANDROID_RES%\font\manrope.ttf' -Destination (Join-Path $fontsDir 'manrope.ttf') -Force;" ^
  "Copy-Item -LiteralPath '%ANDROID_RES%\font\maple_mono.ttf' -Destination (Join-Path $fontsDir 'maple_mono.ttf') -Force;" ^
  "Copy-Item -LiteralPath '%ROOT%README.md' -Destination (Join-Path $nativeRoot 'README.md') -Force;" ^
  "Copy-Item -LiteralPath '%ROOT%run-windows-stack.bat' -Destination (Join-Path $nativeRoot 'run-windows-stack.bat') -Force"
if errorlevel 1 exit /b 1

echo Built %DIST%\SantiyaLocalAiHub-WindowsHub.jar
echo Prepared bundle %BUNDLE%
echo Packed bundle %BUNDLE_ZIP%
echo Built native EXE app image in %NATIVE_DIR%
exit /b 0
