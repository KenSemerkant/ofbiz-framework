#!/usr/bin/env bash
# P1-F6: remove the platform-service layer from the dev cluster.
#   sh platform/services/30-undeploy.sh              # services + backing stores
#   sh platform/services/30-undeploy.sh --reset-data # also wipe PVC data + topics
# Phase-0 infra (mesh, kafka brokers, observability, vault, storage) stays;
# for a full teardown including the cluster: sh infra/dev/down.sh
set -euo pipefail
NS="ofbiz"
RESET_DATA="${1:-}"

echo "==> Helm releases"
for r in identity-svc gateway audit-svc notification-svc search-svc demo-svc; do
  helm -n "${NS}" uninstall "${r}" >/dev/null 2>&1 && echo "  uninstalled ${r}" || echo "  (absent: ${r})"
done

echo "==> Backing stores"
kubectl -n "${NS}" delete statefulset postgres --ignore-not-found >/dev/null
kubectl -n "${NS}" delete deploy redis mailhog --ignore-not-found >/dev/null
kubectl -n "${NS}" delete svc postgres redis mailhog --ignore-not-found >/dev/null
kubectl -n "${NS}" delete configmap postgres-init --ignore-not-found >/dev/null

echo "==> Service credentials + stray verify pods"
kubectl -n "${NS}" delete secret audit-svc-kafka-pem notification-svc-kafka-pem \
  search-svc-kafka-pem --ignore-not-found >/dev/null
kubectl -n "${NS}" delete pod p1f6-verify-tools p1f6-t vt vt2 vt3 vt4 \
  --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- rm -rf /tmp/kafka-tls >/dev/null 2>&1 || true

if [ "${RESET_DATA}" = "--reset-data" ]; then
  echo "==> --reset-data: PVCs + Kafka topics + KafkaUsers"
  kubectl -n "${NS}" delete pvc -l app=postgres --ignore-not-found >/dev/null
  kubectl -n "${NS}" delete pvc data-postgres-0 --ignore-not-found >/dev/null
  for t in search.doc.indexed.v1 search.doc.indexed.v1.dlq; do
    kubectl -n "${NS}" delete kafkatopic "${t}" --ignore-not-found >/dev/null
  done
  kubectl -n "${NS}" delete kafkauser search-svc audit-svc notification-svc \
    --ignore-not-found >/dev/null
  echo "  note: search docs live in the observability OpenSearch — drop with:"
  echo "    kubectl -n observability exec opensearch-0 -- curl -s -XDELETE localhost:9200/search-docs"
else
  echo "  (data kept: postgres PVC, topics, OpenSearch index — pass --reset-data to wipe)"
fi

echo "==> Remaining ofbiz workloads"
kubectl -n "${NS}" get deploy,sts 2>/dev/null | grep -vE 'kafka|strimzi|schema' || true
echo "DONE — platform service layer removed (Phase-0 stack intact)"
