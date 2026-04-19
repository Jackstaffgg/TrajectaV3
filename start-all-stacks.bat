@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%"
set "API_DIR=%ROOT_DIR%\Trajecta-api"
set "WORKER_DIR=%ROOT_DIR%\Trajecta-worker"
set "FRONTEND_DIR=%ROOT_DIR%\Trajecta-frontend"
set "NETWORK_NAME=telemetry-network"

set "GRADLEW=%API_DIR%\gradlew.bat"
set "API_COMPOSE_FILE=%API_DIR%\compose.yaml"
set "WORKER_COMPOSE_FILE=%WORKER_DIR%\docker-compose.yml"
set "FRONTEND_COMPOSE_FILE=%FRONTEND_DIR%\docker-compose.yml"

set "ACTION=up"
set "RUN_BUILD=1"
set "RUN_TESTS=1"
set "RUN_COMPOSE=1"
set "RUN_LOGS=0"
set "WORKER_REPLICAS=%WORKER_REPLICAS%"
if "%WORKER_REPLICAS%"=="" set "WORKER_REPLICAS=1"

if /I "%~1"=="up" (
  set "ACTION=up"
  shift
) else if /I "%~1"=="init" (
  set "ACTION=init"
  shift
) else if /I "%~1"=="validate" (
  set "ACTION=init"
  shift
) else if /I "%~1"=="build" (
  set "ACTION=build"
  shift
) else if /I "%~1"=="status" (
  set "ACTION=status"
  shift
) else if /I "%~1"=="logs" (
  set "ACTION=logs"
  shift
) else if /I "%~1"=="down" (
  set "ACTION=down"
  shift
) else if /I "%~1"=="help" (
  goto usage
) else if /I "%~1"=="/h" (
  goto usage
) else if /I "%~1"=="-h" (
  goto usage
) else if /I "%~1"=="--help" (
  goto usage
)

:parse_args
if "%~1"=="" goto dispatch

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

if /I "%~1"=="workers" (
  if "%~2"=="" (
    echo [ERROR] Missing value after "workers".
    exit /b 1
  )
  set "WORKER_REPLICAS=%~2"
  shift
  shift
  goto parse_args
)

if /I "%~1"=="tests" (
  set "RUN_TESTS=1"
  shift
  goto parse_args
)

if /I "%~1"=="build" (
  rem Compatibility token for legacy usage: "up build"
  set "RUN_BUILD=1"
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

:dispatch
if /I "%ACTION%"=="init" (
  call :init_local
  exit /b %errorlevel%
)

if /I "%ACTION%"=="build" (
  call :init_local || exit /b 1
  call :build_local
  exit /b %errorlevel%
)

if /I "%ACTION%"=="status" (
  call :show_status
  exit /b %errorlevel%
)

if /I "%ACTION%"=="logs" (
  call :show_logs
  exit /b %errorlevel%
)

if /I "%ACTION%"=="down" (
  call :compose_down
  exit /b %errorlevel%
)

if /I "%ACTION%"=="up" (
  call :init_local || exit /b 1
  call :build_local || exit /b 1
  call :compose_up || exit /b 1
  call :show_status || exit /b 1
  if "%RUN_LOGS%"=="1" call :show_logs
  exit /b %errorlevel%
)

echo [ERROR] Unsupported action: %ACTION%
goto usage

:require_file
if not exist "%~1" (
  echo [ERROR] Missing %~2: "%~1"
  exit /b 1
)
exit /b 0

:init_local
echo [INIT] Validating local prerequisites...

where docker >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Required command not found: docker
  exit /b 1
)

call :require_file "%GRADLEW%" "Gradle wrapper" || exit /b 1
call :require_file "%API_COMPOSE_FILE%" "API compose file" || exit /b 1
call :require_file "%WORKER_COMPOSE_FILE%" "Worker compose file" || exit /b 1
call :require_file "%FRONTEND_COMPOSE_FILE%" "Frontend compose file" || exit /b 1

docker compose version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] docker compose plugin is not available
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker daemon is not reachable
  exit /b 1
)

docker network inspect "%NETWORK_NAME%" >nul 2>&1
if errorlevel 1 (
  echo [INIT] Creating shared docker network: %NETWORK_NAME%
  docker network create "%NETWORK_NAME%" >nul
  if errorlevel 1 (
    echo [ERROR] Failed to create docker network: %NETWORK_NAME%
    exit /b 1
  )
) else (
  echo [INIT] Network already exists: %NETWORK_NAME%
)

