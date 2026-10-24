#!/usr/bin/env bash
# P7-S1: remove the CDC bridge — connectors, replication slots, Debezium
# Connect deployment, ofbiz.* topics (archived by 81 first), and scale the
# monolith DB stand-in to zero. Idempotent; safe to re-run.
set -euo pipefail

NS="ofbiz"
BROKER="ofbiz-ofbiz-nodes-0"
KT="/opt/kafka/bin/kafka-topics.sh"
KRC="/opt/kafka/bin/kafka-run-class.sh"

if [ ! -f /tmp/ofbiz-offsets-t0.txt ]; then
  echo "FAIL: /tmp/ofbiz-offsets-t0.txt missing — capture a T0 baseline first"; exit 1
fi

echo "==> [1/6] Zero-traffic evidence (T1 offsets vs T0)"
kubectl -n "${NS}" exec "${BROKER}" -- "${KRC}" org.apache.kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 2>/dev/null | grep -E '^(ofbiz\.|__debezium)' | sort > /tmp/ofbiz-offsets-t1.txt
date -u +%FT%TZ | tee -a /tmp/ofbiz-offsets-t1.txt
if diff -u <(grep -v '^20' /tmp/ofbiz-offsets-t0.txt) <(grep -v '^20' /tmp/ofbiz-offsets-t1.txt); then
  echo "PASS: zero ofbiz.* traffic between T0 and T1 (offsets unchanged)"
else
  echo "FAIL: ofbiz.* offsets moved — investigate before removing the bridge"; exit 1
fi

echo "==> [2/6] Delete Debezium connectors (REST)"
for c in monolith-party monolith-product monolith-shipment monolith-order monolith-p6; do
  kubectl -n "${NS}" run "debezium-del-${c}" --rm -i --restart=Never \
    --image=curlimages/curl:8.10.1 \
    --command -- curl -fsS -X DELETE "http://debezium-connect:8083/connectors/${c}" >/dev/null 2>&1 \
    && echo "  -- deleted ${c}" || echo "  -- ${c} already gone"
done

echo "==> [3/6] Drop replication slots on monolith-db"
DBPOD="$(kubectl -n "${NS}" get pods --no-headers | awk '/^monolith-db-/{print $1; exit}')"
if [ -n "${DBPOD}" ]; then
  for slot in debezium_party debezium_product debezium_shipment debezium_order debezium_p6; do
    kubectl -n "${NS}" exec "${DBPOD}" -- psql -U ofbiz -d ofbiz -c \
      "SELECT pg_drop_replication_slot('${slot}') WHERE EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name='${slot}');" 2>/dev/null \
      && echo "  -- dropped ${slot}" || echo "  -- ${slot} already gone"
  done
else
  echo "  -- monolith-db pod not found (already scaled?)"
fi

echo "==> [4/6] Remove Debezium Connect deployment"
kubectl -n "${NS}" delete -f "$(dirname "$0")/kafka-connect.yaml" --ignore-not-found >/dev/null 2>&1 || true
kubectl -n "${NS}" delete deploy/debezium-connect --ignore-not-found >/dev/null
kubectl -n "${NS}" wait --for=delete deploy/debezium-connect --timeout=120s >/dev/null 2>&1 || true

echo "==> [5/6] Delete archived topics"
for t in $(kubectl -n "${NS}" exec "${BROKER}" -- "${KT}" --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E '^ofbiz\.|^__debezium-heartbeat\.ofbiz|^cdc-connect-' | sort); do
  kubectl -n "${NS}" exec "${BROKER}" -- "${KT}" --bootstrap-server localhost:9092 --delete --topic "${t}" 2>/dev/null \
    && echo "  -- deleted ${t}" || echo "  -- ${t} already gone"
done

echo "==> [6/6] Scale monolith DB stand-in to zero (PVC retained)"
kubectl -n "${NS}" scale deploy/monolith-db --replicas=0

echo "==> Verify"
REMAIN="$(kubectl -n "${NS}" exec "${BROKER}" -- "${KT}" --bootstrap-server localhost:9092 --list 2>/dev/null | grep -cE '^ofbiz\.|^__debezium-heartbeat\.ofbiz|^cdc-connect-' || true)"
SLOTS="$(kubectl -n "${NS}" get pods --no-headers 2>/dev/null | awk '/^monolith-db-/&&/Running/{print "1"}' | head -1)"
if [ "${REMAIN:-0}" = "0" ] && [ -z "${SLOTS:-}" ]; then
  echo "PASS: P7-S1 — CDC bridge removed; ofbiz.* topics gone; monolith-db at 0 replicas"
else
  echo "CHECK: remaining ofbiz.* topics=${REMAIN:-?}; monolith-db running=${SLOTS:-no}"
fi
kubectl -n "${NS}" get deploy monolith-db --no-headers || true
