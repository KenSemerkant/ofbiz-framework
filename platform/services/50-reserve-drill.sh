#!/usr/bin/env bash

# CG-3: oversell drill — seed one inventory item with ATP=50 through the
# API, fire 100 PARALLEL reserves of qty 1, and prove exactly-0 oversell:
# successes <= 50, final ATP = 50 - successes (>= 0), every loss is a 409,
# and cancel compensates by releasing ATP back. Non-zero exit on failure.
set -euo pipefail
NS="ofbiz"
DRILL="OS$(date +%s)"
PRODUCT="PRD-DRILL-${DRILL}"
FACILITY="FAC-DRILL-${DRILL}"

TOOLS="cg3-reserve-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 900 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1' EXIT

echo "==> [1/4] seed item ${DRILL} (QOH=ATP=50) via inventory-svc API"
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -o /dev/null -w 'create %{http_code}\n' -X POST http://inventory-svc:8080/api/v1/inventory-items \
    -H 'Content-Type: application/json' \
    -d '{\"inventoryItemId\":\"${DRILL}\",\"productId\":\"${PRODUCT}\",\"facilityId\":\"${FACILITY}\"}'
  curl -s -o /dev/null -w 'receive %{http_code}\n' -X POST http://inventory-svc:8080/api/v1/inventory-items/${DRILL}/receive \
    -H 'Content-Type: application/json' -d '{\"quantity\":50}'
"

echo "==> [2/4] 100 parallel reserves (qty 1 each)"
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  i=0
  while [ \$((i+=1)) -le 100 ]; do
    curl -s -o /tmp/resp_\$i -w '%{http_code}\n' -X POST http://inventory-svc:8080/api/v1/reservations \
      -H 'Content-Type: application/json' \
      -d '{\"orderId\":\"${DRILL}\",\"orderItemSeqId\":\"00001\",\"productId\":\"${PRODUCT}\",\"facilityId\":\"${FACILITY}\",\"quantity\":1}' \
      > /tmp/code_\$i &
  done
  wait
  echo \"created: \$(cat /tmp/code_* | grep -c 201)\"
  echo \"conflict: \$(cat /tmp/code_* | grep -c 409)\"
  grep -l '\"status\"' /tmp/resp_* 2>/dev/null | head -1 | xargs cat > /tmp/one_reservation.json || true
"
CREATED=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "cat /tmp/code_* | grep -c '^201' || true")
CONFLICT=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "cat /tmp/code_* | grep -c '^409' || true")
echo "successes=${CREATED} conflicts=${CONFLICT}"

echo "==> [3/4] oversell assertions"
ATP=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
  "curl -s http://inventory-svc:8080/api/v1/inventory-items/${DRILL} | sed -n 's/.*\"availableToPromiseTotal\":\([0-9]*\).*/\1/p'")
echo "final ATP=${ATP} (expected $((50 - CREATED)))"
FAIL=0
[ "${CREATED}" -le 50 ] || { echo "FAIL: ${CREATED} successes > 50 — oversold"; FAIL=1; }
[ "${ATP}" -ge 0 ] || { echo "FAIL: ATP ${ATP} < 0 — oversold"; FAIL=1; }
[ "${ATP}" = "$((50 - CREATED))" ] || { echo "FAIL: ATP ${ATP} inconsistent with ${CREATED} successes"; FAIL=1; }
[ "$((CREATED + CONFLICT))" = 100 ] || { echo "FAIL: ${CREATED}+${CONFLICT} != 100 (non-409 losses?)"; FAIL=1; }

echo "==> [4/4] cancel compensates (release check)"
RESERVATION=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
  "sed -n 's/.*\"reservationId\":\"\([^\"]*\)\".*/\1/p' /tmp/one_reservation.json | head -1")
if [ -n "${RESERVATION}" ]; then
  kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
    "curl -s -o /dev/null -w 'cancel %{http_code}\n' -X POST http://inventory-svc:8080/api/v1/reservations/${RESERVATION}/cancel"
  ATP2=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
    "curl -s http://inventory-svc:8080/api/v1/inventory-items/${DRILL} | sed -n 's/.*\"availableToPromiseTotal\":\([0-9]*\).*/\1/p'")
  echo "ATP after cancel=${ATP2} (expected $((ATP + 1)))"
  [ "${ATP2}" = "$((ATP + 1))" ] || { echo "FAIL: cancel did not release ATP"; FAIL=1; }
else
  echo "FAIL: no reservation body captured for the cancel check"; FAIL=1
fi

[ "${FAIL}" = 0 ] \
  && echo "RESERVE DRILL PASSED — ${CREATED} reserves, 0 over-reserved, cancel compensates" \
  || { echo "RESERVE DRILL FAILED"; exit 1; }
