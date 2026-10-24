#!/usr/bin/env bash
# P6-GATE rollback drill (long-tail slices): prove the monolith write path
# still works for every P6 domain and the CDC connector streams monolith-
# side writes onto the ofbiz.<TABLE> topics — the pipe a rollback (gateway
# route flip documented in P6-GATE.md) would rely on. Zero data loss in
# the rollback direction. Exit 0 = drill PASSED.
set -euo pipefail
NS="ofbiz"
BROKER_POD=ofbiz-ofbiz-nodes-0
DRILL_ID="P6-ROLLBACK-DRILL"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
end_offset() { # topic
  kubectl -n "${NS}" exec "${BROKER_POD}" -- \
    /opt/kafka/bin/kafka-get-offsets.sh \
     --bootstrap-server localhost:9092 --topic "$1" --time -1 2>/dev/null \
    | awk -F: '{s+=$3} END {print s+0}'
}

echo "==> connector RUNNING?"
STATUS_JSON=$(kubectl -n "${NS}" exec deploy/debezium-connect -- \
  curl -fsS http://localhost:8083/connectors/monolith-p6/status)
echo "${STATUS_JSON}" | grep -q '"state":"RUNNING"' \
  || { echo "FAIL: connector not RUNNING: ${STATUS_JSON}"; exit 1; }

drill() { # label table topic sql
  local label="$1" table="$2" topic="$3" sql="$4"
  local before after
  before=$(end_offset "${topic}")
  psql_monolith "${sql}" >/dev/null
  after=$(end_offset "${topic}")
  [ "$(( after - before ))" -ge 1 ] \
    && echo "  ${label}: monolith write -> ${topic} streamed (offset ${before} -> ${after})" \
    || { echo "  FAIL: ${label} write not streamed to ${topic}"; return 1; }
}

FAIL=0
echo "==> monolith-side writes stream to ofbiz.* topics (rollback read path)"
drill manufacturing PRODUCT_BOM ofbiz.PRODUCT_BOM \
  "INSERT INTO \"PRODUCT_BOM\" VALUES ('${DRILL_ID}','${DRILL_ID}-C','1','0');" || FAIL=1
drill workeffort WORK_EFFORT ofbiz.WORK_EFFORT \
  "INSERT INTO \"WORK_EFFORT\" VALUES ('${DRILL_ID}','TASK','rollback drill',NULL,'CREATED',NULL,NULL);" || FAIL=1
drill humanres EMPL_POSITION ofbiz.EMPL_POSITION \
  "INSERT INTO \"EMPL_POSITION\" VALUES ('${DRILL_ID}','ENGINEER',NULL,'ACTIVE');" || FAIL=1
drill content CONTENT ofbiz.CONTENT \
  "INSERT INTO \"CONTENT\" VALUES ('${DRILL_ID}','DOCUMENT','rollback drill',NULL,'DRAFT',NULL,NULL,NULL);" || FAIL=1
drill marketing MARKETING_CAMPAIGN ofbiz.MARKETING_CAMPAIGN \
  "INSERT INTO \"MARKETING_CAMPAIGN\" VALUES ('${DRILL_ID}','rollback drill','PLANNED','2026-10-01','2026-10-31');" || FAIL=1

echo "==> cleanup drill rows"
psql_monolith "DELETE FROM \"PRODUCT_BOM\" WHERE \"PRODUCT_ID\"='${DRILL_ID}';" >/dev/null
psql_monolith "DELETE FROM \"WORK_EFFORT\" WHERE \"WORK_EFFORT_ID\"='${DRILL_ID}';" >/dev/null
psql_monolith "DELETE FROM \"EMPL_POSITION\" WHERE \"EMPL_POSITION_ID\"='${DRILL_ID}';" >/dev/null
psql_monolith "DELETE FROM \"CONTENT\" WHERE \"CONTENT_ID\"='${DRILL_ID}';" >/dev/null
psql_monolith "DELETE FROM \"MARKETING_CAMPAIGN\" WHERE \"MARKETING_CAMPAIGN_ID\"='${DRILL_ID}';" >/dev/null

[ "${FAIL}" = "0" ] && echo "PASSED — P6 rollback drill (all five domains, both-direction data path proven; gateway route flip documented in P6-GATE.md)" || exit 1
