#!/usr/bin/env bash
# Proof 10 — event fan-out: account events drive trust, fraud and notifications.
source "$(dirname "$0")/common.sh"

info "Proof 10: trust scoring, fraud detection, MailHog notifications"
login_all

scored=$(new_id)
open_account "$scored" 49.37.10.10 0 "Trust Demo"
assert_code 201 "open trust-scoring account"

api GET "/api/trust-score/$scored" "$CUSTOMER_TOKEN"
assert_code 200 "baseline trust score exists"
assert_json_eq '.score' 50 "new account starts at 50"
assert_json_eq '.tier' 1 "tier 1 (Medium)"

api POST "/api/accounts/$scored/deposits" "$CUSTOMER_TOKEN" \
  '{"amount":44000,"deviceId":"proof10","idempotencyKey":"p10-deposit"}' "p10-deposit"
assert_code 201 "deposit ₹44,000"

# Two complete ₹20,000 blocks award two points at the documented ₹10,000 → +0.5 rate.
wait_check "trust score rises 50 → 52 from the deposit" 60 trust_score_is "$scored" 52 || true

api GET "/api/trust-score/changes?accountId=$scored" "$CUSTOMER_TOKEN"
assert_code 200 "trust change feed"
assert_json_eq '.[0].reason' DEPOSIT "latest change is a DEPOSIT"
assert_json_eq '.[0].newScore' 52 "change feed records the new score"

fraud_acct=$(new_id)
open_account "$fraud_acct" 8.8.8.8 5000 "Fraud Demo"
assert_code 201 "open fraud-demo account"
for i in 1 2 3; do
  api POST "/api/accounts/$fraud_acct/withdrawals" "$CUSTOMER_TOKEN" \
    "{\"amount\":1500,\"deviceId\":\"proof10\",\"idempotencyKey\":\"p10-w-$i\"}" "p10-w-$i"
  assert_code 202 "withdrawal $i of 3 accepted"
done

wait_check "velocity rule opens a fraud case" 60 fraud_case_status_is "$fraud_acct" OPEN || true

api GET "/api/fraud/cases?accountId=$fraud_acct" "$BANK_TOKEN"
assert_code 200 "bank lists fraud cases"
assert_json_eq '.[0].rule' VELOCITY_3_WITHDRAWALS_60S "case names the velocity rule"
case_id=$(jqr '.[0].caseId')

api POST "/api/fraud/cases/$case_id/review" "$CUSTOMER_TOKEN" '{"status":"DISMISSED"}' "p10-customer-review"
assert_code 403 "customer cannot review a fraud case"

api POST "/api/fraud/cases/$case_id/review" "$BANK_TOKEN" '{"status":"REVIEWED","note":"demo reviewed"}' "p10-review"
assert_code 200 "bank reviews the case"
assert_json_eq '.status' REVIEWED "case status is REVIEWED"
assert_json_true '.reviewedAt != null' "reviewedAt recorded"

# Poll the outbox directly with a dedicated check.
notification_has() {  local account=$1 cause=$2
  api GET "/api/notifications?accountId=$account" "$CUSTOMER_TOKEN"
  [[ "$LAST_CODE" == "200" ]] || return 1
  jq -e --arg c "$cause" 'map(.causeEvent) | index($c) != null' >/dev/null 2>&1 <<<"$LAST_BODY"
}
wait_check "notification sent for MoneyDeposited" 60 notification_has "$scored" MoneyDeposited || true
wait_check "notification sent for TrustScoreChanged" 60 notification_has "$scored" TrustScoreChanged || true
wait_check "notification sent for FraudCaseOpened" 60 notification_has "$fraud_acct" FraudCaseOpened || true
wait_check "notification sent for FraudCaseReviewed" 60 notification_has "$fraud_acct" FraudCaseReviewed || true

api GET "/api/notifications?accountId=$fraud_acct" "$CUSTOMER_TOKEN"
assert_code 200 "notification outbox readable"
assert_json_true 'length >= 4' "fraud account has withdrawal + case notifications"
assert_json_true 'all(.deliveryStatus == "SENT")' "all notifications delivered to MailHog"

mail=$(curl -sS --max-time 20 "http://localhost:8025/api/v2/messages?limit=200" || true)
if jq -e --arg addr "$scored@example.com" \
    '[.items[]?.Content.Headers.To[]? | tostring | contains($addr)] | any' \
    >/dev/null 2>&1 <<<"$mail"; then
  pass "MailHog received mail addressed to $scored@example.com"
else
  fail "MailHog did not show mail for $scored@example.com"
fi

api GET "/api/accounts/$scored/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "trust-demo hash chain valid"
api GET "/api/accounts/$fraud_acct/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "fraud-demo hash chain valid"

summary
