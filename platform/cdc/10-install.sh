#!/usr/bin/env bash
# P2-F2-S1: install the CDC bridge — monolith-db stand-in, cdc-connect
# KafkaUser (cloud-profile identity), Debezium Connect deployment, and the
# monolith-party connector via the Connect REST API.
# P3-GATE: product + shipment slices extend the same pattern.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"

echo "==> [1/4] Monolith Postgres stand-in (party slice)"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/monolith-db.yaml"
kubectl -n "${NS}" rollout status deploy/monolith-db --timeout=300s

echo "==> [2/4] P3 product/shipment slices on the running monolith"
sh "${SCRIPT_DIR}/12-extend-monolith-p3.sh"

echo "==> [3/4] Debezium Connect"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/kafka-connect.yaml"
kubectl -n "${NS}" rollout status deploy/debezium-connect --timeout=600s

install_connector() { # name json-file
  # delete-then-POST: the JSON carries the {name, config} wrapper POST expects
  kubectl -n "${NS}" run debezium-curl-"$1" --rm -i --restart=Never \
    --image=curlimages/curl:8.10.1 \
    --command -- sh -c \
    "curl -fsS -X DELETE http://debezium-connect:8083/connectors/$1 >/dev/null 2>&1; \
     curl -fsS -X POST -H 'Content-Type: application/json' \
     --data-binary @- http://debezium-connect:8083/connectors" \
    < "$2" | tail -1
}

echo "==> [4/4] connectors (REST)"
install_connector monolith-party "${SCRIPT_DIR}/party-connector.json"
install_connector monolith-product "${SCRIPT_DIR}/product-connector.json"
install_connector monolith-shipment "${SCRIPT_DIR}/shipment-connector.json"
echo "Installed. Verify with: ${SCRIPT_DIR}/20-verify.sh"