echo [INIT] Validating compose files...
docker compose -f "%API_COMPOSE_FILE%" config >nul || exit /b 1
docker compose -f "%WORKER_COMPOSE_FILE%" config >nul || exit /b 1
docker compose -f "%FRONTEND_COMPOSE_FILE%" config >nul || exit /b 1

echo [INIT] Done.
exit /b 0

:build_local
if "%RUN_BUILD%"=="0" (
  echo [BUILD] Skipped.
  exit /b 0
)

echo [BUILD] API module
pushd "%API_DIR%" >nul || (
  echo [ERROR] Cannot enter API directory: "%API_DIR%"
  exit /b 1
)

if "%RUN_TESTS%"=="1" (
  call "%GRADLEW%" --no-daemon clean build
) else (
  call "%GRADLEW%" --no-daemon clean build -x test
)
set "GRADLE_EXIT=%errorlevel%"
popd >nul
if not "%GRADLE_EXIT%"=="0" (
  echo [ERROR] Gradle build failed.
  exit /b %GRADLE_EXIT%
)

echo [BUILD] Worker image
docker compose -f "%WORKER_COMPOSE_FILE%" build worker || exit /b 1

echo [BUILD] Frontend image
docker compose -f "%FRONTEND_COMPOSE_FILE%" build frontend || exit /b 1

echo [BUILD] Done.
exit /b 0

:compose_up
if "%RUN_COMPOSE%"=="0" (
  echo [UP] Compose start skipped.
  exit /b 0
)

echo [UP] Starting API stack
docker compose -f "%API_COMPOSE_FILE%" up --build --force-recreate --remove-orphans -d || exit /b 1

echo [UP] Starting worker stack (replicas=%WORKER_REPLICAS%)
docker compose -f "%WORKER_COMPOSE_FILE%" up --build --force-recreate --remove-orphans --scale worker=%WORKER_REPLICAS% -d || exit /b 1

echo [UP] Starting frontend stack
docker compose -f "%FRONTEND_COMPOSE_FILE%" up --build --force-recreate --remove-orphans -d || exit /b 1

echo [UP] All stacks are up.
exit /b 0

:show_status
echo [STATUS] API
docker compose -f "%API_COMPOSE_FILE%" ps
echo.
echo [STATUS] Worker
docker compose -f "%WORKER_COMPOSE_FILE%" ps
echo.
echo [STATUS] Frontend
docker compose -f "%FRONTEND_COMPOSE_FILE%" ps
exit /b 0

:show_logs
echo [LOGS] Opening stack logs in separate windows...
start "Trajecta API Logs" cmd /k docker compose -f "%API_COMPOSE_FILE%" logs -f --tail=200
start "Trajecta Worker Logs" cmd /k docker compose -f "%WORKER_COMPOSE_FILE%" logs -f --tail=200
start "Trajecta Frontend Logs" cmd /k docker compose -f "%FRONTEND_COMPOSE_FILE%" logs -f --tail=200
exit /b 0

:compose_down
echo [DOWN] Stopping API stack
docker compose -f "%API_COMPOSE_FILE%" down --remove-orphans
echo [DOWN] Stopping worker stack
docker compose -f "%WORKER_COMPOSE_FILE%" down --remove-orphans
echo [DOWN] Stopping frontend stack
docker compose -f "%FRONTEND_COMPOSE_FILE%" down --remove-orphans
echo [DOWN] Done.
exit /b 0

:usage
echo Usage:
echo   start-all-stacks.bat [up^|init^|validate^|build^|status^|logs^|down] [skiptests] [skipbuild] [skipcompose] [autologs] [workers N]
echo.
echo Commands:
echo   up         Initialize + build + start all local stacks ^(default^)
echo   init       Run only initialization checks ^(docker/tools/network/compose validation^)
echo   validate   Alias for init
echo   build      Build local images and optionally run API tests
echo   status     Show all stack statuses
echo   logs       Open logs for all stacks in separate windows
echo   down       Stop all local stacks
echo.
echo Flags:
echo   skiptests   Skip API tests in build stage
echo   skipbuild   Skip build stage
echo   skipcompose Skip compose up stage
echo   autologs    Open logs after successful start
echo   workers N   Scale worker service replicas
echo.
echo Examples:
echo   start-all-stacks.bat
echo   start-all-stacks.bat up skiptests autologs
echo   start-all-stacks.bat init
echo   start-all-stacks.bat status
echo   start-all-stacks.bat down
echo   start-all-stacks.bat up workers 2
exit /b 0


