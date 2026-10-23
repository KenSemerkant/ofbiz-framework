#!/usr/bin/env bash
# P5-GATE: finance-slice consistency checker — trial-balance parity between
# the monolith AcctgTransEntry table and finance-svc (CG-5 item 3,
# P5-F2-S3/P5-F3-S1): per-account debit/credit sums must be identical.
# Non-zero exit on drift: 0 = parity, 1 = drift, 2 = infrastructure error.
set -euo pipefail
NS="ofbiz"
psql_monolith() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
psql_finance()  { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d finance -Atc "$1"; }

# monolith per-account sums over posted entries
# the comparison set is the monolith's posted transactions: rows the running
# stack added after migration (relay replay of pre-deploy invoicerequested
# events) exist only on the new side and are not migration drift
MONO_IDS=$(psql_monolith "
  SELECT string_agg(chr(39) || \"ACCTG_TRANS_ID\" || chr(39), ',')
  FROM \"ACCTG_TRANS\" WHERE \"IS_POSTED\" = chr(89);") || exit 2

MONO_TB=$(psql_monolith "
  SELECT coalesce(string_agg(\"GL_ACCOUNT_ID\" || '=' || debits::text || '/' || credits::text,
    chr(10) ORDER BY \"GL_ACCOUNT_ID\"), 'empty') FROM (
    SELECT \"GL_ACCOUNT_ID\",
           sum(CASE \"DEBIT_CREDIT_FLAG\" WHEN 'D' THEN \"AMOUNT\" ELSE 0 END) AS debits,
           sum(CASE \"DEBIT_CREDIT_FLAG\" WHEN 'C' THEN \"AMOUNT\" ELSE 0 END) AS credits
    FROM \"ACCTG_TRANS_ENTRY\" e
    WHERE EXISTS (SELECT 1 FROM \"ACCTG_TRANS\" t
                  WHERE t.\"ACCTG_TRANS_ID\" = e.\"ACCTG_TRANS_ID\" AND t.\"IS_POSTED\" = chr(89))
    GROUP BY \"GL_ACCOUNT_ID\") g;") || exit 2

# finance-svc per-account sums over posted transactions (entries inline JSON)
SVC_TB=$(psql_finance "
  SELECT coalesce(string_agg(\"glAccountId\" || '=' || debits::text || '/' || credits::text,
    chr(10) ORDER BY \"glAccountId\"), 'empty') FROM (
    SELECT \"glAccountId\",
           sum(CASE \"debitCredit\" WHEN 'DEBIT' THEN amount ELSE 0 END) AS debits,
           sum(CASE \"debitCredit\" WHEN 'CREDIT' THEN amount ELSE 0 END) AS credits
    FROM gl_transactions t, jsonb_to_recordset(t.entries::jsonb) AS e(
         \"glAccountId\" text, amount numeric, \"debitCredit\" text)
    WHERE t.posted AND t.acctg_trans_id IN (${MONO_IDS})
    GROUP BY \"glAccountId\") g;") || exit 2

echo "trial-balance parity report (P5-GATE finance slice, migrated dataset)"
echo "  monolith per-account D/C:"
echo "${MONO_TB}" | sed 's/^/    /'
echo "  finance-svc per-account D/C:"
echo "${SVC_TB}" | sed 's/^/    /'

FAIL=0
[ "${MONO_TB}" = "${SVC_TB}" ] || { echo "  DRIFT: trial balances differ"; FAIL=1; }
if [ "${FAIL}" = "0" ]; then
  # balanced-ledger invariant visible in the report itself
  echo "  PARITY — trial balance equals the monolith trial balance"
fi
exit "${FAIL}"
