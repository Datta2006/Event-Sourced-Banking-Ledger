#!/usr/bin/env bash
# Proof 3 — registration-IP region routing to three physical Postgres shards.
source "$(dirname "$0")/common.sh"

info "Proof 3: hybrid fragmentation — account rows land on the regional shard"
login_all

apac=$(new_id)
eu=$(new_id)
amer=$(new_id)

open_account "$apac" 49.37.10.10 100 "APAC Customer"
assert_code 201 "open APAC account"
assert_json_eq '.region' APAC "49.37.10.10 → APAC"

open_account "$eu" 85.214.132.1 100 "EU Customer"
assert_code 201 "open EU account"
assert_json_eq '.region' EU "85.214.132.1 → EU"

open_account "$amer" 8.8.8.8 100 "AMER Customer"
assert_code 201 "open AMER account"
assert_json_eq '.region' AMER "8.8.8.8 → AMER"

for pair in "APAC:$apac" "EU:$eu" "AMER:$amer"; do
  region=${pair%%:*}
  id=${pair##*:}
  db="ddbs_$(printf '%s' "$region" | tr '[:upper:]' '[:lower:]')"
  here=$(psql_db "$db" "SELECT count(*) FROM account_core WHERE account_id='$id'")
  assert_eq 1 "$here" "$region account exists in $db.account_core"

  for other in ddbs_apac ddbs_eu ddbs_amer; do
    [[ "$other" == "$db" ]] && continue
    elsewhere=$(psql_db "$other" "SELECT count(*) FROM account_core WHERE account_id='$id'")
    assert_eq 0 "$elsewhere" "$region account absent from $other"
  done

  events=$(psql_db "$db" "SELECT count(*) FROM ledger_events WHERE aggregate_id='$id'")
  assert_eq 2 "$events" "$region event stream stored in $db"
done

api GET "/api/ops/shards" "$BANK_TOKEN"
assert_code 200 "bank can read the shard map"
assert_json_eq 'length' 3 "three regional shards reported"
assert_json_true 'all(.status == "UP")' "all shards UP"
assert_json_true '[.[].rowCounts.account_core] | all(. > 0)' "ops map sees shard row counts"

api GET "/api/ops/shards" "$CUSTOMER_TOKEN"
assert_code 403 "customer cannot read ops endpoints"

summary
