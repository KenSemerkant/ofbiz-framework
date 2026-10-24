#!/usr/bin/env bash
# P6-GATE: migrate the seeded monolith long-tail dataset (48-seed-p6.sh)
# into the five P6 service databases — deterministic, idempotent. The
# dataset the P6 consistency checkers then compare.
set -euo pipefail
NS="ofbiz"
M() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "$1"; }
S() { # db sql
  kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d "$1" -q -c "$2"
}

echo "==> [1/5] manufacturing"
M 'SELECT "PRODUCT_ID"||chr(124)||"COMPONENT_PRODUCT_ID"||chr(124)||coalesce("QUANTITY",1)||chr(124)||coalesce("SCRAP_PCT",0) FROM "PRODUCT_BOM" ORDER BY 1;' \
| while IFS='|' read -r pid cid qty scrap; do
  [ -z "${pid}" ] && continue
  S manufacturing "INSERT INTO boms (product_id, components) VALUES ('${pid}',
    '[{\"productId\":\"${cid}\",\"quantity\":${qty},\"scrapPct\":${scrap}}]')
    ON CONFLICT (product_id) DO UPDATE SET components = EXCLUDED.components;"
done
M 'SELECT "ROUTING_ID"||chr(124)||"PRODUCT_ID" FROM "ROUTING" ORDER BY 1;' \
| while IFS='|' read -r rid pid; do
  [ -z "${rid}" ] && continue
  TASKS=$(M "SELECT string_agg('{\"seq\":' || (\"SEQ_ID\"::int) || ',\"name\":\"' || \"TASK_NAME\" || '\",\"estimatedDuration\":' || coalesce(\"ESTIMATED_DURATION\",0) || '}', ',' ORDER BY \"SEQ_ID\") FROM \"ROUTING_TASK\" WHERE \"ROUTING_ID\"='${rid}';")
  S manufacturing "INSERT INTO routings (routing_id, product_id, tasks) VALUES ('${rid}','${pid}','[${TASKS}]')
    ON CONFLICT (routing_id) DO UPDATE SET tasks = EXCLUDED.tasks;"
done
M 'SELECT "MRP_ID"||chr(124)||"PRODUCT_ID"||chr(124)||"EVENT_TYPE"||chr(124)||coalesce("QUANTITY",0)||chr(124)||to_char("EVENT_DATE",'YYYY-MM-DD"T"HH24:MI:SS"Z"') FROM "MRP_EVENT" ORDER BY 1;' \
| while IFS='|' read -r mid pid etype qty when; do
  [ -z "${mid}" ] && continue
  S manufacturing "INSERT INTO mrp_events (event_id, product_id, event_type, quantity, occurred_at)
    VALUES ('${mid}-${pid}-${etype}','${pid}','${etype}',${qty},'${when}')
    ON CONFLICT (event_id) DO NOTHING;"
done

echo "==> [2/5] workeffort"
M 'SELECT "WORK_EFFORT_ID"||chr(124)||"WORK_EFFORT_TYPE_ID"||chr(124)||"WORK_EFFORT_NAME"||chr(124)||coalesce("PARENT_WORK_EFFORT_ID",chr(45))||chr(124)||"STATUS_ID"||chr(124)||coalesce(to_char("ESTIMATED_START_DATE",'YYYY-MM-DD'),chr(45))||chr(124)||coalesce(to_char("ESTIMATED_COMPLETION_DATE",'YYYY-MM-DD'),chr(45)) FROM "WORK_EFFORT" ORDER BY 1;' \
| while IFS='|' read -r wid type name parent status start end; do
  [ -z "${wid}" ] && continue
  [ "${parent}" = "-" ] && parent=NULL
  [ "${start}" = "-" ] && start=NULL
  [ "${end}" = "-" ] && end=NULL
  S workeffort "INSERT INTO work_efforts (work_effort_id, work_effort_type_id, work_effort_name, parent_work_effort_id, status_id, estimated_start_date, estimated_completion_date)
    VALUES ('${wid}','${type}','${name}',${parent:+'${parent}'},'${status}',${start:+'${start}'},${end:+'${end}'})
    ON CONFLICT (work_effort_id) DO NOTHING;"
done
M 'SELECT "TIMESHEET_ID"||chr(124)||"PARTY_ID"||chr(124)||to_char("FROM_DATE",'YYYY-MM-DD')||chr(124)||to_char("THRU_DATE",'YYYY-MM-DD')||chr(124)||"STATUS_ID" FROM "TIMESHEET" ORDER BY 1;' \
| while IFS='|' read -r tsid party from thru status; do
  [ -z "${tsid}" ] && continue
  ENTRIES=$(M "SELECT string_agg('{\"workEffortId\":\"' || \"WORK_EFFORT_ID\" || '\",\"hours\":' || coalesce(\"HOURS\",0) || ',\"rateTypeId\":\"' || coalesce(\"RATE_TYPE_ID\",'STANDARD') || '\"}', ',' ORDER BY \"TIME_ENTRY_ID\") FROM \"TIME_ENTRY\" WHERE \"TIMESHEET_ID\"='${tsid}';")
  S workeffort "INSERT INTO timesheets (timesheet_id, party_id, from_date, thru_date, status_id, entries)
    VALUES ('${tsid}','${party}','${from}','${thru}','${status}','[${ENTRIES}]')
    ON CONFLICT (timesheet_id) DO NOTHING;"
