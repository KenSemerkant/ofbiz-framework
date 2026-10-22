#!/usr/bin/env bash

# CG-3: search evidence — (a) live path: a product created through
# product-svc's API is searchable through search-svc within 5s (outbox ->
# relay -> product.product.created.v1 -> indexer), (b) p99 query latency
# over 200 queries < 100ms with 100k docs in the products index.
#
# The 100k corpus is bulk-loaded straight into OpenSearch (benchmark load,
# not the live path — the live path is proven separately in step 1). Docs
# use exactly the versioned ProductIndexMappings fields.
# Non-zero exit on any failure.
set -euo pipefail
NS="ofbiz"
DOCS="${1:-100000}"
LIVE_ID="LIVE-$(date +%s)"

SCRIPT=$(mktemp)
cat > "${SCRIPT}" <<'EOF'
#!/bin/sh
set -e
OS=http://opensearch.observability.svc:9200
PRODUCT=http://product-svc:8080
SEARCH=http://search-svc:8080
DOCS=$1
LIVE_ID=$2

echo "==> [1/3] live path: create product ${LIVE_ID}, poll for searchability"
T0=$(date +%s)
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST ${PRODUCT}/api/v1/products \
  -H 'Content-Type: application/json' -d "{\"productId\":\"${LIVE_ID}\",\"productTypeId\":\"FINISHED_GOOD\",\"internalName\":\"Live Path Probe\",\"productName\":\"LiveProbe ${LIVE_ID}\",\"prices\":[{\"productPriceTypeId\":\"DEFAULT_PRICE\",\"currencyUomId\":\"USD\",\"fromDate\":\"2026-01-01T00:00:00Z\",\"price\":9.99}]}")
[ "${CODE}" = "201" ] || { echo "FAIL: product create returned ${CODE}"; exit 1; }
FOUND=""
for i in $(seq 1 25); do
  FOUND=$(curl -s "${SEARCH}/api/v1/search?q=LiveProbe%20${LIVE_ID}&index=products&limit=1")
  echo "${FOUND}" | grep -q "${LIVE_ID}" && break
  sleep 1
done
T1=$(date +%s)
if echo "${FOUND}" | grep -q "${LIVE_ID}"; then
  echo "live path OK: searchable in $((T1 - T0))s (event -> relay -> index -> query)"
else
  echo "FAIL: ${LIVE_ID} not searchable after $((T1 - T0))s: $(echo "${FOUND}" | head -c 200)"; exit 1
fi
[ $((T1 - T0)) -lt 5 ] || { echo "FAIL: live path took $((T1 - T0))s (>= 5s)"; exit 1; }

echo "==> [2/3] bulk-load ${DOCS} docs into the products index"
BATCH=2000
LOADED=0
START=1
while [ "${LOADED}" -lt "${DOCS}" ]; do
  END=$((START + BATCH - 1))
  [ "${END}" -gt "${DOCS}" ] && END=${DOCS}
  awk -v s="${START}" -v e="${END}" 'BEGIN {
    q = sprintf("%c", 34);  # double-quote — no escaped quotes through sh layers
    for (i = s; i <= e; i++) {
      print "{" q "index" q ":{" q "_index" q ":" q "products" q "," q "_id" q ":" q "BENCH-" i q "}}";
      print "{" q "schemaVersion" q ":" q "1" q "," q "productId" q ":" q "BENCH-" i q "," q "productTypeId" q ":" q "FINISHED_GOOD" q "," q "internalName" q ":" q "Benchmark Widget " i q "," q "productName" q ":" q "Widget " i q "," q "categoryIds" q ":" q "BENCH" q "," q "categoryNames" q ":" q "Benchmark" q "," q "defaultPrice" q ":" (10 + i % 90) ".5," q "defaultPriceType" q ":" q "DEFAULT_PRICE" q "," q "defaultPriceCurrency" q ":" q "USD" q "," q "updatedAt" q ":" q "2026-01-01T00:00:00Z" q "}";
    }
  }' > /tmp/bulk.ndjson
  RES=$(curl -s -X POST "${OS}/_bulk" -H 'Content-Type: application/x-ndjson' --data-binary @/tmp/bulk.ndjson)
  echo "${RES}" | grep -q '"errors":true' && { echo "FAIL: bulk errors: $(echo "${RES}" | head -c 300)"; exit 1; }
  LOADED=$((END))
  START=$((END + 1))
done
curl -s -X POST "${OS}/products/_refresh" > /dev/null
COUNT=$(curl -s "${OS}/products/_count" | sed -n 's/.*"count":\([0-9]*\).*/\1/p')
echo "products index holds ${COUNT} docs"
[ "${COUNT}" -ge "${DOCS}" ] || { echo "FAIL: expected >= ${DOCS} docs, got ${COUNT}"; exit 1; }

echo "==> [3/3] p99 over 200 queries"
rm -f /tmp/times
i=0
while [ $((i += 1)) -le 200 ]; do
  Q="Widget $((RANDOM % 50000))"
  curl -s -o /dev/null -w '%{time_total}\n' \
    "${SEARCH}/api/v1/search?q=$(echo ${Q} | sed 's/ /%20/g')&index=products&limit=5" >> /tmp/times
done
P99=$(sort -n /tmp/times | awk 'NR==198{printf "%.0f", $1*1000}')
MEDIAN=$(sort -n /tmp/times | awk 'NR==100{printf "%.0f", $1*1000}')
echo "query p50=${MEDIAN}ms p99=${P99}ms (200 queries, ${COUNT} docs)"
[ "${P99}" -lt 100 ] || { echo "FAIL: p99 ${P99}ms >= 100ms"; exit 1; }
echo "SEARCH BENCHMARK PASSED — live path < 5s, p99 ${P99}ms @ ${COUNT} docs"
EOF

TOOLS="cg3-search-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 3600 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1; rm -f "${SCRIPT}"' EXIT
kubectl -n "${NS}" cp "${SCRIPT}" "${TOOLS}:/tmp/bench.sh"
kubectl -n "${NS}" exec "${TOOLS}" -- sh /tmp/bench.sh "${DOCS}" "${LIVE_ID}"
