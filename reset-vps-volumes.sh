#!/usr/bin/env sh
set -eu

COMPOSE_FILE="docker-compose.vps.yml"
ENV_FILE=".env.vps"
PROJECT_NAME="trajecta"
DRY_RUN=0
CONFIRM=0
STOP_STACK=0
TARGETS=""

usage() {
  cat <<'EOF'
Usage:
  ./reset-vps-volumes.sh [options] [all|db|redis|rabbitmq|minio|edge ...]

Options:
  -p, --project NAME   Compose project name (default: trajecta)
  -d, --down           Stop stack before removing volumes
  -y, --yes            Confirm destructive action
  -n, --dry-run        Show what would be removed without deleting anything
  -h, --help           Show this help

Examples:
  ./reset-vps-volumes.sh all --down --yes
  ./reset-vps-volumes.sh db redis --down --yes
  ./reset-vps-volumes.sh edge --dry-run
  ./reset-vps-volumes.sh -p trajecta minio --yes
EOF
}

fail() {
  echo "[ERROR] $1" >&2
  exit 1
}

join_by_comma() {
  sep=""
  out=""
  for item in "$@"; do
    out="${out}${sep}${item}"
    sep=", "
  done
  printf '%s' "$out"
}

append_unique() {
  value="$1"
  current=" ${2:-} "
  case "$current" in
    *" $value "*) printf '%s' "${2:-}" ;;
    *)
      if [ -z "${2:-}" ]; then
        printf '%s' "$value"
      else
        printf '%s %s' "$2" "$value"
      fi
      ;;
  esac
}

resolve_targets() {
  selected="$1"

  if [ -z "$selected" ]; then
    printf '%s' "pgdata redis_data rabbitmq_data minio_data caddy_data caddy_config"
    return 0
  fi

  case " $selected " in
    *" all "*)
      printf '%s' "pgdata redis_data rabbitmq_data minio_data caddy_data caddy_config"
      return 0
      ;;
  esac

  volumes=""
  for target in $selected; do
    case "$target" in
      db)       volumes="$(append_unique pgdata "$volumes")" ;;
      redis)    volumes="$(append_unique redis_data "$volumes")" ;;
      rabbitmq) volumes="$(append_unique rabbitmq_data "$volumes")" ;;
      minio)    volumes="$(append_unique minio_data "$volumes")" ;;
      edge)     volumes="$(append_unique caddy_data "$volumes")"; volumes="$(append_unique caddy_config "$volumes")" ;;
      all)      volumes="pgdata redis_data rabbitmq_data minio_data caddy_data caddy_config" ;;
      *) fail "Unknown target '$target'. Allowed: all, db, redis, rabbitmq, minio, edge" ;;
    esac
  done

  printf '%s' "$volumes"
}

while [ $# -gt 0 ]; do
  case "$1" in
    -p|--project)
      [ $# -ge 2 ] || fail "Missing value for $1"
      PROJECT_NAME="$2"
      shift 2
      ;;
    -d|--down)
      STOP_STACK=1
      shift
      ;;
    -y|--yes)
      CONFIRM=1
      shift
      ;;
    -n|--dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      while [ $# -gt 0 ]; do
        TARGETS="${TARGETS}${TARGETS:+ }$1"
        shift
      done
      ;;
    -*)
      fail "Unknown option '$1'. Use --help for usage."
      ;;
    *)
      TARGETS="${TARGETS}${TARGETS:+ }$1"
      shift
      ;;
  esac
done

[ -f "$COMPOSE_FILE" ] || fail "Missing $COMPOSE_FILE in current directory"
[ -f "$ENV_FILE" ] || fail "Missing $ENV_FILE. Create it from .env.vps.example"
command -v docker >/dev/null 2>&1 || fail "docker command not found"
docker compose version >/dev/null 2>&1 || fail "docker compose plugin is not available"

VOLUME_KEYS=$(resolve_targets "$TARGETS")

printf '%s\n' "Selected groups : ${TARGETS:-all}"
printf '%s\n' "Project name    : $PROJECT_NAME"
printf '%s\n' "Volume keys     : $VOLUME_KEYS"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "[DryRun] Would inspect and remove matching volumes for compose project '$PROJECT_NAME'."
fi

VOLUME_NAMES=""
for key in $VOLUME_KEYS; do
  names=$(docker volume ls \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter "label=com.docker.compose.volume=$key" \
    --format '{{.Name}}' || true)

  for name in $names; do
    case " $VOLUME_NAMES " in
      *" $name "*) ;;
      *) VOLUME_NAMES="${VOLUME_NAMES}${VOLUME_NAMES:+ }$name" ;;
    esac
  done
done

if [ -z "$VOLUME_NAMES" ]; then
  echo "No matching volumes found for project '$PROJECT_NAME'. Nothing to reset."
  exit 0
fi

printf '%s\n' "Matching volumes: $VOLUME_NAMES"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "[DryRun] Would remove volumes: $VOLUME_NAMES"
  if [ "$STOP_STACK" -eq 1 ]; then
    echo "[DryRun] Would also run: docker compose --env-file $ENV_FILE -f $COMPOSE_FILE down --remove-orphans"
  fi
  exit 0
fi

if [ "$CONFIRM" -ne 1 ]; then
  echo "[WARN] This action will permanently remove data from the volumes above."
  echo "Re-run with --yes to confirm."
  exit 1
fi

if [ "$STOP_STACK" -eq 1 ]; then
  echo "Stopping VPS stack before volume removal..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down --remove-orphans
fi

echo "Removing volumes..."
# shellcheck disable=SC2086
docker volume rm $VOLUME_NAMES

echo "Done. Removed volumes: $VOLUME_NAMES"
echo "Tip: start stack again with ./deploy-vps.sh up or docker compose up -d."

