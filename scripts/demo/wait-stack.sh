#!/usr/bin/env bash
# Wait for the compose stack to be healthy and for every service to register.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="docker compose -f $ROOT_DIR/docker-compose.yml"

services=(api-gateway:8080 account-command-service:8081 ledger-query-service:8082
  fraud-detection-service:8083 notification-service:8084 loan-service:8085
  payment-reservation-service:8086 trust-score-service:8087 frontend:3000)
timeout=${STACK_TIMEOUT:-300}
deadline=$((SECONDS + timeout))

printf 'Waiting for stack health (timeout %ss)...\n' "$timeout"
while (( SECONDS < deadline )); do
  ready=0
  for entry in "${services[@]}"; do
    name=${entry%%:*}
    port=${entry##*:}
    if curl -fsS --max-time 2 "http://localhost:$port/actuator/health" 2>/dev/null \
        | grep -q '"status":"UP"' \
        || { [[ "$name" == frontend ]] && curl -fsS --max-time 2 "http://localhost:$port/healthz" >/dev/null 2>&1; }; then
      ready=$((ready + 1))
    fi
  done
  if (( ready == ${#services[@]} )); then
    break
  fi
  sleep 3
done

if (( ready != ${#services[@]} )); then
  printf 'Not all services healthy (%s/%s). Recent compose status:\n' "$ready" "${#services[@]}"
  $COMPOSE ps
  exit 1
fi
printf 'All %s services report UP.\n' "${#services[@]}"

# Gateway routing needs Eureka registrations, not just direct health.
for _ in $(seq 1 60); do
  apps=$(curl -fsS --max-time 3 -H 'Accept: application/json' \
    http://localhost:8761/eureka/apps 2>/dev/null || true)
  count=$(jq -r '.applications.application | length' <<<"$apps" 2>/dev/null || echo 0)
  if (( count >= 7 )); then
    printf 'Eureka has %s registered services; gateway routing ready.\n' "$count"
    exit 0
  fi
  sleep 3
done

printf 'Eureka did not reach 7 registrations in time.\n' >&2
curl -fsS -H 'Accept: application/json' http://localhost:8761/eureka/apps 2>/dev/null | jq -r '.applications.application[].name' || true
exit 1
