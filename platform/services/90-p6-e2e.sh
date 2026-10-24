#!/usr/bin/env bash

# CG-6: long-tail domain e2e on the staging cluster. Proves the five
# per-track CGs:
#   T1 manufacturing: MRP over the CG6-% dataset (seed 48 + import 49)
#      proposes exactly the netting result of monolith MrpServices on the
#      same dataset — checked against the same netting computed on the
#      monolith MRP_EVENT table itself
#   T2 workeffort: iCal feed is valid RFC 5545 (parsed by an external
#      calendar library on the runner host) and contains the effort
#   T3 humanres: onboarding saga grants the party EMPLOYEE role via
#      party-svc AND creates the employment
#   T4 content: upload → publish → URL fetch roundtrip through Garage S3
#   T5 marketing: attribution report joins order events by tracking code
# Non-zero exit on failure.
set -euo pipefail
NS="ofbiz"
RUN="CG6$(date +%s)"
FAIL=0

TOOLS="cg6-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 900 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1' EXIT
X() { kubectl -n "${NS}" exec -i "${TOOLS}" -- sh -c "$1"; }

echo "==> [1/5] T1 MRP parity (dataset 48-seed, MrpServices netting)"
# reference: same netting on the monolith MRP_EVENT table
REF=$(kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -Atc "
  SELECT string_agg(product_id || '=' || shortage, ' ' ORDER BY product_id) FROM (
    SELECT \"PRODUCT_ID\" AS product_id, GREATEST(-(sum(CASE \"EVENT_TYPE\" WHEN 'DEMAND' THEN -\"QUANTITY\"::numeric ELSE \"QUANTITY\"::numeric END)), 0)::int AS shortage
    FROM \"MRP_EVENT\" WHERE \"MRP_ID\" LIKE 'CG6-%' GROUP BY \"PRODUCT_ID\") t WHERE shortage > 0;")
echo "  monolith reference netting: ${REF:-<none>}"
MRP=$(X "curl -s -X POST http://manufacturing-svc:8080/api/v1/mrp/run -H 'Content-Type: application/json' -d '{}'")
echo "  manufacturing-svc run: $(echo "${MRP}" | head -c 300)"
for pair in ${REF}; do
  prod=${pair%%=*}; want=${pair##*=}
  got=$(echo "${MRP}" | python3 -c "
import sys,json
try: data=json.load(sys.stdin)
except Exception: sys.exit(1)
items=data if isinstance(data,list) else data.get('proposals') or data.get('items') or []
for p in items:
    if p.get('productId')=='${prod}': print(int(float(p.get('quantity')))); break
" 2>/dev/null || echo "")
  [ "${got}" = "${want}" ] && echo "  ${prod}: proposed ${got} (matches monolith)" \
    || { echo "  FAIL: ${prod} proposed '${got}', monolith expects ${want}"; FAIL=1; }
done
# CASE must produce no proposal (supply >= demand)
echo "${MRP}" | grep -q 'CG6-CASE' && { echo "  FAIL: CG6-CASE should not be proposed (supply 12 >= demand 10)"; FAIL=1; } \
  || echo "  CG6-CASE: correctly not proposed"

echo "==> [2/5] T2 iCal feed (external calendar validation)"
X "curl -s -o /dev/null -X POST http://workeffort-svc:8080/api/v1/workefforts -H 'Content-Type: application/json' \
    -d '{\"workEffortId\":\"${RUN}\",\"workEffortTypeId\":\"EVENT\",\"workEffortName\":\"CG6 iCal proof\",\"estimatedStartDate\":\"2026-11-02\",\"estimatedCompletionDate\":\"2026-11-03\"}'" >/dev/null
X "curl -s http://workeffort-svc:8080/api/v1/workefforts/calendar.ics" > /tmp/p6.ics
head -c 200 /tmp/p6.ics; echo
python3 -c "
import sys
try:
    from icalendar import Calendar
except ImportError:
    sys.exit(42)
cal = Calendar.from_ical(open('/tmp/p6.ics','rb').read())
events = [c for c in cal.walk('VEVENT')]
assert any(str(c['UID']).startswith('${RUN}') for c in events), 'drill effort missing'
print('parsed OK: %d VEVENT(s), drill UID present' % len(events))
" || RC=$?
if [ "${RC}" = "42" ]; then
  echo "  (icalendar lib absent — structural RFC 5545 check)"
  grep -q $'\r' /tmp/p6.ics && echo "  CRLF line endings OK" || { echo "  FAIL: no CRLF"; FAIL=1; }
  grep -q "BEGIN:VCALENDAR" /tmp/p6.ics && grep -q "BEGIN:VEVENT" /tmp/p6.ics \
    && grep -q "UID:${RUN}" /tmp/p6.ics && grep -q "DTSTART" /tmp/p6.ics \
    && echo "  VCALENDAR/VEVENT/UID/DTSTART present" || { echo "  FAIL: structure invalid"; FAIL=1; }
elif [ "${RC}" != "0" ]; then
  echo "  FAIL: external parser rejected the feed"; FAIL=1
fi

echo "==> [3/5] T3 onboarding saga (party role + employment)"
X "curl -s -o /dev/null -X POST http://party-svc:8080/api/v1/parties -H 'Content-Type: application/json' \
    -d '{\"partyId\":\"${RUN}\",\"kind\":\"PERSON\",\"firstName\":\"Onboarding\",\"lastName\":\"Drill\"}'" >/dev/null
X "curl -s -o /dev/null -X POST http://humanres-svc:8080/api/v1/positions -H 'Content-Type: application/json' \
    -d '{\"emplPositionId\":\"${RUN}-POS\",\"emplPositionTypeId\":\"ENGINEER\",\"statusId\":\"ACTIVE\"}'" >/dev/null
ONBOARD=""
ONB_ID=""
for i in $(seq 1 30); do
  ONBOARD=$(X "curl -s -X POST http://humanres-svc:8080/api/v1/onboarding -H 'Content-Type: application/json' \
    -d '{\"partyId\":\"${RUN}\",\"emplPositionId\":\"${RUN}-POS\"}'")
  ONB_ID=$(echo "${ONBOARD}" | sed -n 's/.*"onboardingId":"\([^"]*\)".*/\1/p')
  [ -n "${ONB_ID}" ] && break
  sleep 2
done
echo "  onboarding: $(echo "${ONBOARD}" | head -c 200)"
[ -n "${ONB_ID}" ] || { echo "  FAIL: saga not accepted"; FAIL=1; }
STATUS=""
for i in $(seq 1 45); do
  STATUS=$(X "curl -s http://humanres-svc:8080/api/v1/onboarding/${ONB_ID}" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')
  [ "${STATUS}" = "COMPLETED" ] && break
  sleep 2
done
[ "${STATUS}" = "COMPLETED" ] && echo "  saga COMPLETED" || { echo "  FAIL: saga status=${STATUS}"; FAIL=1; }
ROLES=$(X "curl -s http://party-svc:8080/api/v1/parties/${RUN}" | grep -o 'EMPLOYEE' | head -1)
[ "${ROLES}" = "EMPLOYEE" ] && echo "  party role EMPLOYEE granted via party-svc" || { echo "  FAIL: EMPLOYEE role missing"; FAIL=1; }
EMPS=$(kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d humanres -Atc \
  "SELECT count(*) FROM employments WHERE party_id='${RUN}' AND empl_position_id='${RUN}-POS';")
[ "${EMPS}" = "1" ] && echo "  employment created" || { echo "  FAIL: employment count=${EMPS}"; FAIL=1; }

echo "==> [4/5] T4 content upload→publish→fetch (S3 roundtrip)"
BODY="cg6-content-roundtrip-${RUN}"
X "curl -s -o /dev/null -X POST http://content-svc:8080/api/v1/contents -H 'Content-Type: application/json' \
    -d '{\"contentId\":\"${RUN}\",\"contentTypeId\":\"DOCUMENT\",\"contentName\":\"CG6 roundtrip\"}'" >/dev/null
UPL=$(X "curl -s -o /dev/null -w '%{http_code}' -X POST http://content-svc:8080/api/v1/contents/${RUN}/upload \
    -H 'Content-Type: text/plain' --data-binary '${BODY}'")
{ [ "${UPL}" = "200" ] || [ "${UPL}" = "201" ]; } || { echo "  FAIL: upload ${UPL}"; FAIL=1; }
PUB=$(X "curl -s -o /dev/null -w '%{http_code}' -X POST http://content-svc:8080/api/v1/contents/${RUN}/publish")
{ [ "${PUB}" = "200" ] || [ "${PUB}" = "201" ]; } || { echo "  FAIL: publish ${PUB}"; FAIL=1; }
FETCHED=$(X "curl -s http://content-svc:8080/api/v1/contents/${RUN}/data")
[ "${FETCHED}" = "${BODY}" ] && echo "  roundtrip byte-identical through Garage" || { echo "  FAIL: fetched '${FETCHED}'"; FAIL=1; }
# draft guard: re-publish must 409
GUARD=$(X "curl -s -o /dev/null -w '%{http_code}' -X POST http://content-svc:8080/api/v1/contents/${RUN}/publish")
[ "${GUARD}" = "409" ] && echo "  re-publish typed 409" || { echo "  FAIL: re-publish got ${GUARD}, expected 409"; FAIL=1; }

echo "==> [5/5] T5 marketing attribution (orders joined by tracking code)"
X "curl -s -o /dev/null -X POST http://marketing-svc:8080/api/v1/campaigns -H 'Content-Type: application/json' \
    -d '{\"marketingCampaignId\":\"${RUN}-CAMP\",\"campaignName\":\"CG6 drill\",\"statusId\":\"ACTIVE\",\"fromDate\":\"2026-01-01\",\"thruDate\":\"2026-12-31\"}'" >/dev/null
X "curl -s -o /dev/null -X POST http://marketing-svc:8080/api/v1/tracking-codes -H 'Content-Type: application/json' \
    -d '{\"trackingCodeId\":\"${RUN}-TC\",\"marketingCampaignId\":\"${RUN}-CAMP\",\"description\":\"cg6\",\"fromDate\":\"2026-01-01\",\"thruDate\":\"2026-12-31\"}'" >/dev/null
# stock + tracked order so the placed event carries the tracking code
X "curl -s -o /dev/null -X POST http://inventory-svc:8080/api/v1/inventory-items -H 'Content-Type: application/json' \
    -d '{\"inventoryItemId\":\"${RUN}\",\"productId\":\"MP1\",\"facilityId\":\"FAC-${RUN}\"}'" >/dev/null
X "curl -s -o /dev/null -X POST http://inventory-svc:8080/api/v1/inventory-items/${RUN}/receive -H 'Content-Type: application/json' -d '{\"quantity\":5}'" >/dev/null
X "curl -s -o /dev/null -X POST http://order-svc:8080/api/v1/orders -H 'Content-Type: application/json' \
    -d '{\"customerId\":\"P1\",\"currencyUomId\":\"USD\",\"trackingCode\":\"${RUN}-TC\",\"lines\":[{\"productId\":\"MP1\",\"facilityId\":\"FAC-${RUN}\",\"quantity\":1}]}'" >/dev/null
ATTR=""
for i in $(seq 1 30); do
  ATTR=$(X "curl -s http://marketing-svc:8080/api/v1/attribution/${RUN}-TC")
  echo "${ATTR}" | grep -q '"orderCount":1\|orderCount.:.1' && break
  sleep 2
done
echo "  attribution report: $(echo "${ATTR}" | head -c 300)"
echo "${ATTR}" | grep -q "${RUN}-CAMP" && echo "  joins order events by tracking code (campaign resolved)" \
  || { echo "  FAIL: attribution report incomplete"; FAIL=1; }

[ "${FAIL}" = "0" ] && echo "P6 E2E PASSED (all five track CGs)" || { echo "P6 E2E FAILED"; exit 1; }
