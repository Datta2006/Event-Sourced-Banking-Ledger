#!/usr/bin/env bash
# Proof 4 — CQRS: Kafka-driven read model, statement endpoints, replay rebuild.
source "$(dirname "$0")/common.sh"

info "Proof 4: write model → Kafka → FerretDB read model, then replay"
login_all

account=$(new_id)
open_account "$account" 49.37.10.10 1000 "CQRS Demo"
assert_code 201 "open account with ₹1,000"

api POST "/api/accounts/$account/deposits" "$CUSTOMER_TOKEN" \
  '{"amount":250,"deviceId":"proof4","idempotencyKey":"p4-deposit"}' "p4-deposit"
assert_code 201 "deposit ₹250"

api POST "/api/accounts/$account/withdrawals" "$CUSTOMER_TOKEN" \
  '{"amount":100,"deviceId":"proof4","idempotencyKey":"p4-withdraw"}' "p4-withdraw"
assert_code 202 "withdraw ₹100"

projection_version_is() {
  local id=$1 version=$2
  api GET "/api/accounts/$id" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  jq -e --argjson v "$version" '.lastVersion == $v' >/dev/null 2>&1 <<<"$LAST_BODY"
}

wait_check "read model projects balance ₹1,150" 45 projection_balance_is "$account" 1150 || true
wait_check "read model projects version 3" 45 projection_version_is "$account" 3 || true

api GET "/api/accounts/$account" "$CUSTOMER_TOKEN"
assert_code 200 "read account from Ledger Query"
assert_json_eq '.holderName' "CQRS Demo" "holder projected"
assert_json_eq '.region' APAC "region projected"
assert_json_eq '(.availableBalance|tonumber)' 1150 "available balance projected"

api GET "/api/accounts/$account/events" "$CUSTOMER_TOKEN"
assert_code 200 "per-account event feed"
assert_json_eq 'length' 4 "four events in the aggregate stream"
assert_json_eq '[.[].type] | join(",")' "AccountOpened,MoneyDeposited,MoneyDeposited,MoneyWithdrawn" "ordered event types"
assert_json_true '[.[].version] == [0,1,2,3]' "versions are contiguous"

api GET "/api/transactions?accountId=$account" "$CUSTOMER_TOKEN"
assert_code 200 "statement endpoint"
assert_json_eq 'length' 3 "three money movements on the statement"
assert_json_true 'map(.type) | index("MoneyWithdrawn") != null' "withdrawal appears in statement"

api GET "/api/events?limit=200" "$CUSTOMER_TOKEN"
assert_code 200 "global event feed"

replay_code=$(curl -sS --max-time 120 -X POST \
  "http://localhost:8082/internal/projections/replay?drop=false" \
  -o "$TMP/replay.json" -w "%{http_code}" || true)
if [[ "$replay_code" == "200" ]]; then
  replayed=$(jq -r '.replayed' "$TMP/replay.json")
  pass "replay from all shards completed (HTTP 200, $replayed events)"
  assert_eq false "$(jq -r '.droppedFirst' "$TMP/replay.json")" "replay kept the existing read model"
  if (( replayed >= 4 )); then
    pass "replay covered at least this aggregate's four events"
  else
    fail "replay unexpectedly covered only $replayed events"
  fi
else
  fail "replay endpoint returned HTTP $replay_code: $(cat "$TMP/replay.json" 2>/dev/null)"
fi

wait_check "read model is still ₹1,150 after replay" 45 projection_balance_is "$account" 1150 || true

summary
