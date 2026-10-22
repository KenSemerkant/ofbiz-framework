#!/usr/bin/env bash
# CG-3: product write-cutover dataset — import the pre-cutover monolith
# products (MP1..MP50 + the MCAT category) through product-svc's API so
# every write emits an outbox event, the relay publishes it, and
# reverse-sync keeps the monolith current (new-service wins). After this,
# 31-check-product-consistency.sh holds delta 0 on BOTH the mirror and the
# post-cutover truth comparisons. Idempotent — upserts.
set -euo pipefail
NS="ofbiz"

TOOLS="cg3-import-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 900 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1' EXIT

echo "==> [1/2] category"
kubectl -n "${NS}" exec "${TOOLS}" -- curl -s -o /dev/null -w 'category %{http_code}\n' \
  -X POST http://product-svc:8080/api/v1/categories \
  -H 'Content-Type: application/json' \
  -d '{"productCategoryId":"MCAT","categoryName":"Monolith Catalog"}'

echo "==> [2/2] products (MP1..MP50, monolith field values)"
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c '
  OK=0
  g=1
  while [ "${g}" -le 50 ]; do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://product-svc:8080/api/v1/products \
      -H "Content-Type: application/json" \
      -d "{\"productId\":\"MP${g}\",\"productTypeId\":\"FINISHED_GOOD\",\"internalName\":\"Monolith Product ${g}\",\"productName\":\"Product ${g}\",\"categoryIds\":[\"MCAT\"],\"prices\":[{\"productPriceTypeId\":\"DEFAULT_PRICE\",\"currencyUomId\":\"USD\",\"fromDate\":\"2026-01-01T00:00:00Z\",\"price\":\"$((10 + g)).00\"}]}")
    [ "${CODE}" = "201" ] && OK=$((OK + 1))
    g=$((g + 1))
  done
  echo "imported ${OK}/50 products"
  [ "${OK}" = "50" ] || exit 1
'

echo "waiting for reverse-sync convergence (monolith catches up to product-svc)"
for _ in $(seq 1 30); do
  NATIVE=$(kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d product -Atc \
    'SELECT count(*) FROM products;' | tr -d '[:space:]')
  # seed rows carry NULL LAST_UPDATED_STAMP; reverse-sync upserts stamp them
  SYNCED=$(kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
    'SELECT count(*) FROM "PRODUCT" WHERE "LAST_UPDATED_STAMP" IS NOT NULL;' | tr -d '[:space:]')
  [ "${NATIVE}" = "${SYNCED}" ] && [ "${NATIVE}" -ge 50 ] 2>/dev/null && break
  sleep 2
done
echo "product-svc=${NATIVE} monolith(imported)=${SYNCED}"
[ "${NATIVE}" = "${SYNCED}" ] || { echo "FAIL: reverse-sync did not converge"; exit 1; }
echo "IMPORT DONE — delta check: sh platform/cdc/31-check-product-consistency.sh"
