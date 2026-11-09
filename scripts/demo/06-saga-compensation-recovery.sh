#!/usr/bin/env bash
# Proof 6 — saga compensation and crash recovery.
#   A. source debited, target credit fails (target CLOSED) → TransferFailed +
#      TransferSourceReimbursed, source balance restored.
#   B. account service is killed between source debit and target credit; on
#      restart recoverSagas() resumes the persisted saga and completes it once.
source "$(dirname "$0")/common.sh"

RECOVERY_DIRTY=0
cleanup() {
  if [[ "$RECOVERY_DIRTY" == "1" ]]; then
    $COMPOSE up -d --no-deps --force-recreate account-command-service >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT

wait_service_up() {
  local port=$1 timeout=$2
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 2 "http://localhost:$port/actuator/health" 2>/dev/null \
        | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

info "Proof 6: saga compensation (target closed)"
login_all

source_acct=$(new_id)
closed_target=$(new_id)
open_account "$source_acct" 49.37.10.10 1000 "Saga Source"
assert_code 201 "open source account"
open_account "$closed_target" 85.214.132.1 0 "Closed Target"
assert_code 201 "open target account"

api POST "/api/accounts/$closed_target/close" "$BANK_TOKEN" "" "p6-close"
assert_code 200 "bank closes the target account"

api POST /api/transfers "$CUSTOMER_TOKEN" \
  "{\"fromAccountId\":\"$source_acct\",\"toAccountId\":\"$closed_target\",\"amount\":200,\"idempotencyKey\":\"p6-compensate\"}" \
  "p6-compensate"
assert_code 202 "transfer accepted"
comp_id=$(jqr .transferId)
wait_check "saga compensates after target rejection" 45 transfer_status_is "$comp_id" COMPENSATED || true

api GET "/api/transfers/$comp_id" "$CUSTOMER_TOKEN"
assert_code 200 "read compensated transfer"
assert_json_eq '.status' COMPENSATED "status is COMPENSATED"
assert_json_true '.failureReason != null' "failure reason recorded"

wait_check "source balance restored to ₹1,000" 45 projection_balance_is "$source_acct" 1000 || true

api GET "/api/accounts/$source_acct/events" "$CUSTOMER_TOKEN"
assert_json_true 'map(.type) | index("TransferSourceDebited") != null' "source had been debited"
assert_json_true 'map(.type) | index("TransferFailed") != null' "TransferFailed appended"
assert_json_true 'map(.type) | index("TransferSourceReimbursed") != null' "TransferSourceReimbursed appended"
api GET "/api/accounts/$source_acct/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "compensating chain verifies"

if [[ "${RUN_RECOVERY:-1}" != "1" ]]; then
  info "Recovery sub-proof skipped (RUN_RECOVERY=$RUN_RECOVERY)"
  summary
  exit 0
fi

info "Proof 6b: crash between source debit and target credit, then restart"
RECOVERY_DIRTY=1
SAGA_PAUSE_AFTER_SOURCE_MS=30000 $COMPOSE up -d --no-deps --force-recreate account-command-service >/dev/null 2>&1
if wait_service_up 8081 120; then
  pass "account service restarted with a 30s pause after source debit"
else
  fail "account service did not become healthy with the pause hook"
  summary
  exit 1
fi

rec_source=$(new_id)
rec_target=$(new_id)
open_account "$rec_source" 49.37.10.10 500 "Recovery Source"
assert_code 201 "open recovery source"
open_account "$rec_target" 8.8.8.8 0 "Recovery Target"
assert_code 201 "open recovery target"

api POST /api/transfers "$CUSTOMER_TOKEN" \
  "{\"fromAccountId\":\"$rec_source\",\"toAccountId\":\"$rec_target\",\"amount\":125,\"idempotencyKey\":\"p6-recovery\"}" \
  "p6-recovery"
assert_code 202 "transfer accepted before crash"
rec_id=$(jqr .transferId)

wait_check "saga persists SOURCE_DEBITED before the crash" 30 transfer_status_is "$rec_id" SOURCE_DEBITED || true

$COMPOSE stop account-command-service >/dev/null 2>&1 || true
pass "account service stopped mid-saga (SIGKILL after graceful timeout)"

$COMPOSE up -d --no-deps --force-recreate account-command-service >/dev/null 2>&1
RECOVERY_DIRTY=0
if wait_service_up 8081 120; then
  pass "account service restarted normally"
else
  fail "account service did not restart"
  summary
  exit 1
fi

wait_check "startup recovery completes the persisted saga" 120 transfer_status_is "$rec_id" COMPLETED || true

api GET "/api/transfers/$rec_id" "$CUSTOMER_TOKEN"
assert_json_eq '.status' COMPLETED "recovered saga is COMPLETED"

wait_check "recovered source debited once to ₹375" 60 projection_balance_is "$rec_source" 375 || true
wait_check "recovered target credited once to ₹125" 60 projection_balance_is "$rec_target" 125 || true

api GET "/api/accounts/$rec_source/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "recovered source chain valid"
api GET "/api/accounts/$rec_source/events" "$CUSTOMER_TOKEN"
withdrawals=$(jq '[.[] | select(.type == "MoneyWithdrawn")] | length' <<<"$LAST_BODY")
assert_eq 1 "$withdrawals" "source was debited exactly once"

summary
