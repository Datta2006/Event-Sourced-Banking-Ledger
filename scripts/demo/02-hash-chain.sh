#!/usr/bin/env bash
# Proof 2 — tamper-evident per-aggregate hash chain.
source "$(dirname "$0")/common.sh"

info "Proof 2: SHA-256 hash chain detects a tampered event"
login_all

account=$(new_id)
open_account "$account" 85.214.132.1 500 "Audit Demo"
assert_code 201 "open account"
region=$(jqr .region)
assert_eq "EU" "$region" "registration IP resolved to EU"
shard="ddbs_$(printf '%s' "$region" | tr '[:upper:]' '[:lower:]')"

api POST "/api/accounts/$account/deposits" "$CUSTOMER_TOKEN" \
  '{"amount":100,"deviceId":"proof2","idempotencyKey":"p2-deposit"}' "p2-deposit"
assert_code 201 "deposit ₹100"

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_code 200 "verify audit chain"
assert_json_eq '.valid' true "chain verifies before tampering"
assert_json_eq '.eventCount' 3 "three chained events"

original=$(psql_db "$shard" "SELECT hash FROM ledger_events WHERE aggregate_id='$account' AND version=1")
[[ -n "$original" ]] || die "could not read event hash from $shard"
pass "read version-1 hash from physical shard $shard"

psql_db "$shard" "UPDATE ledger_events SET hash='0000000000000000000000000000000000000000000000000000000000000000' WHERE aggregate_id='$account' AND version=1" >/dev/null
pass "tampered version-1 hash directly in Postgres"

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_code 200 "verify audit chain after tampering"
assert_json_eq '.valid' false "chain reports invalid"
assert_json_eq '.brokenAtVersion' 1 "failure localised to version 1"

psql_db "$shard" "UPDATE ledger_events SET hash='$original' WHERE aggregate_id='$account' AND version=1" >/dev/null
pass "restored the original hash"

api GET "/api/accounts/$account/audit/verify" "$CUSTOMER_TOKEN"
assert_json_eq '.valid' true "chain verifies again after restore"

summary
