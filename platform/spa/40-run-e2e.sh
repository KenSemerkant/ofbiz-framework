#!/usr/bin/env bash
# P8: run the Playwright suite against the deployed kind cluster.
# Manages port-forwards; prints per-journey PASS summary. Non-zero on failure.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"
PIDS=()

cleanup() { kill "${PIDS[@]}" 2>/dev/null || true; }
trap cleanup EXIT

kubectl -n "${NS}" port-forward svc/gateway 8080:8080 >/dev/null 2>&1 & PIDS+=($!)
kubectl -n "${NS}" port-forward svc/spa 4200:4200 >/dev/null 2>&1 & PIDS+=($!)
for _ in $(seq 1 15); do
  curl -s -o /dev/null localhost:4200 || true
  curl -s -o /dev/null localhost:8080 || true
  if curl -s -o /dev/null localhost:4200 && curl -s -o /dev/null localhost:8080; then break; fi
  sleep 1
done

cd "${SCRIPT_DIR}/../../tests/e2e"
pnpm exec playwright test --reporter=dot 2>&1 | tee /tmp/p8-e2e.log
echo
echo "== P8 E2E summary =="
grep -oE '[0-9]+ passed' /tmp/p8-e2e.log | tail -1
if grep -qE '[0-9]+ failed' /tmp/p8-e2e.log; then echo "FAILURES PRESENT"; exit 1; fi
echo "P8 E2E EVIDENCE PASSED ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
