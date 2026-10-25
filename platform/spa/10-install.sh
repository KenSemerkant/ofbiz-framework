#!/usr/bin/env bash
# P8: build the SPA image, load into kind, roll the deployment.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"
CLUSTER="ofbiz"
TAG="spa:local"

docker build -t "${TAG}" "${SCRIPT_DIR}"
kind load docker-image "${TAG}" --name "${CLUSTER}"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/spa.yaml"
kubectl -n "${NS}" set image deploy/spa nginx="${TAG}"
# Same-tag deploys are a set-image no-op; restart so the pod picks up the
# freshly loaded spa:local image instead of serving a stale bundle.
kubectl -n "${NS}" rollout restart deploy/spa
kubectl -n "${NS}" rollout status deploy/spa --timeout=180s
echo "SPA deployed (port-forward: kubectl -n ${NS} port-forward svc/spa 4200:4200)"
