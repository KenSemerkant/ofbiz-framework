#!/usr/bin/env bash
# P1-F6-S3: CG-1 gate evidence — the five platform services, live on the
# kind stack: readiness, STRICT mTLS, DLQ topics, single-process OIDC e2e
# through the gateway, search e2e through the gateway, a Jaeger trace, a
# Prometheus series and a log-store hit per service.
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
NS="ofbiz"
PASS=0; FAIL=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS: $1"; PASS=$((PASS+1)); else echo "FAIL: $1 (expected $2, got $3)"; FAIL=$((FAIL+1)); fi
}
# in-cluster probes run through one long-lived tools pod; kubectl exec
# output is exact (kubectl run -i capture proved flaky under capture)
TOOLS_POD="p1f6-verify-tools"
in_cluster() {
  kubectl -n "${NS}" exec "${TOOLS_POD}" -- sh -c "$1"
}
kubectl -n "${NS}" delete pod "${TOOLS_POD}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS_POD}" --image=curlimages/curl:8.11.1 --command -- sleep 1800 >/dev/null
for i in $(seq 1 30); do
  kubectl -n "${NS}" exec "${TOOLS_POD}" -- true >/dev/null 2>&1 && break
  sleep 2
done
trap 'kubectl -n "${NS}" delete pod "${TOOLS_POD}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1' EXIT

echo "==> 1. Deployments Ready"
for d in identity-svc gateway audit-svc notification-svc search-svc party-svc product-svc inventory-svc; do
  if kubectl -n "${NS}" rollout status "deploy/${d}" --timeout=30s >/dev/null 2>&1; then
    check "${d} Ready" ok ok
  else
    check "${d} Ready" ok failed
  fi
done

echo "==> 2. STRICT mTLS still enforced (bootstrap assertions)"
if sh "${ROOT}/platform/bootstrap/10-verify-mtls.sh" 2>&1 | grep -q "ALL CHECKS PASSED"; then
  check "mesh mTLS" ok ok
else
  check "mesh mTLS" ok failed
fi

echo "==> 3. Topics declared (CRs) + functional (search e2e proves the broker side)"
for t in search.doc.indexed.v1 search.doc.indexed.v1.dlq \
  product.product.created.v1 product.product.updated.v1 product.product.deleted.v1 \
  product.price.changed.v1 product.category.updated.v1 \
  inventory.stock.reserved.v1 inventory.stock.confirmed.v1; do
  if kubectl -n "${NS}" get kafkatopic "${t}" >/dev/null 2>&1; then
    check "kafkatopic ${t}" ok ok
  else
    check "kafkatopic ${t}" ok missing
  fi
done

