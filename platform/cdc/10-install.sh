#!/usr/bin/env bash
# P2-F2-S1: install the CDC bridge — monolith-db stand-in, cdc-connect
# KafkaUser (cloud-profile identity), Debezium Connect deployment, and the
# monolith-party connector via the Connect REST API.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"

echo "==> [1/4] Monolith Postgres stand-in (party slice)"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/monolith-db.yaml"
kubectl -n "${NS}" rollout status deploy/monolith-db --timeout=300s

echo "==> [2/4] cdc-connect KafkaUser (cloud-profile identity; local uses the plaintext listener)"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/kafka-user.yaml"

echo "==> [3/4] Debezium Connect"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/kafka-connect.yaml"
kubectl -n "${NS}" rollout status deploy/debezium-connect --timeout=600s

echo "==> [4/4] monolith-party connector (REST)"
# delete-then-POST: the JSON carries the {name, config} wrapper POST expects
kubectl -n "${NS}" run debezium-curl --rm -i --restart=Never --image=curlimages/curl:8.10.1 \
  --command -- sh -c \
  "curl -fsS -X DELETE http://debezium-connect:8083/connectors/monolith-party >/dev/null 2>&1; \
   curl -fsS -X POST -H 'Content-Type: application/json' \
   --data-binary @- http://debezium-connect:8083/connectors" \
  < "${SCRIPT_DIR}/party-connector.json" | tail -1
echo "Installed. Verify with: ${SCRIPT_DIR}/20-verify.sh"
