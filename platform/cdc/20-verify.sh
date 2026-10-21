#!/usr/bin/env bash
# P2-F2-S1 gate: snapshot completes on the party slice, and insert/update/
# delete on the monolith party table surface as ofbiz.PARTY events within 5s.
# KNOWN ISSUE (P2-F2-S1): values are JSON with embedded schemas (Avro deferred,
# see platform/cdc/kafka-connect.yaml header); flow is proven by offsets.
# Exits non-zero on any failure.
set -euo pipefail
NS="ofbiz"
FAIL=0
BROKER_POD=ofbiz-ofbiz-nodes-0

echo "==> connector RUNNING?"
STATUS_JSON=$(kubectl -n "${NS}" exec deploy/debezium-connect -- \
  curl -fsS http://localhost:8083/connectors/monolith-party/status)
echo "${STATUS_JSON}" | grep -q '"state":"RUNNING"' \
  || { echo "FAIL: connector not RUNNING: ${STATUS_JSON}"; exit 1; }
echo "connector RUNNING"

end_offset() { # topic
  kubectl -n "${NS}" exec "${BROKER_POD}" -- \
    /opt/kafka/bin/kafka-get-offsets.sh \
     --bootstrap-server localhost:9092 --topic "$1" --time -1 2>/dev/null \
    | awk -F: '{s+=$3} END {print s+0}'
}

echo "==> snapshot carried pre-existing rows? (ofbiz.PARTY >= 50 events)"
SNAP=$(end_offset ofbiz.PARTY)
echo "ofbiz.PARTY events: ${SNAP}"
[ "${SNAP}" -ge 50 ] || { echo "FAIL: snapshot incomplete"; FAIL=1; }

echo "==> insert/update/delete within 5s?"
UNIQUE="V$(date +%s)"
BEFORE=$(end_offset ofbiz.PARTY)
kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
  "INSERT INTO \"PARTY\" (\"PARTY_ID\",\"PARTY_TYPE_ID\",\"CREATED_DATE\") VALUES ('${UNIQUE}','PERSON',now());"
AFTER_INS=$(end_offset ofbiz.PARTY)
[ "$(( AFTER_INS - BEFORE ))" -ge 1 ] || { echo "FAIL: insert not streamed"; FAIL=1; }

kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
  "UPDATE \"PARTY\" SET \"DESCRIPTION\"='cdc-verify' WHERE \"PARTY_ID\"='${UNIQUE}';"
AFTER_UPD=$(end_offset ofbiz.PARTY)
[ "$(( AFTER_UPD - AFTER_INS ))" -ge 1 ] || { echo "FAIL: update not streamed"; FAIL=1; }

kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc \
  "DELETE FROM \"PARTY\" WHERE \"PARTY_ID\"='${UNIQUE}';"
AFTER_DEL=$(end_offset ofbiz.PARTY)
[ "$(( AFTER_DEL - AFTER_UPD ))" -ge 1 ] || { echo "FAIL: delete not streamed"; FAIL=1; }

[ "${FAIL}" = "0" ] && echo "ALL CHECKS PASSED (P2-F2-S1)" || { echo "CHECKS FAILED"; exit 1; }
