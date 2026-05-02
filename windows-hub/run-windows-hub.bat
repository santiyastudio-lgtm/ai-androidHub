@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%SantiyaLocalAiHub-WindowsHub.jar"
if not exist "%JAR%" set "JAR=%ROOT%dist\SantiyaLocalAiHub-WindowsHub.jar"
if not exist "%JAR%" (
  echo SantiyaLocalAiHub-WindowsHub.jar not found. Run build-windows-hub.bat first.
  exit /b 1
)

set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :try_java "%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD (
  for /f "delims=" %%I in ('where.exe javac 2^>nul') do (
    if not defined JAVA_CMD if exist "%%~dpIjava.exe" call :try_java "%%~dpIjava.exe"
  )
)
if not defined JAVA_CMD (
  for /f "delims=" %%I in ('where.exe java 2^>nul') do (
    if not defined JAVA_CMD call :try_java "%%~fI"
  )
)
if not defined JAVA_CMD (
  echo A working Java 17 or newer runtime was not found.
  echo Install a JDK or JRE, set JAVA_HOME if needed, then run this launcher again.
  exit /b 1
)

set "DATA_ROOT=%ROOT%data"
set "HAS_BUNDLE_DATA="
if exist "%DATA_ROOT%\" (
  set "HAS_BUNDLE_DATA=1"
  if not exist "%DATA_ROOT%\models" mkdir "%DATA_ROOT%\models" >nul 2>nul
  if not exist "%DATA_ROOT%\peers.csv" (
    >"%DATA_ROOT%\peers.csv" echo # name,host,port,freeRamMb,totalRamMb,cpuCores,computeScore,acceleratorSummary
    >>"%DATA_ROOT%\peers.csv" echo # Peer-1,192.168.0.21,17860,6144,8192,8,10.5,RTX-or-NPU-summary
  )
)

set "SUPPRESS_BANNER="
for %%A in (%*) do (
  if /i "%%~A"=="--help" set "SUPPRESS_BANNER=1"
  if /i "%%~A"=="-h" set "SUPPRESS_BANNER=1"
  if /i "%%~A"=="/?" set "SUPPRESS_BANNER=1"
)
if not defined SUPPRESS_BANNER (
  echo Starting SantiyaLocalAiHub Windows Hub...
  echo Java runtime: "%JAVA_CMD%"
  if defined HAS_BUNDLE_DATA (
    echo Models directory: "%DATA_ROOT%\models"
    echo Peers file: "%DATA_ROOT%\peers.csv"
  )
  echo Press Ctrl+C to stop the service.
)

if "%~1"=="" (
  if defined HAS_BUNDLE_DATA (
    "%JAVA_CMD%" -jar "%JAR%" --headless "--models-dir=%DATA_ROOT%\models" "--peers-file=%DATA_ROOT%\peers.csv"
  ) else (
    "%JAVA_CMD%" -jar "%JAR%" --headless
  )
) else (
  if defined HAS_BUNDLE_DATA (
    "%JAVA_CMD%" -jar "%JAR%" "--models-dir=%DATA_ROOT%\models" "--peers-file=%DATA_ROOT%\peers.csv" %*
  ) else (
    "%JAVA_CMD%" -jar "%JAR%" %*
  )
)

exit /b %errorlevel%

:try_java
"%~1" -jar "%JAR%" --help >nul 2>nul
if not errorlevel 1 set "JAVA_CMD=%~1"
exit /b 0
