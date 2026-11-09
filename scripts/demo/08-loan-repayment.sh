#!/usr/bin/env bash
# Proof 8 — full loan repayment returns funds to the admin pool and updates trust.
source "$(dirname "$0")/common.sh"

info "Proof 8: loan repayment, pool replenishment and trust score"
login_all

api GET /api/loans/pool "$BANK_TOKEN"
assert_code 200 "read pool before repayment"
before=$(jq -r '.balance | tonumber' <<<"$LAST_BODY")
if (( $(printf '%.0f' "$before") < 2000 )); then
  api POST /api/loans/pool/replenish "$BANK_TOKEN" '{"amount":5000}'
  before=$(jq -r '.balance | tonumber' <<<"$LAST_BODY")
  pass "pool topped up for the repayment proof"
fi

account=$(new_id)
open_account "$account" 49.37.10.10 0 "Repayment Demo"
assert_code 201 "open borrower account"

api POST /api/loans "$CUSTOMER_TOKEN" \
  "{\"accountId\":\"$account\",\"amount\":1000,\"purpose\":\"repayment-proof\",\"idempotencyKey\":\"p8-loan\"}" \
  "p8-loan"
assert_code 202 "request ₹1,000 loan"
loan=$(jqr .loanId)
wait_check "loan is disbursed from the pool" 45 loan_status_is "$loan" DISBURSED || true

api GET "/api/loans/pool" "$BANK_TOKEN"
after_disbursement=$(jq -r '.balance | tonumber' <<<"$LAST_BODY")
expected_disbursed=$(awk -v b="$before" 'BEGIN { printf "%.2f", b - 1000 }')
actual_disbursed=$(awk -v a="$after_disbursement" 'BEGIN { printf "%.2f", a }')
assert_eq "$expected_disbursed" "$actual_disbursed" "pool reduced by the disbursed amount"

wait_check "borrower received ₹1,000" 45 projection_balance_is "$account" 1000 || true

api POST "/api/loans/$loan/repayment" "$CUSTOMER_TOKEN" '{"amount":1000}' "p8-repay"
assert_code 200 "full repayment accepted"
assert_json_eq '.status' REPAID "loan status is REPAID"
assert_json_true '.repaidAt != null' "repaidAt recorded"

api GET "/api/loans/pool" "$BANK_TOKEN"
after_repayment=$(jq -r '.balance | tonumber' <<<"$LAST_BODY")
expected_repaid=$(awk -v b="$before" 'BEGIN { printf "%.2f", b }')
actual_repaid=$(awk -v a="$after_repayment" 'BEGIN { printf "%.2f", a }')
assert_eq "$expected_repaid" "$actual_repaid" "pool restored to its pre-loan balance"

reason=$(psql_db ledger "SELECT reason FROM loan.pool_replenishments ORDER BY occurred_at DESC LIMIT 1")
assert_eq LOAN_REPAID "$reason" "pool history records reason LOAN_REPAID"

wait_check "borrower debited back to ₹0" 45 projection_balance_is "$account" 0 || true
wait_check "trust: 50 −5 (loan taken) +15 (early repayment) = 60" 60 trust_score_is "$account" 60 || true

api GET "/api/trust-score/changes?accountId=$account" "$CUSTOMER_TOKEN"
assert_code 200 "trust change feed"
assert_json_true '[.[] | select(.reason == "LOAN_REPAYMENT")] | length > 0' "LOAN_REPAYMENT change recorded"

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "borrower hash chain valid"

api POST "/api/loans/$loan/repayment" "$CUSTOMER_TOKEN" '{"amount":1000}' "p8-repay"
assert_code 200 "repayment retry is idempotent"
assert_json_eq '.status' REPAID "replayed loan status is REPAID"

summary
