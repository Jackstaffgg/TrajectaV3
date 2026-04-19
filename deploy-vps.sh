#!/usr/bin/env sh
set -eu

COMPOSE_FILE="docker-compose.vps.yml"
ENV_FILE=".env.vps"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "[ERROR] Missing $COMPOSE_FILE in current directory"
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "[ERROR] Missing $ENV_FILE. Create it from .env.vps.example"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker command not found"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] docker compose plugin is not available"
  exit 1
fi


WORKER_REPLICAS="$(grep -E '^WORKER_REPLICAS=' "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- | tr -d "'\"[:space:]")"
if [ -z "$WORKER_REPLICAS" ]; then
  WORKER_REPLICAS="2"
fi

case "$WORKER_REPLICAS" in
  ''|*[!0-9]*)
    echo "[ERROR] WORKER_REPLICAS must be a positive integer (current: $WORKER_REPLICAS)"
    exit 1
    ;;
  0)
    echo "[ERROR] WORKER_REPLICAS must be >= 1"
    exit 1
    ;;
esac

run_compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

action="${1:-up}"
confirm="${2:-}"

case "$action" in
  up)
    run_compose up -d --build --remove-orphans --scale "worker=$WORKER_REPLICAS"
    ;;
  pull)
    run_compose pull
    ;;
  down)
    run_compose down --remove-orphans
    ;;
  restart)
    run_compose up -d --build --force-recreate --remove-orphans --scale "worker=$WORKER_REPLICAS"
    ;;
  logs)
    run_compose logs -f --tail=200
    ;;
  status)
    run_compose ps
    ;;
  validate)
    run_compose config > /dev/null
    echo "[OK] Compose config is valid"
    ;;
  wipe)
    if [ "$confirm" != "--yes" ]; then
      echo "[ERROR] wipe is destructive. Run: ./deploy-vps.sh wipe --yes"
      exit 1
    fi
    echo "[WARN] Removing Trajecta stack containers, images, volumes and compose network..."
    run_compose down --volumes --rmi all --remove-orphans
    echo "[WARN] Pruning unused Docker resources on host (build cache, dangling images/networks)..."
    docker system prune -f
    echo "[OK] Full cleanup completed"
    ;;
  *)
    echo "Usage: ./deploy-vps.sh [up|pull|down|restart|logs|status|validate|wipe --yes]"
    echo "Hint: set WORKER_REPLICAS in .env.vps (default: 2)."
    exit 1
    ;;
esac
