#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
API_DIR="$ROOT_DIR/Trajecta-api"
WORKER_DIR="$ROOT_DIR/Trajecta-worker"
FRONTEND_DIR="$ROOT_DIR/Trajecta-frontend"

API_COMPOSE_FILE="$API_DIR/compose.yaml"
WORKER_COMPOSE_FILE="$WORKER_DIR/docker-compose.yml"
FRONTEND_COMPOSE_FILE="$FRONTEND_DIR/docker-compose.yml"
NETWORK_NAME="telemetry-network"

RUN_BUILD=1
RUN_TESTS=1
RUN_COMPOSE=1
RUN_LOGS=0

print_usage() {
  cat <<'USAGE'
Usage:
  ./start-all-stacks.sh [up|init|build|status|logs|down] [skiptests] [skipbuild] [skipcompose] [autologs]

Commands:
  up         Initialize + build + start all local stacks (default)
  init       Run only initialization checks (docker/tools/network/compose validation)
  build      Build local images and optionally run API tests
  status     Show all stack statuses
  logs       Follow logs for all stacks
  down       Stop all local stacks

Flags:
  skiptests   Skip API tests in build stage
  skipbuild   Skip image/API build stage
  skipcompose Skip compose up stage
  autologs    Tail logs after successful start

Examples:
  ./start-all-stacks.sh
  ./start-all-stacks.sh up skiptests autologs
  ./start-all-stacks.sh build skiptests
  ./start-all-stacks.sh init
  ./start-all-stacks.sh status
  ./start-all-stacks.sh down
USAGE
}

require_file() {
  local path="$1"
  local name="$2"
  if [[ ! -f "$path" ]]; then
    echo "[ERROR] Missing $name: $path"
    exit 1
  fi
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] Required command not found: $cmd"
    exit 1
  fi
}

parse_args() {
  ACTION="up"

  if [[ $# -gt 0 ]]; then
    case "$1" in
      up|init|build|status|logs|down)
        ACTION="$1"
        shift
        ;;
      help|-h|--help)
        print_usage
        exit 0
        ;;
    esac
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      skiptests)
        RUN_TESTS=0
        ;;
      skipbuild)
        RUN_BUILD=0
        ;;
      skipcompose)
        RUN_COMPOSE=0
        ;;
      autologs|logs)
        RUN_LOGS=1
        ;;
      help|-h|--help)
        print_usage
        exit 0
        ;;
      *)
        echo "[WARN] Unknown argument: $1"
        ;;
    esac
    shift
  done
}

init_local() {
  echo "[INIT] Validating local prerequisites..."

  require_cmd docker
  require_file "$API_COMPOSE_FILE" "API compose file"
  require_file "$WORKER_COMPOSE_FILE" "Worker compose file"
  require_file "$FRONTEND_COMPOSE_FILE" "Frontend compose file"

  if ! docker compose version >/dev/null 2>&1; then
    echo "[ERROR] docker compose plugin is not available"
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    echo "[ERROR] Docker daemon is not reachable"
    exit 1
  fi

  if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
    echo "[INIT] Creating shared docker network: $NETWORK_NAME"
    docker network create "$NETWORK_NAME" >/dev/null
  else
    echo "[INIT] Network already exists: $NETWORK_NAME"
  fi

  echo "[INIT] Validating compose files..."
  docker compose -f "$API_COMPOSE_FILE" config >/dev/null
  docker compose -f "$WORKER_COMPOSE_FILE" config >/dev/null
  docker compose -f "$FRONTEND_COMPOSE_FILE" config >/dev/null

  echo "[INIT] Done."
}

build_local() {
  if [[ "$RUN_BUILD" != "1" ]]; then
    echo "[BUILD] Skipped."
    return
  fi

  echo "[BUILD] API module"
  if [[ "$RUN_TESTS" == "1" ]]; then
    (cd "$API_DIR" && sh gradlew --no-daemon clean build)
  else
    (cd "$API_DIR" && sh gradlew --no-daemon clean build -x test)
  fi

  echo "[BUILD] Worker image"
  docker compose -f "$WORKER_COMPOSE_FILE" build worker

  echo "[BUILD] Frontend image"
  docker compose -f "$FRONTEND_COMPOSE_FILE" build frontend

  echo "[BUILD] Done."
}

compose_up() {
  if [[ "$RUN_COMPOSE" != "1" ]]; then
    echo "[UP] Compose start skipped."
    return
  fi

  echo "[UP] Starting API stack"
  docker compose -f "$API_COMPOSE_FILE" up --build --force-recreate --remove-orphans -d

  echo "[UP] Starting worker stack"
  docker compose -f "$WORKER_COMPOSE_FILE" up --build --force-recreate --remove-orphans -d

  echo "[UP] Starting frontend stack"
  docker compose -f "$FRONTEND_COMPOSE_FILE" up --build --force-recreate --remove-orphans -d

  echo "[UP] All stacks are up."
}

show_status() {
  echo "[STATUS] API"
  docker compose -f "$API_COMPOSE_FILE" ps
  echo
  echo "[STATUS] Worker"
  docker compose -f "$WORKER_COMPOSE_FILE" ps
  echo
  echo "[STATUS] Frontend"
  docker compose -f "$FRONTEND_COMPOSE_FILE" ps
}

show_logs() {
  echo "[LOGS] Press Ctrl+C to stop."
  docker compose -f "$API_COMPOSE_FILE" logs -f --tail=100 &
  local api_pid=$!
  docker compose -f "$WORKER_COMPOSE_FILE" logs -f --tail=100 &
  local worker_pid=$!
  docker compose -f "$FRONTEND_COMPOSE_FILE" logs -f --tail=100 &
  local frontend_pid=$!

  trap 'kill "$api_pid" "$worker_pid" "$frontend_pid" >/dev/null 2>&1 || true' INT TERM EXIT
  wait
}

compose_down() {
  echo "[DOWN] Stopping API stack"
  docker compose -f "$API_COMPOSE_FILE" down --remove-orphans
  echo "[DOWN] Stopping worker stack"
  docker compose -f "$WORKER_COMPOSE_FILE" down --remove-orphans
  echo "[DOWN] Stopping frontend stack"
  docker compose -f "$FRONTEND_COMPOSE_FILE" down --remove-orphans
  echo "[DOWN] Done."
}

main() {
  parse_args "$@"

  case "$ACTION" in
    init)
      init_local
      ;;
    build)
      init_local
      build_local
      ;;
    status)
      show_status
      ;;
    logs)
      show_logs
      ;;
    down)
      compose_down
      ;;
    up)
      init_local
      build_local
      compose_up
      show_status
      if [[ "$RUN_LOGS" == "1" ]]; then
        show_logs
      fi
      ;;
    *)
      echo "[ERROR] Unsupported action: $ACTION"
      print_usage
      exit 1
      ;;
  esac
}

main "$@"

