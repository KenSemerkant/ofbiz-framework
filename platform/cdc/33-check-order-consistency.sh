#!/usr/bin/env bash
# P4-GATE: order-slice consistency checker — delta report (row counts +
# field checksums) between the monolith order tables and order-svc.
#   truth : monolith ORDER_HEADER vs order-svc orders (post-cutover; the
#           reverse-sync consumer keeps the monolith current, new-svc wins)
# Non-zero exit on drift: 0 = delta 0, 1 = drift, 2 = infrastructure error.
set -euo pipefail
NS="ofbiz"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_order()    { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d orderdb -Atc "$1"; }

MONO_COUNT=$(psql_monolith 'SELECT count(*) FROM "ORDER_HEADER";') || exit 2
SVC_COUNT=$(psql_order 'SELECT count(*) FROM orders;') || exit 2

MONO_SUM=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT h.\"ORDER_ID\" || '|' || h.\"CUSTOMER_ID\" || '|' || h.\"STATUS_ID\"
           || '|' || h.\"CURRENCY_UOM\" || '|' || h.\"GRAND_TOTAL\"::text
           || '|' || coalesce((SELECT string_agg(i.\"PRODUCT_ID\" || '/' || i.\"QUANTITY\"
                                 ORDER BY i.\"ORDER_ITEM_SEQ_ID\"), ',')
                                 FROM \"ORDER_ITEM\" i WHERE i.\"ORDER_ID\" = h.\"ORDER_ID\"),'')
           AS row_text
    FROM \"ORDER_HEADER\" h
  ) t;") || exit 2
SVC_SUM=$(psql_order "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT o.order_id || '|' || o.customer_id || '|' || o.status || '|'
           || o.currency_uom_id || '|' || o.grand_total::text
           || '|' || coalesce((SELECT string_agg((line->>'productId') || '/' || (line->>'quantity')
                                 ORDER BY line->>'orderItemSeqId'), ',')
                                 FROM jsonb_array_elements(o.lines::jsonb) line), '')
           AS row_text
    FROM orders o
  ) t;") || exit 2

echo "consistency delta report (P4-GATE order slice)"
echo "  post-cutover truth : monolith=${MONO_COUNT} order-svc=${SVC_COUNT} checksums ${MONO_SUM:0:12}/${SVC_SUM:0:12}"

FAIL=0
if [ "${MONO_COUNT}" != "${SVC_COUNT}" ] || [ "${MONO_SUM}" != "${SVC_SUM}" ]; then
  echo "DRIFT (order-svc vs monolith) — delta non-zero"; FAIL=1
fi
[ "${FAIL}" = "0" ] && echo "DELTA 0 — CONSISTENT" || { echo "CHECKS FAILED"; exit 1; }
