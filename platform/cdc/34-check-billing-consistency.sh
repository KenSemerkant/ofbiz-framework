#!/usr/bin/env bash
# P5-GATE: billing-slice consistency checker — delta report between the
# monolith INVOICE/PAYMENT tables and billing-svc (P5-F3-S1). Compares the
# MIGRATED dataset (seed 46 + import 47); the lifecycle e2e adds rows only
# to the new stack afterwards.
# Non-zero exit on drift: 0 = delta 0, 1 = drift, 2 = infrastructure error.
set -euo pipefail
NS="ofbiz"
psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_billing()  { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d billing -Atc "$1"; }

MONO_INV_IDS=$(psql_monolith "
  SELECT string_agg(chr(39) || \"INVOICE_ID\" || chr(39), ',') FROM \"INVOICE\";") || exit 2

MONO_INVOICES=$(psql_monolith "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT \"INVOICE_ID\" || '|' || \"PARTY_ID\" || '|'
           || (CASE \"STATUS_ID\" WHEN 'INVOICE_PAID' THEN 'SETTLED'
                                  WHEN 'INVOICE_READY' THEN 'ISSUED'
                                  ELSE \"STATUS_ID\" END)
           || '|' || \"CURRENCY_UOM\" || '|' || \"INVOICE_TOTAL\"::text AS row_text
    FROM \"INVOICE\") t;") || exit 2
SVC_INVOICES=$(psql_billing "
  SELECT coalesce(md5(string_agg(row_text, chr(10) ORDER BY row_text)), 'empty') FROM (
    SELECT invoice_id || '|' || customer_id || '|' || status || '|'
           || currency_uom_id || '|' || total::text AS row_text
    FROM invoices WHERE invoice_id IN (${MONO_INV_IDS})) t;") || exit 2

MONO_PAYMENTS=$(psql_monolith "
  SELECT coalesce(md5(string_agg(\"PAYMENT_ID\" || '|' || \"INVOICE_ID\" || '|' || \"AMOUNT\"::text, chr(10))), 'empty')
    FROM \"PAYMENT\";") || exit 2
SVC_PAYMENTS=$(psql_billing "
  SELECT coalesce(md5(string_agg(payment_id || '|' || invoice_id || '|' || amount::text, chr(10))), 'empty')
    FROM payments WHERE idempotency_key LIKE 'migrated-%';") || exit 2

echo "consistency delta report (P5-GATE billing slice, migrated dataset)"
echo "  invoices : monolith/billing checksums ${MONO_INVOICES:0:12}/${SVC_INVOICES:0:12}"
echo "  payments : monolith/billing checksums ${MONO_PAYMENTS:0:12}/${SVC_PAYMENTS:0:12}"

FAIL=0
[ "${MONO_INVOICES}" = "${SVC_INVOICES}" ] || { echo "  DRIFT: invoice checksums differ"; FAIL=1; }
[ "${MONO_PAYMENTS}" = "${SVC_PAYMENTS}" ] || { echo "  DRIFT: payment checksums differ"; FAIL=1; }
[ "${FAIL}" = "0" ] && echo "  DELTA 0 — billing slice consistent"
exit "${FAIL}"
