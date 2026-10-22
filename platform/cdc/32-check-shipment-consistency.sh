#!/usr/bin/env bash
# P3-GATE: shipment-slice consistency checker — delta report (row counts +
# field checksums) between the monolith shipment slice and the inventory-svc
# CDC projection. Read-mostly slice pre-P4: no reverse-sync (documented
# deviation in issues/P3-GATE.md) — the projection is the mirror; the
# native shipments table is the P4 write model and out of scope here.
# Non-zero exit on drift: 0 = delta 0, 1 = drift, 2 = infrastructure error.
set -euo pipefail
NS="ofbiz"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_inventory() { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d inventory -Atc "$1"; }

MONO_COUNT=$(psql_monolith 'SELECT count(*) FROM "SHIPMENT";') || exit 2
PROJ_COUNT=$(psql_inventory 'SELECT count(*) FROM monolith_shipments;') || exit 2

MONO_SUM=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT s.\"SHIPMENT_ID\" || '|' || coalesce(s.\"STATUS_ID\",'')
           || '|' || coalesce((SELECT string_agg(i.\"SHIPMENT_ITEM_SEQ_ID\" || '/' || coalesce(i.\"PRODUCT_ID\",'') || '/' || coalesce(i.\"QUANTITY\",''), ',' ORDER BY i.\"SHIPMENT_ITEM_SEQ_ID\")
                                 FROM \"SHIPMENT_ITEM\" i WHERE i.\"SHIPMENT_ID\" = s.\"SHIPMENT_ID\"),'')
           AS row_text
    FROM \"SHIPMENT\" s
  ) t;") || exit 2
PROJ_SUM=$(psql_inventory "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT s.shipment_id || '|' || coalesce(s.status_id,'')
           || '|' || coalesce((SELECT string_agg(i.shipment_item_seq_id || '/' || coalesce(i.product_id,'') || '/' || coalesce(i.quantity,''), ',' ORDER BY i.shipment_item_seq_id)
                                 FROM monolith_shipment_items i WHERE i.shipment_id = s.shipment_id),'')
           AS row_text
    FROM monolith_shipments s
  ) t;") || exit 2

echo "consistency delta report (P3-GATE shipment slice)"
echo "  mirror : monolith=${MONO_COUNT} projection=${PROJ_COUNT} checksums ${MONO_SUM:0:12}/${PROJ_SUM:0:12}"

FAIL=0
if [ "${MONO_COUNT}" != "${PROJ_COUNT}" ] || [ "${MONO_SUM}" != "${PROJ_SUM}" ]; then
  echo "DRIFT (shipment mirror) — delta non-zero"; FAIL=1
fi
[ "${FAIL}" = "0" ] && echo "DELTA 0 — CONSISTENT" || { echo "CHECKS FAILED"; exit 1; }
