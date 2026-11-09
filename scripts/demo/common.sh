#!/usr/bin/env bash
# Shared helpers for the live, end-to-end demo proofs.
#
# Every proof drives the system through the API Gateway (the same surface the
# browser uses) unless it explicitly needs a storage-layer fixture. Assertions
# are printed as PASS/FAIL and the process exits non-zero if anything failed.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_BASE="${API_BASE:-http://localhost:8080}"
COMPOSE="docker compose -f $ROOT_DIR/docker-compose.yml"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/ddbs-demo.XXXXXX")"
export TMP
trap 'rm -rf "$TMP"' EXIT

PASSED=0
FAILED=0

CUSTOMER_TOKEN=""
BANK_TOKEN=""
LAST_CODE=""
LAST_BODY=""

pass() {
  PASSED=$((PASSED + 1))
  printf '  \033[32mPASS\033[0m  %s\n' "$*"
}

fail() {
  FAILED=$((FAILED + 1))
  printf '  \033[31mFAIL\033[0m  %s\n' "$*" >&2
}

info() {
  printf '\n\033[1m%s\033[0m\n' "$*"
}

die() {
  printf '\033[31mfatal:\033[0m %s\n' "$*" >&2
  exit 2
}

new_id() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

# http_call BODYFILE METHOD PATH TOKEN DATA IDEMPOTENCY
# Prints the HTTP status code; writes the response body to BODYFILE.
http_call() {
  local out=$1 method=$2 path=$3 token=${4:-} data=${5:-} idem=${6:-}
  local args=(-sS --max-time 120 -X "$method" "$API_BASE$path"
    -H "Accept: application/json" -o "$out" -w "%{http_code}")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$idem" ]]; then
    args+=(-H "Idempotency-Key: $idem")
  fi
  if [[ -n "$data" ]]; then
    args+=(-H "Content-Type: application/json" --data "$data")
  fi
  curl "${args[@]}" || true
}

# api METHOD PATH [TOKEN] [DATA] [IDEMPOTENCY]
# Sets LAST_CODE and LAST_BODY.
api() {
  local out="$TMP/api.$$.$RANDOM.json"
  LAST_CODE=$(http_call "$out" "$@")
  LAST_BODY=$(cat "$out" 2>/dev/null || true)
  rm -f "$out"
}

# api_on BASE METHOD PATH ...
api_on() {
  local base=$1; shift
  local saved=$API_BASE
  API_BASE=$base
  api "$@"
  API_BASE=$saved
}

jqr() {
  jq -r "$1" <<<"$LAST_BODY" 2>/dev/null || true
}

assert_code() {
  local expected=$1 label=$2
  if [[ "$LAST_CODE" == "$expected" ]]; then
    pass "$label [HTTP $LAST_CODE]"
  else
    fail "$label [expected HTTP $expected, got ${LAST_CODE:-<none>}] body=$(tr '\n' ' ' <<<"$LAST_BODY" | cut -c1-300)"
  fi
}

assert_eq() {
  local expected=$1 actual=$2 label=$3
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label [expected '$expected', got '$actual']"
  fi
}

assert_json_eq() {
  local expr=$1 expected=$2 label=$3
  local actual
  actual=$(jq -r "$expr" <<<"$LAST_BODY" 2>/dev/null || true)
  assert_eq "$expected" "$actual" "$label ($expr)"
}

assert_json_true() {
  local expr=$1 label=$2
  if jq -e "$expr" >/dev/null 2>&1 <<<"$LAST_BODY"; then
    pass "$label"
  else
    fail "$label [jq '$expr' false for: $(tr '\n' ' ' <<<"$LAST_BODY" | cut -c1-300)]"
  fi
}

assert_contains() {
  local needle=$1 label=$2
  if grep -qF -- "$needle" <<<"$LAST_BODY"; then
    pass "$label"
  else
    fail "$label [missing '$needle']"
  fi
}

# wait_check DESCRIPTION TIMEOUT_SECONDS COMMAND...
wait_check() {
  local desc=$1 timeout=$2; shift 2
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if "$@" >/dev/null 2>&1; then
      pass "$desc"
      return 0
    fi
    sleep 1
  done
  fail "$desc [timed out after ${timeout}s]"
  return 1
}

login_all() {
  api POST /api/auth/login "" '{"username":"customer","password":"customer123"}'
  assert_code 200 "customer login"
  CUSTOMER_TOKEN=$(jqr .token)
  [[ -n "$CUSTOMER_TOKEN" ]] || die "customer login returned no token"

  api POST /api/auth/login "" '{"username":"bank","password":"bank123"}'
  assert_code 200 "bank login"
  BANK_TOKEN=$(jqr .token)
  [[ -n "$BANK_TOKEN" ]] || die "bank login returned no token"
}

# open_account ID IP INITIAL_DEPOSIT HOLDER
open_account() {
  local id=$1 ip=$2 amount=$3 holder=${4:-Demo User}
  api POST /api/accounts "$CUSTOMER_TOKEN" \
    "{\"accountId\":\"$id\",\"holderName\":\"$holder\",\"registrationIp\":\"$ip\",\"initialDeposit\":$amount,\"idempotencyKey\":\"open-$id\"}" \
    "open-$id"
}

get_account_cmd() {
  api GET "/api/accounts/$1" "$CUSTOMER_TOKEN"
}

# psql_db DATABASE SQL - query the compose Postgres from inside the container.
psql_db() {
  local db=$1 sql=$2
  $COMPOSE exec -T postgres psql -U ddbs -d "$db" -tAc "$sql" | tr -d '\r'
}

valkey() {
  $COMPOSE exec -T valkey valkey-cli "$@" | tr -d '\r'
}

# projection_balance_is ACCOUNT_ID EXPECTED (numeric compare, eventual)
projection_balance_is() {
  local id=$1 expected=$2
  api GET "/api/accounts/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  jq -e --argjson e "$expected" '(.availableBalance | tonumber) == $e' >/dev/null 2>&1 <<<"$LAST_BODY"
}

projection_reserved_is() {
  local id=$1 expected=$2
  api GET "/api/accounts/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  jq -e --argjson e "$expected" '(.reservedBalance | tonumber) == $e' >/dev/null 2>&1 <<<"$LAST_BODY"
}

transfer_status_is() {
  local id=$1 status=$2
  api GET "/api/transfers/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  [[ "$(jqr .status)" == "$status" ]]
}

loan_status_is() {
  local id=$1 status=$2
  api GET "/api/loans/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  [[ "$(jqr .status)" == "$status" ]]
}

trust_score_is() {
  local id=$1 score=$2
  api GET "/api/trust-score/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  [[ "$(jqr .score)" == "$score" ]]
}

fraud_case_status_is() {
  local id=$1 status=$2
  api GET "/api/fraud/cases?accountId=$id" "$BANK_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  jq -e --arg s "$status" 'length > 0 and (.[0].status == $s)' >/dev/null 2>&1 <<<"$LAST_BODY"
}

summary() {
  printf '\n\033[1mResult: %d passed, %d failed\033[0m\n' "$PASSED" "$FAILED"
  if (( FAILED > 0 )); then
    exit 1
  fi
}
