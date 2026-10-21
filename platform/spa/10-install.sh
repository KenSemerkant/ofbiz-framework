#!/usr/bin/env bash
# CG-2: deploy the SPA skeleton (index.html + nginx on :4200).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"

kubectl -n "${NS}" create configmap spa-index --from-file=index.html="${SCRIPT_DIR}/index.html" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/spa.yaml"
kubectl -n "${NS}" rollout status deploy/spa --timeout=180s
echo "SPA skeleton deployed (port-forward: kubectl -n ${NS} port-forward svc/spa 4200:4200)"
