#!/usr/bin/env bash
# Phase 0 / P0-F3 (expanded): observability from inception —
# Jaeger (tracing), Prometheus (metrics), Grafana+Loki (dashboards/logs),
# OpenSearch (log analytics/audit retention).
# Idempotent. Requires the bootstrapped cluster (00-bootstrap.sh).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NS="observability"

echo "==> Pointing Istio mesh tracing at Jaeger (zipkin receiver)"
# zipkin, not otlp: on Istio 1.31 minimal profile the OTLP provider doesn't
# emit mesh spans (KNOWN-ISSUE-1) while the zipkin receiver path does.
istioctl install --set profile=minimal --skip-confirmation \
  --set meshConfig.enableTracing=true \
  --set "meshConfig.extensionProviders[0].name=zipkin" \
  --set "meshConfig.extensionProviders[0].zipkin.url=http://jaeger-collector.observability.svc.cluster.local:9411/api/v2/spans" \
  --set "meshConfig.defaultProviders.tracing[0]=zipkin" >/dev/null

echo "==> Applying observability stack"
kubectl apply -f "${SCRIPT_DIR}/observability-stack.yaml" >/dev/null

echo "==> Waiting for rollouts"
for d in jaeger prometheus loki grafana; do
  kubectl -n "${NS}" rollout status "deploy/${d}" --timeout=300s
done
kubectl -n "${NS}" rollout status statefulset/opensearch --timeout=600s
kubectl -n "${NS}" rollout status daemonset/promtail --timeout=300s
kubectl -n "${NS}" rollout status daemonset/filebeat --timeout=300s

echo "Observability stack ready. Verify with platform/observability/20-verify.sh"
