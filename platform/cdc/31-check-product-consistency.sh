#!/usr/bin/env bash
# P3-GATE: product-slice consistency checker — delta report (row counts +
# field checksums) between the monolith product slice and product-svc.
#   mirror : monolith vs the CDC projection (pre-cutover mirror; the CDC
#            bridge must reproduce every business field)
#   truth  : monolith vs the native products table (post-cutover; the
#            reverse-sync consumer keeps the monolith current, new-svc wins)
# FROM_DATE/assocs excluded from the fold (wire-form epoch values; assocs
# empty on the seeded slice) — same call as party excluding audit stamps.
# Non-zero exit on drift: 0 = delta 0, 1 = drift, 2 = infrastructure error.
set -euo pipefail
NS="ofbiz"

psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_product()  { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d product -Atc "$1"; }

MONO_COUNT=$(psql_monolith 'SELECT count(*) FROM "PRODUCT";') || exit 2
PROJ_COUNT=$(psql_product 'SELECT count(*) FROM monolith_products;') || exit 2
NATIVE_COUNT=$(psql_product 'SELECT count(*) FROM products;') || exit 2

MONO_SUM=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT p.\"PRODUCT_ID\" || '|' || coalesce(p.\"PRODUCT_TYPE_ID\",'') || '|'
           || coalesce(p.\"INTERNAL_NAME\",'') || '|' || coalesce(p.\"PRODUCT_NAME\",'')
           || '|' || coalesce((SELECT string_agg(m.\"PRODUCT_CATEGORY_ID\", ',' ORDER BY m.\"PRODUCT_CATEGORY_ID\")
                                 FROM \"PRODUCT_CATEGORY_MEMBER\" m WHERE m.\"PRODUCT_ID\" = p.\"PRODUCT_ID\"),'')
           || '|' || coalesce((SELECT string_agg(pr.\"PRODUCT_PRICE_TYPE_ID\" || '/' || pr.\"CURRENCY_UOM_ID\" || '=' || pr.\"PRICE\", ',' ORDER BY pr.\"PRODUCT_PRICE_TYPE_ID\", pr.\"CURRENCY_UOM_ID\")
                                 FROM \"PRODUCT_PRICE\" pr WHERE pr.\"PRODUCT_ID\" = p.\"PRODUCT_ID\"),'')
           AS row_text
    FROM \"PRODUCT\" p
  ) t;") || exit 2
PROJ_SUM=$(psql_product "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT p.product_id || '|' || coalesce(p.product_type_id,'') || '|'
           || coalesce(p.internal_name,'') || '|' || coalesce(p.product_name,'')
           || '|' || coalesce((SELECT string_agg(m.product_category_id, ',' ORDER BY m.product_category_id)
                                 FROM monolith_product_members m WHERE m.product_id = p.product_id),'')
           || '|' || coalesce((SELECT string_agg(pr.product_price_type_id || '/' || pr.currency_uom_id || '=' || pr.price, ',' ORDER BY pr.product_price_type_id, pr.currency_uom_id)
                                 FROM monolith_product_prices pr WHERE pr.product_id = p.product_id),'')
           AS row_text
    FROM monolith_products p
  ) t;") || exit 2
NATIVE_SUM=$(psql_product "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT product_id || '|' || coalesce(product_type_id,'') || '|'
           || coalesce(internal_name,'') || '|' || coalesce(product_name,'')
           AS row_text
    FROM products
  ) t;") || exit 2
NATIVE_CORE_MONO_SUM=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT p.\"PRODUCT_ID\" || '|' || coalesce(p.\"PRODUCT_TYPE_ID\",'') || '|'
           || coalesce(p.\"INTERNAL_NAME\",'') || '|' || coalesce(p.\"PRODUCT_NAME\",'')
           AS row_text
    FROM \"PRODUCT\" p
  ) t;") || exit 2

echo "consistency delta report (P3-GATE product slice)"
echo "  pre-cutover mirror : monolith=${MONO_COUNT} projection=${PROJ_COUNT} checksums ${MONO_SUM:0:12}/${PROJ_SUM:0:12}"
echo "  post-cutover truth : monolith=${MONO_COUNT} product-svc=${NATIVE_COUNT} checksums ${NATIVE_CORE_MONO_SUM:0:12}/${NATIVE_SUM:0:12}"

FAIL=0
if [ "${MONO_COUNT}" != "${PROJ_COUNT}" ] || [ "${MONO_SUM}" != "${PROJ_SUM}" ]; then
  echo "DRIFT (pre-cutover mirror) — delta non-zero"; FAIL=1
fi
if [ "${MONO_COUNT}" != "${NATIVE_COUNT}" ] || [ "${NATIVE_CORE_MONO_SUM}" != "${NATIVE_SUM}" ]; then
  echo "DRIFT (post-cutover product-svc vs monolith) — delta non-zero"; FAIL=1
fi
[ "${FAIL}" = "0" ] && echo "DELTA 0 — CONSISTENT" || { echo "CHECKS FAILED"; exit 1; }
