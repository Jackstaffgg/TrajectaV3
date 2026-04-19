#!/bin/bash

set -u

API_URL="${1:-https://localhost}"
USERS_COUNT="${2:-30}"
FRONTEND_URL="${3:-https://localhost}"
PROBE_PATH="${4:-/api/v1/users/me}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

pass_count=0
warn_count=0
fail_count=0

print_status() {
  local name="$1"
  local status="$2"
  local details="$3"
  local color="$NC"

  case "$status" in
    PASS) color="$GREEN"; pass_count=$((pass_count + 1));;
    WARN) color="$YELLOW"; warn_count=$((warn_count + 1));;
    FAIL) color="$RED"; fail_count=$((fail_count + 1));;
  esac

  printf "%b%-34s %-5s %s%b\n" "$color" "$name" "$status" "$details" "$NC"
}

check_endpoint() {
  local name="$1"
  local url="$2"
  local auth_header="${3:-}"
  local warn_codes="${4:-}"

  local body http_code payload

  if [ -n "$auth_header" ]; then
    body=$(curl -sk -m 6 -H "$auth_header" -w "\n%{http_code}" "$url" 2>/dev/null || true)
  else
    body=$(curl -sk -m 6 -w "\n%{http_code}" "$url" 2>/dev/null || true)
  fi

  http_code=$(echo "$body" | tail -n1)
  payload=$(echo "$body" | sed '$d' | tr '\n' ' ' | head -c 120)

  if [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
    print_status "$name" "PASS" "HTTP $http_code; body=$payload"
  elif [[ "$http_code" =~ ^3[0-9][0-9]$ ]]; then
    print_status "$name" "PASS" "HTTP $http_code (redirect)"
  elif [[ ",${warn_codes}," == *",${http_code},"* ]]; then
    print_status "$name" "WARN" "HTTP $http_code"
  elif [ -n "$http_code" ]; then
    print_status "$name" "FAIL" "HTTP $http_code"
  else
    print_status "$name" "FAIL" "Connection failed"
  fi
}

check_compose_stack() {
  local name="$1"
  local compose_file="$2"

  if [ ! -f "$compose_file" ]; then
    print_status "$name" "WARN" "Compose file not found"
    return
  fi

  local output
  output=$(docker compose -f "$compose_file" ps --format json 2>/dev/null || true)

  if [ -z "$output" ]; then
    print_status "$name" "WARN" "No services found"
    return
  fi

  local total running
  total=$(echo "$output" | grep -c '{' || true)
  running=$(echo "$output" | grep -c '"State":"running"' || true)

  if [ "$running" -eq "$total" ]; then
    print_status "$name" "PASS" "running=$running total=$total"
  else
    print_status "$name" "WARN" "running=$running total=$total"
  fi
}

echo -e "\n${CYAN}====================================${NC}"
echo -e "${CYAN}TRAJECTA HOST DIAGNOSTICS${NC}"
echo -e "${CYAN}====================================${NC}"
echo "API URL      : $API_URL"
echo "Frontend URL : $FRONTEND_URL"
echo "Users probe  : $USERS_COUNT"
echo "Probe path   : $PROBE_PATH"

echo -e "\n${YELLOW}[1/4] Host and containers${NC}"
if docker info >/dev/null 2>&1; then
  print_status "Docker daemon" "PASS" "Reachable"
else
  print_status "Docker daemon" "FAIL" "docker info failed"
fi

check_compose_stack "VPS stack" "$ROOT_DIR/docker-compose.vps.yml"

echo -e "\n${YELLOW}[2/4] HTTP endpoints${NC}"

check_endpoint "Frontend root" "$FRONTEND_URL"
check_endpoint "API /me" "$API_URL$PROBE_PATH" "Authorization: Bearer $ADMIN_TOKEN"

echo -e "\n${YELLOW}[3/4] Concurrent API probe${NC}"

tmpdir=$(mktemp -d)

for i in $(seq 1 "$USERS_COUNT"); do
  {
    curl -sk -m 8 \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -o /dev/null \
      -w "%{http_code} %{time_total}" \
      "$API_URL$PROBE_PATH" > "$tmpdir/$i" 2>/dev/null || echo "000 0" > "$tmpdir/$i"
  } &
done

wait

ok=0
failed=0
times=()
declare -A codes

for i in $(seq 1 "$USERS_COUNT"); do
  code=$(awk '{print $1}' "$tmpdir/$i")
  t=$(awk '{print $2}' "$tmpdir/$i")

  if [[ "$code" =~ ^2[0-9][0-9]$ || "$code" =~ ^3[0-9][0-9]$ ]]; then
    ok=$((ok + 1))
    times+=("$t")
  else
    failed=$((failed + 1))
  fi

  codes[$code]=$(( ${codes[$code]:-0} + 1 ))
done

rm -rf "$tmpdir"

avg_ms=0
max_ms=0
if [ "${#times[@]}" -gt 0 ]; then
  avg_ms=$(printf '%s\n' "${times[@]}" | awk '{sum+=$1} END {printf "%.1f", (sum/NR)*1000}')
  max_ms=$(printf '%s\n' "${times[@]}" | awk 'BEGIN{m=0} {if($1>m)m=$1} END {printf "%d", m*1000}')
fi

codes_summary=""
for key in $(printf '%s\n' "${!codes[@]}" | sort); do
  codes_summary="${codes_summary}${key}=${codes[$key]} "
done

if [ "$failed" -eq 0 ]; then
  print_status "Concurrent probe" "PASS" "ok=$ok avg=${avg_ms}ms max=${max_ms}ms [$codes_summary]"
elif [ "$ok" -gt 0 ]; then
  print_status "Concurrent probe" "WARN" "ok=$ok failed=$failed [$codes_summary]"
else
  print_status "Concurrent probe" "FAIL" "all failed [$codes_summary]"
fi

echo -e "\n${YELLOW}[4/4] Summary${NC}"
echo -e "${GREEN}PASS: $pass_count${NC}"
echo -e "${YELLOW}WARN: $warn_count${NC}"
echo -e "${RED}FAIL: $fail_count${NC}"

if [ "$fail_count" -eq 0 ] && [ "$warn_count" -eq 0 ]; then
  echo -e "${GREEN}System status: HEALTHY${NC}"
elif [ "$fail_count" -eq 0 ]; then
  echo -e "${YELLOW}System status: PARTIAL${NC}"
else
  echo -e "${RED}System status: UNHEALTHY${NC}"
fi

echo -e "\n${CYAN}Done.${NC}\n"
