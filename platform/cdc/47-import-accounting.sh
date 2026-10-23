#!/usr/bin/env bash
# P5-GATE: migrate the seeded monolith accounting dataset into the new
# services' databases (deterministic SQL import — the dataset both CG-5
# consistency checkers then compare). Idempotent.
set -euo pipefail
NS="ofbiz"
M()   { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
B()   { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d billing -q -c "$1"; }
F()   { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d finance -q -c "$1"; }

echo "==> [1/3] chart of accounts + periods + posting rules -> finance"
F "INSERT INTO gl_accounts (gl_account_id, account_code, account_name, post_balance, gl_account_class_id)
   SELECT \"GL_ACCOUNT_ID\", \"ACCOUNT_CODE\", \"ACCOUNT_NAME\",
          CASE \"GL_ACCOUNT_TYPE_ID\" WHEN 'REVENUE' THEN 'CREDIT' ELSE 'DEBIT' END,
          \"GL_ACCOUNT_TYPE_ID\"
   FROM dblink('host=monolith-db.ofbiz.svc user=ofbiz password=ofbiz dbname=ofbiz',
              'SELECT "GL_ACCOUNT_ID","ACCOUNT_CODE","ACCOUNT_NAME","GL_ACCOUNT_TYPE_ID" FROM "GL_ACCOUNT"') AS t(
              a text, b text, c text, d text)
   ON CONFLICT (gl_account_id) DO NOTHING;" 2>/dev/null || {
  # no dblink in staging postgres: pull rows via kubectl instead
  while IFS='|' read -r id code name type; do
    [ -z "${id}" ] && continue
    F "INSERT INTO gl_accounts VALUES ('${id}','${code}','${name}',
         '$( [ "${type}" = REVENUE ] && echo CREDIT || echo DEBIT)','${type}')
       ON CONFLICT (gl_account_id) DO NOTHING;"
  done < <(M 'SELECT "GL_ACCOUNT_ID"||chr(124)||"ACCOUNT_CODE"||chr(124)||"ACCOUNT_NAME"||chr(124)||"GL_ACCOUNT_TYPE_ID" FROM "GL_ACCOUNT";')
}
F "INSERT INTO periods VALUES ('P-2025','2025-01-01','2025-12-31',true) ON CONFLICT DO NOTHING;"
F "INSERT INTO periods VALUES ('P-2026','2026-01-01','2026-12-31',false) ON CONFLICT DO NOTHING;"
F "INSERT INTO posting_rules VALUES ('R-ISSUE','billing.invoice.issued.v1','1200','4000',true) ON CONFLICT DO NOTHING;"
F "INSERT INTO posting_rules VALUES ('R-SETTLE','billing.invoice.settled.v1','1100','1200',true) ON CONFLICT DO NOTHING;"

echo "==> [2/3] invoices + payments -> billing"
import_invoice() { # id customer status total  (status already mapped)
  B "INSERT INTO invoices (invoice_id, order_id, customer_id, billing_account_id, status,
       lines, history, total, currency_uom_id, created_at)
     VALUES ('${1}', null, '${2}', null, '${3}',
       '[{\"invoiceItemSeqId\":\"1\",\"invoiceItemTypeId\":\"INV_FPROD_ITEM\",\"description\":\"migrated\",\"quantity\":1,\"amount\":${4}}]',
       '[{\"status\":\"DRAFT\",\"changedAt\":\"2026-01-01T00:00:00Z\",\"note\":\"created\"},{\"status\":\"${3}\",\"changedAt\":\"2026-01-02T00:00:00Z\",\"note\":\"migrated\"}]',
       ${4}, 'USD', '2026-01-01T00:00:00Z')
     ON CONFLICT (invoice_id) DO NOTHING;"
}
while IFS='|' read -r id party status total; do
  [ -z "${id}" ] && continue
  case "${status}" in
    INVOICE_PAID)  mapped=SETTLED ;;
    INVOICE_READY) mapped=ISSUED ;;
    *)             mapped="${status}" ;;
  esac
  import_invoice "${id}" "${party}" "${mapped}" "${total}"
done < <(M 'SELECT "INVOICE_ID"||chr(124)||"PARTY_ID"||chr(124)||"STATUS_ID"||chr(124)||"INVOICE_TOTAL"::text FROM "INVOICE";')

import_payment() { # id invoice amount
  B "INSERT INTO payments (payment_id, invoice_id, idempotency_key, amount, currency_uom_id,
       status, gateway_ref, result_code, created_at)
     VALUES ('${1}','${2}','migrated-${1}',${3},'USD','CAPTURED','migrated-${1}','APPROVED','2026-01-02T00:00:00Z')
     ON CONFLICT (payment_id) DO NOTHING;"
}
while IFS='|' read -r id invoice amount; do
  [ -z "${id}" ] && continue
  import_payment "${id}" "${invoice}" "${amount}"
done < <(M 'SELECT "PAYMENT_ID"||chr(124)||"INVOICE_ID"||chr(124)||"AMOUNT"::text FROM "PAYMENT";')

echo "==> [3/3] journal transactions -> finance"
import_trans() { # id type desc period entries-json
  F "INSERT INTO gl_transactions (acctg_trans_id, trans_type, description, period_id,
       entries, posted, posted_at)
     VALUES ('${1}','${2}','${3}','${4}','${5}',true,'2026-01-02T00:00:00Z')
     ON CONFLICT (acctg_trans_id) DO NOTHING;"
}
while IFS='|' read -r id type desc period; do
  [ -z "${id}" ] && continue
  ENTRIES=$(M "SELECT string_agg('{\"glAccountId\":\"' || \"GL_ACCOUNT_ID\" || '\",\"amount\":' || \"AMOUNT\"::text || ',\"debitCredit\":\"' || (CASE \"DEBIT_CREDIT_FLAG\" WHEN 'D' THEN 'DEBIT' ELSE 'CREDIT' END) || '\"}', ',' ORDER BY \"ACCTG_TRANS_ENTRY_SEQ_ID\") FROM \"ACCTG_TRANS_ENTRY\" WHERE \"ACCTG_TRANS_ID\" = '${id}';")
  import_trans "${id}" "${type}" "${desc:-imported}" "${period}" "[${ENTRIES}]"
done < <(M 'SELECT "ACCTG_TRANS_ID"||chr(124)||"ACCTG_TRANS_TYPE_ID"||chr(124)||coalesce("DESCRIPTION",chr(45))||chr(124)||"CUSTOM_TIME_PERIOD_ID" FROM "ACCTG_TRANS" WHERE "IS_POSTED" = chr(89);')

echo "IMPORTED — billing + finance now hold the migrated dataset"
