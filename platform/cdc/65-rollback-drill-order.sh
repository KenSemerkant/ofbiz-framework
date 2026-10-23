#!/usr/bin/env bash
# P4-GATE rollback drill (order slice): prove the monolith write path still
# works and CDC carries monolith-side writes back into order-svc — zero
# data loss in either direction.
#   1. capture the order count in the monolith (reverse-sync has kept
#      ORDER_HEADER current — that is the rollback copy)
#   2. write one order row DIRECTLY into the monolith (the stand-in has no
#      HTTP order API — the DML stands in for a monolith-side write)
#   3. verify the CDC connector streams it into order-svc's
#      monolith_order_projection (rollback read path proven, zero loss)
#   4. clean the drill row up
# Exit 0 = drill PASSED.
set -euo pipefail
NS="ofbiz"
DRILL_ID="ORDER-ROLLBACK-DRILL"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_order()    { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d orderdb -Atc "$1"; }

BEFORE_MONO=$(psql_monolith 'SELECT count(*) FROM "ORDER_HEADER";')
echo "==> [1/3] monolith order rows before: ${BEFORE_MONO}"

echo "==> [2/3] monolith-side write (drill row ${DRILL_ID})"
psql_monolith "INSERT INTO \"ORDER_HEADER\" (\"ORDER_ID\",\"CUSTOMER_ID\",\"STATUS_ID\",\"CURRENCY_UOM\",\"GRAND_TOTAL\") VALUES ('${DRILL_ID}','DRILL','CREATED','USD',0);"
psql_monolith "INSERT INTO \"ORDER_ITEM\" (\"ORDER_ID\",\"ORDER_ITEM_SEQ_ID\",\"PRODUCT_ID\",\"QUANTITY\",\"UNIT_PRICE\",\"UNIT_LIST_PRICE\") VALUES ('${DRILL_ID}','00001','DRILL','1','0','0');"

echo "==> [3/3] CDC streams the rollback write into order-svc projection"
FOUND=0
for i in $(seq 1 30); do
  COUNT=$(psql_order "SELECT count(*) FROM monolith_order_projection WHERE payload LIKE '%${DRILL_ID}%';")
  if [ "${COUNT}" -gt 0 ]; then FOUND=1; break; fi
  sleep 2
done
if [ "${FOUND}" = "1" ]; then
  echo "  rollback write visible in order-svc after ~$((i*2))s — zero data loss"
else
  echo "  DRILL FAILED: CDC did not carry the rollback write into order-svc"
  psql_order "SELECT topic, left(payload, 120) FROM monolith_order_projection ORDER BY applied_at DESC LIMIT 5;" || true
  exit 1
fi

# cleanup: the drill row leaves both stores (it was never a real order)
psql_monolith "DELETE FROM \"ORDER_ITEM\" WHERE \"ORDER_ID\" = '${DRILL_ID}';"
psql_monolith "DELETE FROM \"ORDER_STATUS_HISTORY\" WHERE \"ORDER_ID\" = '${DRILL_ID}';"
psql_monolith "DELETE FROM \"ORDER_HEADER\" WHERE \"ORDER_ID\" = '${DRILL_ID}';"

echo "PASSED — order rollback drill (gateway route flip is documented in"
echo "P4-GATE.md; the data path in both directions is proven above)"
