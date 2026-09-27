#!/usr/bin/env bash
# Regenerates frontend/src/api/specs/*.ts from docs/api_spec/*.yaml, then the
# aggregate frontend/src/api/contract.ts (hand-written aggregator, stable).
# Run from anywhere: frontend/scripts/gen-contract.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/frontend"
mkdir -p src/api/specs
for f in "$ROOT"/docs/api_spec/*.yaml; do
  name="$(basename "$f" .yaml)"
  npx openapi-typescript "$f" -o "src/api/specs/${name}.ts"
done
echo "Contract regenerated. src/api/contract.ts re-exports these via ./specs/*."
