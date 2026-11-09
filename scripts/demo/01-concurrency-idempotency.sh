#!/usr/bin/env bash
# Proof 1 — concurrency + idempotency on the write model.
#   * 20 simultaneous ₹1,000 withdrawals against a ₹10,000 balance: exactly ten
#     succeed, ten get 409, the balance never goes negative.
#   * 5 simultaneous retries with the SAME Idempotency-Key deposit only once.
#   * the per-aggregate hash chain still verifies after the contention.
source "$(dirname "$0")/common.sh"

info "Proof 1: withdrawal contention and idempotent retries"
login_all

account=$(new_id)
open_account "$account" 49.37.10.10 10000 "Contention Demo"
assert_code 201 "open account with ₹10,000"

info "20 concurrent ₹1,000 withdrawals (distinct idempotency keys)"
for i in $(seq 1 20); do
  (
    body="{\"amount\":1000,\"deviceId\":\"proof1\",\"idempotencyKey\":\"p1-w-$i-$account\"}"
    code=$(http_call "$TMP/w$i.json" POST "/api/accounts/$account/withdrawals" \
      "$CUSTOMER_TOKEN" "$body" "p1-w-$i-$account")
    printf '%s' "$code" >"$TMP/w$i.code"
  ) &
done
wait

accepted=0
conflicts=0
other=0
for i in $(seq 1 20); do
  code=$(cat "$TMP/w$i.code")
  case "$code" in
    202) accepted=$((accepted + 1)) ;;
    409) conflicts=$((conflicts + 1)) ;;
    *) other=$((other + 1)); printf '    unexpected HTTP %s: %s\n' "$code" "$(cat "$TMP/w$i.json")" ;;
  esac
done

assert_eq 10 "$accepted" "exactly 10 withdrawals accepted"
assert_eq 10 "$conflicts" "remaining 10 rejected with 409"
assert_eq 0 "$other" "no unexpected statuses"

wait_check "read model converges to zero balance" 45 projection_balance_is "$account" 0 || true

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_code 200 "audit verification reachable"
assert_json_true '.valid == true' "hash chain valid after concurrent writes"
assert_json_eq '.eventCount' 12 "event count = AccountOpened + deposit + 10 withdrawals"

info "5 concurrent retries with one Idempotency-Key (₹100 deposit)"
for i in $(seq 1 5); do
  (
    body='{"amount":100,"deviceId":"proof1-idem","idempotencyKey":"p1-idem-'$account'"}'
    code=$(http_call "$TMP/d$i.json" POST "/api/accounts/$account/deposits" \
      "$CUSTOMER_TOKEN" "$body" "p1-idem-$account")
    printf '%s' "$code" >"$TMP/d$i.code"
  ) &
done
wait

deposit_accepted=0
for i in $(seq 1 5); do
  code=$(cat "$TMP/d$i.code")
  [[ "$code" == "201" ]] && deposit_accepted=$((deposit_accepted + 1))
done
assert_eq 5 "$deposit_accepted" "all five retries replay the stored 201 response"

wait_check "balance increased by exactly ₹100 (not ₹500)" 45 projection_balance_is "$account" 100 || true

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_json_eq '.eventCount' 13 "only ONE MoneyDeposited was appended"
assert_json_true '.valid == true' "hash chain still valid after replay"

summary
