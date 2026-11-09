#!/usr/bin/env bash
# Proof 7 — loan priority queue and admin-pool contention.
#   A tier-1 request that cannot be funded waits; a later tier-2 request jumps
#   ahead (tier first, FCFS inside a tier) and is disbursed. Replenishing the
#   pool then lets the waiting request proceed.
#
# The tier cache is a read model fed by trust-score.events. For a deterministic
# demo we seed it with the same SQL row that a TrustScoreChanged would write.
source "$(dirname "$0")/common.sh"

info "Proof 7: loan queue priority and pool contention"
login_all

# Fresh loan demo state: ₹50,000 pool, empty queue.
valkey set admin_pool:balance 50000 >/dev/null
valkey del loan:queue >/dev/null
psql_db ledger "UPDATE loan.admin_pool SET available_balance=50000, version=version+1, updated_at=now()" >/dev/null
pass "reset admin pool to ₹50,000 and cleared the demo queue"

api GET /api/loans/pool "$BANK_TOKEN"
assert_code 200 "bank reads admin pool"
assert_json_eq '(.balance|tonumber)' 50000 "pool starts at ₹50,000"

low_acct=$(new_id)
high_acct=$(new_id)
open_account "$low_acct" 49.37.10.10 0 "Tier One"
assert_code 201 "open tier-1 borrower"
open_account "$high_acct" 85.214.132.1 0 "Tier Two"
assert_code 201 "open tier-2 borrower"

psql_db ledger "INSERT INTO loan.trust_tiers(account_id,tier,score,updated_at) VALUES ('$high_acct',2,75,now()) ON CONFLICT (account_id) DO UPDATE SET tier=2, score=75, updated_at=now()" >/dev/null
pass "seeded tier-2 trust cache for the second borrower"

api POST /api/loans "$CUSTOMER_TOKEN" \
  "{\"accountId\":\"$low_acct\",\"amount\":60000,\"purpose\":\"tier-one\",\"idempotencyKey\":\"p7-low\"}" \
  "p7-low"
assert_code 202 "tier-1 request accepted"
low_loan=$(jqr .loanId)
assert_json_eq '.trustTier' 1 "tier-1 snapshot on the request"

wait_check "tier-1 request waits for funds (₹60,000 > pool)" 30 loan_status_is "$low_loan" WAITING_FOR_FUNDS || true

api POST /api/loans "$CUSTOMER_TOKEN" \
  "{\"accountId\":\"$high_acct\",\"amount\":20000,\"purpose\":\"tier-two\",\"idempotencyKey\":\"p7-high\"}" \
  "p7-high"
assert_code 202 "tier-2 request accepted later"
high_loan=$(jqr .loanId)
assert_json_eq '.trustTier' 2 "tier-2 snapshot on the request"

wait_check "later tier-2 request is disbursed first" 45 loan_status_is "$high_loan" DISBURSED || true
api GET "/api/loans/$low_loan" "$CUSTOMER_TOKEN"
assert_json_eq '.status' WAITING_FOR_FUNDS "tier-1 request is still waiting"
assert_json_true '(.loanId != null)' "tier-1 loan still exists (not rejected)"

api GET /api/loans/pool "$BANK_TOKEN"
assert_json_eq '(.balance|tonumber)' 30000 "pool after the ₹20,000 tier-2 disbursement"

wait_check "tier-2 borrower received ₹20,000" 45 projection_balance_is "$high_acct" 20000 || true

api POST /api/loans/pool/replenish "$BANK_TOKEN" '{"amount":40000}' "p7-replenish"
assert_code 200 "bank replenishes the pool by ₹40,000"
assert_json_eq '(.balance|tonumber)' 70000 "pool after replenishment"

wait_check "waiting tier-1 request is re-checked and disbursed" 45 loan_status_is "$low_loan" DISBURSED || true
api GET /api/loans/pool "$BANK_TOKEN"
assert_json_eq '(.balance|tonumber)' 10000 "pool after the ₹60,000 tier-1 disbursement"

api GET /api/loans/pool/history "$BANK_TOKEN"
assert_code 200 "pool history"
assert_json_true '[.[] | select((.amount|tonumber) == 40000)] | length > 0' "₹40,000 replenishment recorded"

api GET "/api/loans/queue" "$CUSTOMER_TOKEN"
assert_code 403 "customer cannot read the internal queue"
api GET "/api/loans/queue" "$BANK_TOKEN"
assert_code 200 "bank can read the queue"
assert_json_eq '(.entries | length)' 0 "queue drained after both disbursements"

summary
