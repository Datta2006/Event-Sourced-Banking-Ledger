#!/usr/bin/env bash
# Run every live proof against a running stack. Requires a clean demo state:
#   docker compose down -v && docker compose up -d --build
#
# Set RUN_RECOVERY=0 to skip the destructive kill/restart sub-proof.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

"$HERE/wait-stack.sh" || exit 1

scripts=(
  01-concurrency-idempotency.sh
  02-hash-chain.sh
  03-region-sharding.sh
  04-cqrs-projection.sh
  05-transfer-success.sh
  06-saga-compensation-recovery.sh
  07-loan-priority-pool.sh
  08-loan-repayment.sh
  09-offline-payment.sh
  10-trust-fraud-notifications.sh
)

results=()
failed=0
for script in "${scripts[@]}"; do
  printf '\n============================================================\n'
  printf 'Running %s\n' "$script"
  printf '============================================================\n'
  if bash "$HERE/$script"; then
    results+=("PASS  $script")
  else
    results+=("FAIL  $script")
    failed=$((failed + 1))
  fi
done

printf '\n============================================================\n'
printf 'Demo suite summary\n'
printf '============================================================\n'
for line in "${results[@]}"; do
  printf '%s\n' "$line"
done

if (( failed > 0 )); then
  printf '\n%s proof script(s) failed.\n' "$failed"
  exit 1
fi
printf '\nAll proofs passed.\n'
