#!/usr/bin/env bash
# Proof 5 — cross-region transfer saga completes (APAC → EU).
source "$(dirname "$0")/common.sh"

info "Proof 5: transfer across shards (APAC → EU)"
login_all

source_acct=$(new_id)
target_acct=$(new_id)
open_account "$source_acct" 49.37.10.10 1000 "Transfer Source"
assert_code 201 "open APAC source with ₹1,000"
open_account "$target_acct" 85.214.132.1 0 "Transfer Target"
assert_code 201 "open EU target with ₹0"

api POST /api/transfers "$CUSTOMER_TOKEN" \
  "{\"fromAccountId\":\"$source_acct\",\"toAccountId\":\"$target_acct\",\"amount\":300,\"idempotencyKey\":\"p5-transfer\"}" \
  "p5-transfer"
assert_code 202 "transfer accepted asynchronously"
transfer_id=$(jqr .transferId)
assert_json_eq '.status' INITIATED "saga starts in INITIATED"
[[ -n "$transfer_id" && "$transfer_id" != "null" ]] || die "no transferId returned"

wait_check "saga reaches COMPLETED" 45 transfer_status_is "$transfer_id" COMPLETED || true

api GET "/api/transfers/$transfer_id" "$CUSTOMER_TOKEN"
assert_code 200 "read transfer status"
assert_json_eq '.status' COMPLETED "transfer status is COMPLETED"
assert_json_eq '(.amount|tonumber)' 300 "transfer amount recorded"

wait_check "source read model debited to ₹700" 45 projection_balance_is "$source_acct" 700 || true
wait_check "target read model credited to ₹300" 45 projection_balance_is "$target_acct" 300 || true

api GET "/api/accounts/$source_acct/events" "$CUSTOMER_TOKEN"
assert_json_true 'map(.type) | index("TransferInitiated") != null' "source event: TransferInitiated"
assert_json_true 'map(.type) | index("MoneyWithdrawn") != null' "source event: MoneyWithdrawn"
assert_json_true 'map(.type) | index("TransferSourceDebited") != null' "source event: TransferSourceDebited"
assert_json_true 'map(.type) | index("TransferCompleted") != null' "source event: TransferCompleted"

api GET "/api/accounts/$target_acct/events" "$CUSTOMER_TOKEN"
assert_json_true 'map(.type) | index("MoneyDeposited") != null' "target event: MoneyDeposited"
assert_json_true 'map(.type) | index("TransferTargetCredited") != null' "target event: TransferTargetCredited"

api GET "/api/accounts/$source_acct/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "source hash chain valid"
api GET "/api/accounts/$target_acct/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "target hash chain valid"

api POST /api/transfers "$CUSTOMER_TOKEN" \
  "{\"fromAccountId\":\"$source_acct\",\"toAccountId\":\"$target_acct\",\"amount\":300,\"idempotencyKey\":\"p5-transfer\"}" \
  "p5-transfer"
assert_code 202 "retrying the same Idempotency-Key is accepted"
assert_eq "$transfer_id" "$(jqr .transferId)" "retry replayed the same transferId"

summary
