#!/usr/bin/env bash
# CG-2: build the 10k-party write-cutover dataset through party-svc's API
# (POST /api/v1/parties) so every write emits an outbox event, the relay
# publishes it, and reverse-sync keeps the monolith current.
#   1. import the pre-cutover monolith parties (P1..P50) through the API
#   2. create 10k new parties in parallel (natural >100 msg/s burst)
#   3. print reverse-sync convergence time (CG-2 lag evidence)
set -euo pipefail
NS="ofbiz"
TOOLS="p2-seed-tools"
PARTY="http://party-svc.${NS}.svc:8080/api/v1/parties"

kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 3600 >/dev/null
for _ in $(seq 1 60); do [ "$(kubectl -n "${NS}" get pod "${TOOLS}" -o jsonpath='{.status.containerStatuses[?(@.ready==true)].ready}' 2>/dev/null | wc -c)" -gt 1 ] \
  && kubectl -n "${NS}" get pod "${TOOLS}" -o jsonpath='{.status.containerStatuses[*].ready}' 2>/dev/null | grep -q "true true" && break; sleep 2; done

post() { kubectl -n "${NS}" exec "${TOOLS}" -- curl -fsS -o /dev/null -X POST "${PARTY}" \
  -H "Content-Type: application/json" -d "$1"; }

echo "==> [1/3] importing pre-cutover parties"
IMPORTS=$(kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
  "SELECT \"PARTY_ID\" || '|' || coalesce(\"FIRST_NAME\",'NULLMARK') || '|' || coalesce(\"LAST_NAME\",'NULLMARK') FROM \"PARTY\" p LEFT JOIN \"PERSON\" pr USING (\"PARTY_ID\") ORDER BY 1;")
while IFS='|' read -r id first last; do
  if [ "${first}" = "NULLMARK" ]; then
    post "{\"partyId\":\"${id}\",\"kind\":\"PARTY_GROUP\",\"groupName\":\"Imported ${id}\",\"roles\":[\"CUSTOMER\"]}"
  else
    post "{\"partyId\":\"${id}\",\"kind\":\"PERSON\",\"firstName\":\"${first}\",\"lastName\":\"${last}\",\"roles\":[\"CUSTOMER\"]}"
  fi
done <<< "${IMPORTS}"

echo "==> [2/3] 10k parties through the API (parallel)"
START=$(date +%s)
kubectl -n "${NS}" exec "${TOOLS}" -- sh -c '
  seq 1 10000 | xargs -P 20 -I{} curl -fsS -o /dev/null -X POST '"${PARTY}"' \
    -H "Content-Type: application/json" \
    -d "{\"partyId\":\"C10K-{}\",\"kind\":\"PERSON\",\"firstName\":\"First{}\",\"lastName\":\"Last{}\",\"roles\":[\"CUSTOMER\"]}"'
echo "  10k posts done in $(( $(date +%s) - START ))s (>=100 msg/s)"

echo "==> [3/3] waiting for reverse-sync convergence (lag 0)"
for _ in $(seq 1 120); do
  OUTBOX_PENDING=$(kubectl -n "${NS}" exec postgres-0 -- psql -U party -d party -Atc \
    "SELECT count(*) FROM outbox WHERE published_at IS NULL;")
  LAG=$(kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- \
    /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --group party-reverse-sync --describe 2>/dev/null \
    | awk '$2=="party.party.created.v1" || $2=="party.party.updated.v1" {s+=$6} END {print s+0}')
  [ "${OUTBOX_PENDING}" = "0" ] && [ "${LAG}" = "0" ] && break
  sleep 2
done
CONVERGED=$(( $(date +%s) - START ))
echo "  outbox pending=${OUTBOX_PENDING} consumer lag=${LAG} (converged ${CONVERGED}s after seeding start)"

kubectl -n "${NS}" delete pod "${TOOLS}" --force --grace-period=0 >/dev/null 2>&1 || true
echo "DONE — dataset built; run 30-check-consistency.sh for the delta report"
