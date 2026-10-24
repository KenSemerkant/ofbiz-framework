#!/usr/bin/env bash
# P7 gate-trap fix: broker-1 KRaft metadata log corrupted ("Full log
# truncation expected but didn't happen" fatal fault). Moves the broker-1
# __cluster_metadata log ASIDE (not deleted) inside its PVC; Strimzi then
# recreates the broker, which re-syncs metadata from the healthy quorum
# (brokers 0 + 2, which hold full topic replicas). Topic data on the volume
# is untouched.
set -euo pipefail
NS="ofbiz"

echo "==> [1/4] Stop the crash-looping broker pod (PVC survives)"
kubectl -n "${NS}" delete pod ofbiz-ofbiz-nodes-1 --grace-period=10 >/dev/null
sleep 8

echo "==> [2/4] Helper pod mounts the PVC read-write"
kubectl -n "${NS}" delete pod p7-broker1-fix --ignore-not-found >/dev/null 2>&1
kubectl apply -f - <<'YAML'
apiVersion: v1
kind: Pod
metadata:
  name: p7-broker1-fix
  namespace: ofbiz
spec:
  restartPolicy: Never
  volumes:
    - name: d
      persistentVolumeClaim:
        claimName: data-ofbiz-ofbiz-nodes-1
  containers:
    - name: fix
      image: busybox:1.36
      command: ["sh", "-c"]
      args:
        - |
          set -e
          ls /data
          SRC=$(find /data -maxdepth 2 -type d -name __cluster_metadata | head -1)
          if [ -n "$SRC" ]; then
            mv "$SRC" "/data/metadata-moved-aside-20261024"
            echo "moved: $SRC -> /data/metadata-moved-aside-20261024"
          else
            echo "no __cluster_metadata found (already moved?)"
          fi
          ls /data
      volumeMounts:
        - name: d
          mountPath: /data
YAML
kubectl -n "${NS}" wait --for=condition=Ready pod/p7-broker1-fix --timeout=180s >/dev/null
kubectl -n "${NS}" wait --for=jsonpath='{.status.phase}'=Succeeded pod/p7-broker1-fix --timeout=180s >/dev/null 2>&1 || true
echo "--- helper output ---"
kubectl -n "${NS}" logs p7-broker1-fix || true
kubectl -n "${NS}" delete pod p7-broker1-fix --ignore-not-found >/dev/null

echo "==> [3/4] Wait for Strimzi to recreate broker-1"
sleep 20
for i in $(seq 1 30); do
  READY="$(kubectl -n "${NS}" get pod ofbiz-ofbiz-nodes-1 -o jsonpath='{.status.containerStatuses[?(@.name=="kafka")].ready}' 2>/dev/null || echo '')"
  [ "${READY}" = "true" ] && break
  sleep 10
done
echo "broker-1 kafka ready: ${READY:-unknown}"

echo "==> [4/4] Delete the two topics that were blocked on broker-1"
for t in ofbiz.SHIPMENT ofbiz.SHIPMENT_ITEM; do
  kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 --delete --topic "${t}" 2>&1 | tail -1 || true
done
sleep 8
REMAIN="$(kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -cE '^ofbiz\.|^__debezium|^cdc-connect-' || true)"
if [ "${REMAIN:-0}" = "0" ]; then
  echo "PASS: broker-1 recovered, zero ofbiz.*/debezium/cdc-connect topics remain"
else
  echo "CHECK: ${REMAIN} target topics still listed (broker-1 may still be syncing)"
fi