done

echo "==> [3/5] humanres"
M 'SELECT "EMPL_POSITION_ID"||chr(124)||"EMPL_POSITION_TYPE_ID"||chr(124)||coalesce("REPORTS_TO_EMPL_POSITION_ID",chr(45))||chr(124)||"STATUS_ID" FROM "EMPL_POSITION" ORDER BY 1;' \
| while IFS='|' read -r pid type reports status; do
  [ -z "${pid}" ] && continue
  [ "${reports}" = "-" ] && reports=NULL || reports="'${reports}'"
  S humanres "INSERT INTO empl_positions VALUES ('${pid}','${type}',${reports},'${status}')
    ON CONFLICT (empl_position_id) DO NOTHING;"
done
M 'SELECT "EMPLOYMENT_ID"||chr(124)||"PARTY_ID"||chr(124)||"EMPL_POSITION_ID"||chr(124)||to_char("FROM_DATE",'YYYY-MM-DD')||chr(124)||coalesce(to_char("THRU_DATE",'YYYY-MM-DD'),chr(45)) FROM "EMPLOYMENT" ORDER BY 1;' \
| while IFS='|' read -r eid party pid from thru; do
  [ -z "${eid}" ] && continue
  [ "${thru}" = "-" ] && thru=NULL || thru="'${thru}'"
  S humanres "INSERT INTO employments VALUES ('${eid}','${party}','${pid}','${from}',${thru})
    ON CONFLICT (employment_id) DO NOTHING;"
done

echo "==> [4/5] content (metadata only — object bytes stay in S3 per ADR-008)"
M 'SELECT "CONTENT_ID"||chr(124)||"CONTENT_TYPE_ID"||chr(124)||"CONTENT_NAME"||chr(124)||coalesce("PARENT_CONTENT_ID",chr(45))||chr(124)||"STATUS_ID"||chr(124)||coalesce("MIME_TYPE",chr(45))||chr(124)||coalesce("SIZE_BYTES"::text,chr(45)) FROM "CONTENT" ORDER BY 1;' \
| while IFS='|' read -r cid ctype name parent status mime size; do
  [ -z "${cid}" ] && continue
  [ "${parent}" = "-" ] && parent=NULL || parent="'${parent}'"
  [ "${mime}" = "-" ] && mime=NULL || mime="'${mime}'"
  [ "${size}" = "-" ] && size=NULL
  S content "INSERT INTO contents (content_id, content_type_id, content_name, parent_content_id, status_id, content_type, size_bytes)
    VALUES ('${cid}','${ctype}','${name}',${parent},'${status}',${mime},${size})
    ON CONFLICT (content_id) DO NOTHING;"
done

echo "==> [5/5] marketing"
M 'SELECT "MARKETING_CAMPAIGN_ID"||chr(124)||"CAMPAIGN_NAME"||chr(124)||"STATUS_ID"||chr(124)||to_char("FROM_DATE",'YYYY-MM-DD')||chr(124)||to_char("THRU_DATE",'YYYY-MM-DD') FROM "MARKETING_CAMPAIGN" ORDER BY 1;' \
| while IFS='|' read -r cid name status from thru; do
  [ -z "${cid}" ] && continue
  S marketing "INSERT INTO marketing_campaigns VALUES ('${cid}','${name}','${status}','${from}','${thru}')
    ON CONFLICT (marketing_campaign_id) DO NOTHING;"
done
M 'SELECT "TRACKING_CODE_ID"||chr(124)||coalesce("MARKETING_CAMPAIGN_ID",chr(45))||chr(124)||coalesce("DESCRIPTION",chr(45)) FROM "TRACKING_CODE" ORDER BY 1;' \
| while IFS='|' read -r tcode camp desc; do
  [ -z "${tcode}" ] && continue
  [ "${camp}" = "-" ] && camp=NULL || camp="'${camp}'"
  [ "${desc}" = "-" ] && desc=NULL || desc="'${desc}'"
  S marketing "INSERT INTO tracking_codes (tracking_code_id, marketing_campaign_id, description, from_date, thru_date)
    VALUES ('${tcode}',${camp},${desc},'2026-01-01','2030-12-31')
    ON CONFLICT (tracking_code_id) DO NOTHING;"
done
M 'SELECT "SALES_OPPORTUNITY_ID"||chr(124)||"DESCRIPTION"||chr(124)||coalesce("OPPORTUNITY_STAGE_ID",chr(45))||chr(124)||coalesce("CLOSE_PROBABILITY"::text,chr(45)) FROM "SALES_OPPORTUNITY" ORDER BY 1;' \
| while IFS='|' read -r oid desc stage prob; do
  [ -z "${oid}" ] && continue
  [ "${stage}" = "-" ] && stage='PROPOSAL'
  [ "${prob}" = "-" ] && prob=0
  S marketing "INSERT INTO sales_opportunities VALUES ('${oid}','${desc}','${stage}',${prob})
    ON CONFLICT (sales_opportunity_id) DO NOTHING;"
done

echo "P6 import done"
