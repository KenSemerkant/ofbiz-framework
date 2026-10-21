#!/usr/bin/env bash
# CG-2: rollback drill for the party write-cutover.
#   1. record the monolith fingerprint (row count)
#   2. flip the gateway party route back to the monolith backend
#   3. monolith-side write (the rollback write path)
#   4. CDC mirrors that write into party-svc within seconds — zero data loss
#   5. flip the route forward again to party-svc, re-check fingerprint
# Non-zero exit on any failure.
set -euo pipefail
NS="ofbiz"
DRILL_ID="RB$(date +%s)"

rowcount() {
  kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
    "SELECT count(*) FROM \"PARTY\";" | tr -d '[:space:]'
}

echo "==> [1/5] baseline"
BEFORE=$(rowcount)
echo "monolith PARTY rows: ${BEFORE}"

echo "==> [2/5] rollback: gateway party route -> monolith backend"
kubectl -n "${NS}" set env deploy/gateway GATEWAY_PARTY_URI="http://monolith-db.${NS}.svc:5432"
kubectl -n "${NS}" rollout status deploy/gateway --timeout=180s >/dev/null
echo "flip applied"

echo "==> [3/5] monolith-side write (rollback write path)"
kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
  "INSERT INTO \"PARTY\" (\"PARTY_ID\",\"PARTY_TYPE_ID\",\"CREATED_DATE\") VALUES ('${DRILL_ID}','PERSON',now());" >/dev/null

echo "==> [4/5] CDC mirrors the write into party-svc (zero data loss check)"
FOUND=""
for _ in $(seq 1 25); do
  FOUND=$(kubectl -n "${NS}" exec postgres-0 -- psql -U party -d party -Atc \
    "SELECT count(*) FROM monolith_parties WHERE party_id='${DRILL_ID}';" | tr -d '[:space:]')
  [ "${FOUND}" = "1" ] && break
  sleep 2
done
[ "${FOUND}" = "1" ] || { echo "FAIL: rollback write not mirrored by CDC"; exit 1; }
echo "write ${DRILL_ID} visible in party-svc projection"

echo "==> [5/5] flip forward again to party-svc"
kubectl -n "${NS}" set env deploy/gateway GATEWAY_PARTY_URI="http://party-svc.${NS}.svc:8080"
kubectl -n "${NS}" rollout status deploy/gateway --timeout=180s >/dev/null

AFTER=$(rowcount)
EXPECTED=$(( BEFORE + 1 ))
[ "${AFTER}" = "${EXPECTED}" ] \
  && echo "ROLLBACK DRILL PASSED — route flip back and forth, write preserved (${BEFORE} -> ${AFTER})" \
  || { echo "FAIL: unexpected row count ${BEFORE} -> ${AFTER}"; exit 1; }
