#!/usr/bin/env bash
# P2-F2-S4: consistency checker — delta report (row counts + field
# checksums) between the monolith party slice and party-svc, over the
# parties both sides hold. Non-zero exit on drift (CI-gateable).
#
# Usage: sh platform/cdc/30-check-consistency.sh
# Exit:  0 = delta 0, 1 = drift, 2 = infrastructure error
set -euo pipefail
NS="ofbiz"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_party()    { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d party -Atc "$1"; }

MONO_COUNT=$(psql_monolith 'SELECT count(*) FROM "PARTY";') || exit 2
# party-svc table carries both native parties and the CDC projection;
# the projection table is the pre-cutover mirror — compare against it.
PROJ_COUNT=$(psql_party 'SELECT count(*) FROM monolith_parties;') || exit 2

# Field checksum: fold the joined party+person/group rows into one md5 per
# side, order-normalised. Roles aggregated per party and sorted.
MONO_SUM=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT p.\"PARTY_ID\" || '|' || coalesce(p.\"PARTY_TYPE_ID\",'') || '|'
           || coalesce(pr.\"FIRST_NAME\",'') || '|' || coalesce(pr.\"LAST_NAME\",'')
           || '|' || coalesce(pg.\"GROUP_NAME\",'')
           || '|' || coalesce((SELECT string_agg(\"ROLE_TYPE_ID\", ',' ORDER BY \"ROLE_TYPE_ID\")
                                 FROM \"PARTY_ROLE\" r WHERE r.\"PARTY_ID\" = p.\"PARTY_ID\"),'')
           AS row_text
    FROM \"PARTY\" p
    LEFT JOIN \"PERSON\" pr ON pr.\"PARTY_ID\" = p.\"PARTY_ID\"
    LEFT JOIN \"PARTY_GROUP\" pg ON pg.\"PARTY_ID\" = p.\"PARTY_ID\"
  ) t;") || exit 2
PROJ_SUM=$(psql_party "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT party_id || '|' || coalesce(party_type_id,'') || '|'
           || coalesce(first_name,'') || '|' || coalesce(last_name,'')
           || '|' || coalesce(group_name,'')
           || '|' || coalesce((SELECT string_agg(role_type_id, ',' ORDER BY role_type_id)
                                 FROM monolith_party_roles r WHERE r.party_id = m.party_id),'')
           AS row_text
    FROM monolith_parties m
  ) t;") || exit 2

# Post-cutover source of truth: the native parties table, mirrored back to
# the monolith by the reverse-sync consumer (CG-2 write cutover check).
NATIVE_COUNT=$(psql_party 'SELECT count(*) FROM parties;') || exit 2
NATIVE_SUM=$(psql_party "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT p.party_id || '|' || coalesce(p.kind,'') || '|'
           || coalesce(p.first_name,'') || '|' || coalesce(p.last_name,'')
           || '|' || coalesce(p.group_name,'')
           || '|' || coalesce((SELECT string_agg(r.role_type_id, ',' ORDER BY r.role_type_id)
                                 FROM party_roles r WHERE r.party_id = p.party_id),'')
           AS row_text
    FROM parties p
  ) t;") || exit 2

echo "consistency delta report (P2-F2-S4 + CG-2)"
echo "  pre-cutover mirror : monolith=${MONO_COUNT} projection=${PROJ_COUNT} checksums ${MONO_SUM}/${PROJ_SUM}"
echo "  post-cutover truth : monolith=${MONO_COUNT} party-svc=${NATIVE_COUNT} checksums ${MONO_SUM}/${NATIVE_SUM}"

FAIL=0
if [ "${MONO_COUNT}" != "${PROJ_COUNT}" ] || [ "${MONO_SUM}" != "${PROJ_SUM}" ]; then
  echo "DRIFT (pre-cutover mirror) — delta non-zero"; FAIL=1
fi
if [ "${MONO_COUNT}" != "${NATIVE_COUNT}" ] || [ "${MONO_SUM}" != "${NATIVE_SUM}" ]; then
  echo "DRIFT (post-cutover party-svc vs monolith) — delta non-zero"; FAIL=1
fi
[ "${FAIL}" = "0" ] && echo "DELTA 0 — CONSISTENT" || { echo "CHECKS FAILED"; exit 1; }
