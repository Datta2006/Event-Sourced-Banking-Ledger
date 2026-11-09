#!/usr/bin/env bash
# Proof 9 — offline reservations: signed token, double capture rejected, expiry.
source "$(dirname "$0")/common.sh"

info "Proof 9: offline token, double-capture 409, expiry 410"
login_all

payer=$(new_id)
merchant=$(new_id)
open_account "$payer" 49.37.10.10 1000 "Offline Payer"
assert_code 201 "open payer with ₹1,000"
open_account "$merchant" 85.214.132.1 0 "Demo Merchant"
assert_code 201 "open merchant account"

api POST /api/reservations "$CUSTOMER_TOKEN" \
  "{\"accountId\":\"$payer\",\"amount\":300,\"ttlMinutes\":30}" "p9-reserve"
assert_code 201 "reserve ₹300 and mint offline token"
reservation=$(jqr .reservationId)
token=$(jqr .offlineToken)
assert_json_eq '.status' RESERVED "reservation is RESERVED"
[[ -n "$token" && "$token" != "null" ]] || die "no offline token returned"

alg=$(python3 - "$token" <<'PY'
import base64, json, sys
head = sys.argv[1].split('.')[0]
head += '=' * (-len(head) % 4)
print(json.loads(base64.urlsafe_b64decode(head)).get('alg', ''))
PY
)
assert_eq RS256 "$alg" "offline token is signed with RS256"

api GET /api/reservations/jwks.json "$CUSTOMER_TOKEN"
assert_code 200 "JWKS endpoint"
assert_json_eq '(.keys | length)' 1 "one public signing key published"
assert_json_eq '.keys[0].kty' RSA "published key is RSA"

wait_check "payer available balance drops to ₹700" 45 projection_balance_is "$payer" 700 || true
wait_check "payer reserved balance is ₹300" 45 projection_reserved_is "$payer" 300 || true

api POST "/api/reservations/$reservation/capture" "$CUSTOMER_TOKEN" \
  "{\"offlineToken\":\"$token\",\"merchantAccountId\":\"$merchant\"}" "p9-capture-1"
assert_code 200 "first capture succeeds"
assert_json_eq '.status' CAPTURED "reservation is CAPTURED"
assert_json_true '.capturedAt != null' "capturedAt recorded"

api POST "/api/reservations/$reservation/capture" "$CUSTOMER_TOKEN" \
  "{\"offlineToken\":\"$token\",\"merchantAccountId\":\"$merchant\"}" "p9-capture-2"
assert_code 409 "replaying the same token is rejected with 409"

wait_check "payer reserved balance returns to ₹0" 45 projection_reserved_is "$payer" 0 || true
wait_check "payer available balance stays ₹700" 45 projection_balance_is "$payer" 700 || true
wait_check "merchant received exactly ₹300" 45 projection_balance_is "$merchant" 300 || true

info "Expiry path: hold released and capture returns 410"
api POST /api/reservations "$CUSTOMER_TOKEN" \
  "{\"accountId\":\"$payer\",\"amount\":200,\"ttlMinutes\":1}" "p9-expire"
assert_code 201 "second reservation created"
expiring=$(jqr .reservationId)
expired_token=$(jqr .offlineToken)

wait_check "expiring hold takes ₹200 from available" 45 projection_reserved_is "$payer" 200 || true
psql_db ledger "UPDATE payment.reservations SET expires_at = now() - interval '5 seconds' WHERE reservation_id='$expiring'" >/dev/null
pass "time-travelled the reservation past its TTL"

api POST "/api/reservations/$expiring/capture" "$CUSTOMER_TOKEN" \
  "{\"offlineToken\":\"$expired_token\",\"merchantAccountId\":\"$merchant\"}" "p9-expire-capture"
assert_code 410 "capture after expiry returns 410 Gone"

wait_check "expired hold released back to ₹700 available" 45 projection_balance_is "$payer" 700 || true
wait_check "expired hold no longer reserved" 45 projection_reserved_is "$payer" 0 || true

api GET "/api/reservations/$expiring" "$CUSTOMER_TOKEN"
assert_json_eq '.status' EXPIRED "reservation is EXPIRED"

api GET "/api/accounts/$payer/events" "$CUSTOMER_TOKEN"
assert_json_eq '[.[] | select(.type == "FundsCaptured")] | length' 1 "FundsCaptured emitted exactly once"
assert_json_true 'map(.type) | index("FundsReserved") != null' "FundsReserved events present"
assert_json_true 'map(.type) | index("FundsReleased") != null' "FundsReleased emitted for expiry"
api GET "/api/accounts/$payer/audit/verify" "$CUSTOMER_TOKEN"
assert_json_true '.valid == true' "payer hash chain valid"

summary
