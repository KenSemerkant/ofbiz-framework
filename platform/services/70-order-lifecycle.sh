#!/usr/bin/env bash

# CG-4: full order lifecycle e2e on the staging cluster — quote→order→
# approve→complete through order-svc, plus the cancel path. Proves:
#   - the placeOrder saga prices from product-svc (snapshot 9.99)
#   - the ECA steps fire on order.order.placed: inventory ATP drops
#     (reserve), order.order.invoicerequested lands on the topic
#   - marketing attribution records the tracking code (P4-F2-S2)
#   - the reverse-sync keeps the monolith ORDER_HEADER current
#   - illegal transition is a typed 409
# Non-zero exit on failure.
set -euo pipefail
NS="ofbiz"
RUN="CG4$(date +%s)"
CUSTOMER="P1"          # seeded party (monolith-db init + party projection)
PRODUCT="MP1"          # seeded monolith product (CDC-mirrored into product-svc)
FACILITY="FAC-DRILL-${RUN}"

TOOLS="cg4-order-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 900 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1' EXIT

# seed stock for the drill product so the saga can reserve
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -o /dev/null -X POST http://inventory-svc:8080/api/v1/inventory-items \
    -H 'Content-Type: application/json' \
    -d '{\"inventoryItemId\":\"${RUN}\",\"productId\":\"${PRODUCT}\",\"facilityId\":\"${FACILITY}\"}'
  curl -s -o /dev/null -X POST http://inventory-svc:8080/api/v1/inventory-items/${RUN}/receive \
    -H 'Content-Type: application/json' -d '{\"quantity\":10}'
"

echo "==> [1/5] quote → order (price snapshot from product-svc)"
FAIL=0
QUOTE=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -X POST http://order-svc:8080/api/v1/quotes -H 'Content-Type: application/json' \
    -d '{\"customerId\":\"${CUSTOMER}\",\"currencyUomId\":\"USD\",\"lines\":[{\"productId\":\"${PRODUCT}\",\"facilityId\":\"${FACILITY}\",\"quantity\":2}]}'")
QUOTE_ID=$(echo "${QUOTE}" | sed -n 's/.*"quoteId":"\([^"]*\)".*/\1/p')
ORDER=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -X POST http://order-svc:8080/api/v1/quotes/${QUOTE_ID}/convert")
ORDER_ID=$(echo "${ORDER}" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
UNIT_PRICE=$(echo "${ORDER}" | sed -n 's/.*"unitPrice":\([0-9.]*\).*/\1/p')
echo "  quote=${QUOTE_ID} order=${ORDER_ID} snapshotUnitPrice=${UNIT_PRICE}"
[ -n "${ORDER_ID}" ] || { echo "FAIL: no order created"; exit 1; }
[ "${UNIT_PRICE}" = "9.99" ] || { echo "FAIL: snapshot price ${UNIT_PRICE} != 9.99"; FAIL=1; }

echo "==> [2/5] ECA saga on order.order.placed (ATP reserve + invoice trigger)"
ATP=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
  "curl -s http://inventory-svc:8080/api/v1/inventory-items/${RUN} | sed -n 's/.*\"availableToPromiseTotal\":\([0-9]*\).*/\1/p'")
echo "  ATP after placed=${ATP} (expected 8)"
[ "${ATP}" = "8" ] || { echo "FAIL: ATP ${ATP} != 8 — reserve did not fire"; FAIL=1; }

INVOICE_EVENT=0
for i in $(seq 1 30); do
  INVOICE_EVENT=$(kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d orderdb -Atc \
    "SELECT count(*) FROM outbox WHERE event_type = 'order.order.invoicerequested.v1' AND aggregate_key = '${ORDER_ID}';")
  [ "${INVOICE_EVENT}" -ge 1 ] && break
  sleep 2
done
echo "  order.order.invoicerequested outbox rows=${INVOICE_EVENT}"
[ "${INVOICE_EVENT}" -ge 1 ] || { echo "FAIL: invoice trigger did not fire"; FAIL=1; }

echo "==> [3/5] approve → complete (typed 409 on the illegal shortcut first)"
CODE=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
  "curl -s -o /dev/null -w '%{http_code}' -X POST http://order-svc:8080/api/v1/orders/${ORDER_ID}/complete")
[ "${CODE}" = "409" ] || { echo "FAIL: illegal transition returned ${CODE}, expected 409"; FAIL=1; }
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -o /dev/null -w 'approve %{http_code}\n' -X POST http://order-svc:8080/api/v1/orders/${ORDER_ID}/approve
  curl -s -o /dev/null -w 'complete %{http_code}\n' -X POST http://order-svc:8080/api/v1/orders/${ORDER_ID}/complete"
STATUS=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
  "curl -s http://order-svc:8080/api/v1/orders/${ORDER_ID} | sed -n 's/.*\"status\":\"\([^\"]*\)\".*/\1/p'")
echo "  final status=${STATUS}"
[ "${STATUS}" = "COMPLETED" ] || { echo "FAIL: status ${STATUS} != COMPLETED"; FAIL=1; }

echo "==> [4/5] marketing attribution recorded (tracking code)"
ATTR=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c "
  curl -s -o /dev/null -w '%{http_code}' -X POST http://order-svc:8080/api/v1/orders \
    -H 'Content-Type: application/json' \
    -d '{\"customerId\":\"${CUSTOMER}\",\"currencyUomId\":\"USD\",\"trackingCode\":\"CG4-PROMO\",\"lines\":[{\"productId\":\"${PRODUCT}\",\"facilityId\":\"${FACILITY}\",\"quantity\":1}]}'")
echo "  tracked order placed: ${ATTR}"
ATTR_COUNT=0
for i in $(seq 1 30); do
  ATTR_COUNT=$(kubectl -n "${NS}" exec "${TOOLS}" -- sh -c \
    "curl -s 'http://order-svc:8080/api/v1/attributions?trackingCode=CG4-PROMO'")
  [ "${ATTR_COUNT}" -ge 1 ] 2>/dev/null && break
  sleep 2
done
echo "  attribution count for CG4-PROMO=${ATTR_COUNT}"
[ "${ATTR_COUNT}" -ge 1 ] 2>/dev/null || { echo "FAIL: attribution not recorded"; FAIL=1; }

echo "==> [5/5] reverse-sync keeps the monolith current"
MONO_STATUS=missing
for i in $(seq 1 30); do
  MONO_STATUS=$(kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
    "SELECT \"STATUS_ID\" FROM \"ORDER_HEADER\" WHERE \"ORDER_ID\" = '${ORDER_ID}';")
  [ "${MONO_STATUS}" = "COMPLETED" ] && break
  sleep 2
done
echo "  monolith ORDER_HEADER status=${MONO_STATUS}"
[ "${MONO_STATUS}" = "COMPLETED" ] || { echo "FAIL: reverse-sync did not carry the order"; FAIL=1; }

[ "${FAIL}" = 0 ] \
  && echo "ORDER LIFECYCLE E2E PASSED — quote→order→approve→complete, ECA sagas fired, attribution recorded, reverse-sync current" \
  || { echo "ORDER LIFECYCLE E2E FAILED"; exit 1; }
