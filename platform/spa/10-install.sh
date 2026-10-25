#!/usr/bin/env bash
# CG-2: deploy the SPA skeleton (index.html + nginx on :4200).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="ofbiz"

kubectl -n "${NS}" create configmap spa-index --from-file=index.html="${SCRIPT_DIR}/index.html" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
# OIDC callback lands on /login/oauth2/code/gateway (the registered redirect
# URI) — serve index.html for any path so the ?code=... param reaches the SPA.
kubectl -n "${NS}" create configmap spa-nginx-conf --from-file=default.conf=/dev/stdin \
  --dry-run=client -o yaml <<'EOF' | kubectl apply -f - >/dev/null
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;
  location / { try_files $uri /index.html; }
}
EOF
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/spa.yaml"
kubectl -n "${NS}" rollout status deploy/spa --timeout=180s
echo "SPA skeleton deployed (port-forward: kubectl -n ${NS} port-forward svc/spa 4200:4200)"
