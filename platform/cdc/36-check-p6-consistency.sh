#!/usr/bin/env bash
# P6-GATE consistency checker — delta report between the seeded monolith
# long-tail tables (48-seed-p6.sh + 49-import-p6.sh) and the five P6
# services. Compares the MIGRATED CG6-% dataset per domain (one checker
# covers all five tracks; same contract as 34/35).
# Non-zero exit on drift: 0 = delta 0, 1 = drift.
set -euo pipefail
NS="ofbiz"
M() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
S() { kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d "$1" -Atc "$2"; }
FAIL=0
cmp() { # label mono_val svc_val
  echo "  $1: monolith/svc checksums ${2:0:12}/${3:0:12}"
  [ "$2" = "$3" ] || { echo "  DRIFT: $1"; FAIL=1; }
}

echo "consistency delta report (P6-GATE, migrated CG6-% dataset)"

echo "== manufacturing =="
cmp boms \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"PRODUCT_ID\"||chr(124)||\"COMPONENT_PRODUCT_ID\"||chr(124)||\"QUANTITY\"||chr(124)||\"SCRAP_PCT\" AS t FROM \"PRODUCT_BOM\" WHERE \"PRODUCT_ID\" LIKE 'CG6-%') x;")" \
  "$(S manufacturing "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT product_id||chr(124)||(j->>'productId')||chr(124)||(j->>'quantity')||chr(124)||(j->>'scrapPct') AS t FROM boms CROSS JOIN LATERAL (SELECT (components::json)->i AS j FROM generate_series(0, json_array_length(components::json)-1) i) e WHERE product_id LIKE 'CG6-%') x;")"
cmp mrp-events \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"PRODUCT_ID\"||chr(124)||\"EVENT_TYPE\"||chr(124)||\"QUANTITY\" AS t FROM \"MRP_EVENT\" WHERE \"MRP_ID\" LIKE 'CG6-%') x;")" \
  "$(S manufacturing "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT product_id||chr(124)||event_type||chr(124)||(round(quantity))::int::text AS t FROM mrp_events WHERE event_id LIKE 'CG6-%') x;")"

echo "== workeffort =="
cmp efforts \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"WORK_EFFORT_ID\"||chr(124)||\"WORK_EFFORT_TYPE_ID\"||chr(124)||\"WORK_EFFORT_NAME\"||chr(124)||\"STATUS_ID\" AS t FROM \"WORK_EFFORT\" WHERE \"WORK_EFFORT_ID\" LIKE 'CG6-%') x;")" \
  "$(S workeffort "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT work_effort_id||chr(124)||work_effort_type_id||chr(124)||work_effort_name||chr(124)||status_id AS t FROM work_efforts WHERE work_effort_id LIKE 'CG6-%') x;")"
cmp timesheets \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"TIMESHEET_ID\"||chr(124)||\"PARTY_ID\"||chr(124)||\"STATUS_ID\" AS t FROM \"TIMESHEET\" WHERE \"TIMESHEET_ID\" LIKE 'CG6-%') x;")" \
  "$(S workeffort "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT timesheet_id||chr(124)||party_id||chr(124)||status_id AS t FROM timesheets WHERE timesheet_id LIKE 'CG6-%') x;")"

echo "== humanres =="
cmp positions \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"EMPL_POSITION_ID\"||chr(124)||\"EMPL_POSITION_TYPE_ID\"||chr(124)||\"STATUS_ID\" AS t FROM \"EMPL_POSITION\" WHERE \"EMPL_POSITION_ID\" LIKE 'CG6-%') x;")" \
  "$(S humanres "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT empl_position_id||chr(124)||empl_position_type_id||chr(124)||status_id AS t FROM empl_positions WHERE empl_position_id LIKE 'CG6-%') x;")"
cmp employments \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"EMPLOYMENT_ID\"||chr(124)||\"PARTY_ID\"||chr(124)||\"EMPL_POSITION_ID\" AS t FROM \"EMPLOYMENT\" WHERE \"EMPLOYMENT_ID\" LIKE 'CG6-%') x;")" \
  "$(S humanres "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT employment_id||chr(124)||party_id||chr(124)||empl_position_id AS t FROM employments WHERE employment_id LIKE 'CG6-%') x;")"

echo "== content =="
cmp contents \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"CONTENT_ID\"||chr(124)||\"CONTENT_TYPE_ID\"||chr(124)||\"CONTENT_NAME\"||chr(124)||\"STATUS_ID\" AS t FROM \"CONTENT\" WHERE \"CONTENT_ID\" LIKE 'CG6-%') x;")" \
  "$(S content "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT content_id||chr(124)||content_type_id||chr(124)||content_name||chr(124)||status_id AS t FROM contents WHERE content_id LIKE 'CG6-%') x;")"

echo "== marketing =="
cmp campaigns \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"MARKETING_CAMPAIGN_ID\"||chr(124)||\"CAMPAIGN_NAME\"||chr(124)||\"STATUS_ID\" AS t FROM \"MARKETING_CAMPAIGN\" WHERE \"MARKETING_CAMPAIGN_ID\" LIKE 'CG6-%') x;")" \
  "$(S marketing "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT marketing_campaign_id||chr(124)||campaign_name||chr(124)||status_id AS t FROM marketing_campaigns WHERE marketing_campaign_id LIKE 'CG6-%') x;")"
cmp tracking-codes \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"TRACKING_CODE_ID\"||chr(124)||coalesce(\"MARKETING_CAMPAIGN_ID\",chr(45)) AS t FROM \"TRACKING_CODE\" WHERE \"TRACKING_CODE_ID\" LIKE 'CG6-%') x;")" \
  "$(S marketing "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT tracking_code_id||chr(124)||coalesce(marketing_campaign_id,chr(45)) AS t FROM tracking_codes WHERE tracking_code_id LIKE 'CG6-%') x;")"
cmp opportunities \
  "$(M "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT \"SALES_OPPORTUNITY_ID\"||chr(124)||\"DESCRIPTION\"||chr(124)||coalesce(\"OPPORTUNITY_STAGE_ID\",chr(45))||chr(124)||coalesce(\"CLOSE_PROBABILITY\"::text,chr(45)) AS t FROM \"SALES_OPPORTUNITY\" WHERE \"SALES_OPPORTUNITY_ID\" LIKE 'CG6-%') x;")" \
  "$(S marketing "SELECT md5(string_agg(t, chr(10) ORDER BY t)) FROM (SELECT sales_opportunity_id||chr(124)||description||chr(124)||opportunity_stage_id||chr(124)||close_probability::text AS t FROM sales_opportunities WHERE sales_opportunity_id LIKE 'CG6-%') x;")"

[ "${FAIL}" = "0" ] && echo "  DELTA 0 — all five P6 slices consistent"
exit "${FAIL}"
