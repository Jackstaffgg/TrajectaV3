@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "GRADLEW=%PROJECT_DIR%\gradlew.bat"
set "COMPOSE_FILE=%PROJECT_DIR%\compose.yaml"

if not exist "%GRADLEW%" (
  echo [ERROR] Gradle wrapper not found: "%GRADLEW%"
  exit /b 1
)

if not exist "%COMPOSE_FILE%" (
  echo [ERROR] Compose file not found: "%COMPOSE_FILE%"
  exit /b 1
)

pushd "%PROJECT_DIR%"
if errorlevel 1 (
  echo [ERROR] Unable to switch to project directory: "%PROJECT_DIR%"
  exit /b 1
)

set "RUN_BUILD=1"
set "RUN_TESTS=1"
set "RUN_COMPOSE=1"
set "RUN_LOGS=0"

:parse_args
if "%~1"=="" goto run

if /I "%~1"=="skiptests" (
  set "RUN_TESTS=0"
  shift
  goto parse_args
)

if /I "%~1"=="skipbuild" (
  set "RUN_BUILD=0"
  shift
  goto parse_args
)

if /I "%~1"=="skipcompose" (
  set "RUN_COMPOSE=0"
  shift
  goto parse_args
)

if /I "%~1"=="autologs" (
  set "RUN_LOGS=1"
  shift
  goto parse_args
)

if /I "%~1"=="logs" (
  set "RUN_LOGS=1"
  shift
  goto parse_args
)

if /I "%~1"=="help" goto usage
if /I "%~1"=="/h" goto usage
if /I "%~1"=="-h" goto usage
if /I "%~1"=="--help" goto usage

echo [WARN] Unknown argument: %~1
shift
goto parse_args

:run
if "%RUN_BUILD%"=="1" (
  if "%RUN_TESTS%"=="1" (
    echo Running: "%GRADLEW%" clean build
    call "%GRADLEW%" clean build
  ) else (
    echo Running: "%GRADLEW%" clean build -x test
    call "%GRADLEW%" clean build -x test
  )
  if errorlevel 1 (
    echo [ERROR] Gradle build failed.
    popd
    exit /b 1
  )
) else (
  echo Build step skipped.
)

if "%RUN_COMPOSE%"=="1" (
  echo Running: docker compose -f "%COMPOSE_FILE%" up --build -d backend
  docker compose -f "%COMPOSE_FILE%" up --build -d backend
  if errorlevel 1 (
    echo [ERROR] Docker Compose failed.
    popd
    exit /b 1
  )
) else (
  echo Compose step skipped.
)

if "%RUN_LOGS%"=="1" (
  if "%RUN_COMPOSE%"=="0" (
    echo [WARN] Auto logs requested, but compose step was skipped. Showing logs for current stack.
  )
  echo Running: docker compose -f "%COMPOSE_FILE%" logs -f --tail=200
  docker compose -f "%COMPOSE_FILE%" logs -f --tail=200
  if errorlevel 1 (
    echo [ERROR] Docker Compose logs command failed.
    popd
    exit /b 1
  )
)

echo Done.
popd
exit /b 0

:usage
echo Usage:
echo   start-all.bat [skiptests] [skipbuild] [skipcompose] [autologs]
echo.
echo Examples:
echo   start-all.bat
echo   start-all.bat skiptests
echo   start-all.bat skipcompose
echo   start-all.bat autologs
echo   start-all.bat skiptests autologs
popd
exit /b 0