echo "==> 3b. P3 DBs provisioned (product/inventory/search)"
for db in product inventory search; do
  TABLES=$(kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d "${db}" -Atc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" 2>/dev/null \
    | tr -d '[:space:]' || echo 0)
  if [ "${TABLES}" -gt 0 ] 2>/dev/null; then
    check "db ${db} has tables (${TABLES})" ok ok
  else
    check "db ${db} has tables" ok "none"
  fi
done

echo "==> 4. Single-process OIDC e2e: client_credentials -> gateway demo route"
E2E=$(in_cluster '
  TOKEN=$(curl -s -u gateway:dev-local-secret -d "grant_type=client_credentials" \
    http://identity-svc:8080/oauth2/token | sed -n "s/.*\"access_token\":\"\([^\"]*\)\".*/\1/p")
  if [ -z "${TOKEN}" ]; then echo "no-token"; exit 0; fi
  AUTH=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" \
    http://gateway:8080/api/v1/demo/hello?name=e2e)
  ANON=$(curl -s -o /dev/null -w "%{http_code}" http://gateway:8080/api/v1/demo/hello?name=e2e)
  echo "${AUTH} ${ANON}"
' || true)
check "identity token -> gateway 200" 200 "$(echo "${E2E}" | head -1 | cut -d" " -f1 | tr -d '\r')"
check "no token -> gateway 401" 401 "$(echo "${E2E}" | head -1 | cut -d" " -f2 | tr -d '\r')"

echo "==> 5. Search e2e: event (mTLS producer) -> gateway query"
# ship the search-svc KafkaUser PEM into the broker pod for an authenticated produce
TMP=$(mktemp -d)
# truststore carries BOTH CAs (same as 10-deploy's kafka_pem_secret):
# brokers serve cluster-CA certs while KafkaUser secrets ship the clients
# CA — one file validates either chain. With only the clients CA the
# handshake fails PKIX once the broker serves a rotated cluster-CA cert.
kubectl -n "${NS}" get secret ofbiz-cluster-ca-cert -o 'jsonpath={.data.ca\.crt}' | base64 -d > "${TMP}/ca.crt"
kubectl -n "${NS}" get secret search-svc -o 'jsonpath={.data.ca\.crt}' | base64 -d >> "${TMP}/ca.crt"
kubectl -n "${NS}" get secret search-svc -o 'jsonpath={.data.user\.key}' | base64 -d > "${TMP}/user.key"
kubectl -n "${NS}" get secret search-svc -o 'jsonpath={.data.user\.crt}' | base64 -d > "${TMP}/user.crt"
cat "${TMP}/user.key" "${TMP}/user.crt" > "${TMP}/user.pem"
cat > "${TMP}/client.properties" <<'PROP'
security.protocol=SSL
ssl.truststore.type=PEM
ssl.truststore.location=/tmp/kafka-tls/ca.crt
ssl.keystore.type=PEM
ssl.keystore.location=/tmp/kafka-tls/user.pem
PROP
kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- mkdir -p /tmp/kafka-tls >/dev/null 2>&1 || true
kubectl -n "${NS}" cp "${TMP}/ca.crt" ofbiz-ofbiz-nodes-0:/tmp/kafka-tls/ca.crt >/dev/null 2>&1
kubectl -n "${NS}" cp "${TMP}/user.pem" ofbiz-ofbiz-nodes-0:/tmp/kafka-tls/user.pem >/dev/null 2>&1
kubectl -n "${NS}" cp "${TMP}/client.properties" ofbiz-ofbiz-nodes-0:/tmp/kafka-tls/client.properties >/dev/null 2>&1
rm -rf "${TMP}"
echo '{"docId":"gate-e2e-doc","fields":{"name":"gateway laptop e2e"}}' \
  | kubectl -n "${NS}" exec -i ofbiz-ofbiz-nodes-0 -- bin/kafka-console-producer.sh \
    --bootstrap-server ofbiz-kafka-bootstrap.ofbiz.svc:9093 --topic search.doc.indexed.v1 \
    --producer.config /tmp/kafka-tls/client.properties >/dev/null 2>&1 \
  && echo "produced (mTLS, CN=search-svc)"
SEARCH=""
TOKEN=$(in_cluster 'curl -s -u gateway:dev-local-secret -d "grant_type=client_credentials" http://identity-svc:8080/oauth2/token | sed -n "s/.*\"access_token\":\"\([^\"]*\)\".*/\1/p"' || true)
for i in $(seq 1 30); do
  SEARCH=$(in_cluster "curl -s -H 'Authorization: Bearer ${TOKEN}' 'http://gateway:8080/api/v1/search?q=gateway%20laptop&limit=5'" || true)
  if echo "${SEARCH}" | grep -q gate-e2e-doc; then break; fi
  sleep 2
done
if echo "${SEARCH}" | grep -q gate-e2e-doc; then
  check "search hit via gateway" ok ok
else
  check "search hit via gateway" ok "missing: $(echo "${SEARCH}" | head -c 200)"
fi

echo "==> 6. Jaeger trace for gateway requests"
TRACES=$(in_cluster \
  'curl -s "http://jaeger-query.observability:16686/api/traces?service=gateway&limit=5"' || true)
TRACE_COUNT=$(echo "${TRACES}" | grep -o '"traceID"' | wc -l | tr -d " ")
if [ "${TRACE_COUNT}" -gt 0 ] 2>/dev/null; then
  check "jaeger traces for gateway" ok ok
  echo "     evidence: kubectl -n observability port-forward svc/jaeger-query 16686 -> service 'gateway'"
else
  check "jaeger traces for gateway" ok "none (export lag? re-run in 30s)"
fi

echo "==> 7. Prometheus series per service (ofbiz-sidecars job)"
PROM=$(in_cluster \
  'curl -s "http://prometheus.observability:9090/api/v1/query?query=up%7Bjob%3D%22ofbiz-sidecars%22%7D"' || true)
UP_COUNT=$(echo "${PROM}" | grep -o '"value":\[1[0-9.]*,"1"\]' | wc -l | tr -d " ")
if [ "${UP_COUNT}" -ge 5 ]; then
  check "prometheus targets up (${UP_COUNT})" ok ok
else
  check "prometheus targets up" ok "only ${UP_COUNT}: $(echo "${PROM}" | head -c 200)"
fi

echo "==> 8. Log store hit per service (Loki; selector on the pod-log filename path)"
for d in identity-svc gateway audit-svc notification-svc search-svc party-svc product-svc inventory-svc; do
  RES=$(in_cluster "curl -s -G 'http://loki.observability:3100/loki/api/v1/query_range' \
    --data-urlencode 'query={filename=~\".*${d}.*\"}' --data-urlencode 'limit=1'" || true)
  if echo "${RES}" | grep -q '"values":\[\['; then
    check "logs: ${d}" ok ok
  else
    check "logs: ${d}" ok "none: $(echo "${RES}" | head -c 120)"
  fi
done

echo
echo "RESULT: ${PASS} passed, ${FAIL} failed"
if [ "${FAIL}" -eq 0 ]; then echo "ALL CHECKS PASSED (P1-F6-S3, CG-1 evidence)"; fi
exit "${FAIL}"
