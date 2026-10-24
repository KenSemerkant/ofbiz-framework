#!/usr/bin/env bash
# P7-S1: archive every ofbiz.* CDC topic (plus Debezium heartbeats and
# connect-internal topics) to S3 before deletion. Messages are dumped via
# kafka-console-consumer from the beginning; end offsets go into the manifest.
set -euo pipefail

NS="ofbiz"
STORAGE_NS="storage"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$(mktemp -d)"
GARAGE_POD="garage-0"
G="/garage -c /etc/garage.toml"
SVC="garage.${STORAGE_NS}.svc"
BROKER="ofbiz-ofbiz-nodes-0"
KC="/opt/kafka/bin/kafka-console-consumer.sh"
KRC="/opt/kafka/bin/kafka-run-class.sh"

echo "==> [1/3] Dump topics (beginning-to-end)"
TOPICS="$(kubectl -n "${NS}" exec "${BROKER}" -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E '^ofbiz\.|^__debezium-heartbeat\.ofbiz|^cdc-connect-' | sort)"
echo "${TOPICS}" | tee "${OUT}/topic-list.txt"
for t in ${TOPICS}; do
  echo "  -- ${t}"
  kubectl -n "${NS}" exec "${BROKER}" -- "${KC}" --bootstrap-server localhost:9092 \
    --topic "${t}" --from-beginning --timeout-ms 8000 > "${OUT}/${t}.jsonl" 2>/dev/null || true
done

kubectl -n "${NS}" exec "${BROKER}" -- "${KRC}" org.apache.kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 2>/dev/null | grep -E '^(ofbiz\.|__debezium|cdc-connect-)' | sort > "${OUT}/offsets.txt"

echo "==> [2/3] Tarball + manifest"
# glob must expand inside ${OUT} — `tar -C` does not relocate the pattern
(cd "${OUT}" && tar -czf "${OUT}/ofbiz-topics-${STAMP}.tar.gz" ./*.jsonl topic-list.txt offsets.txt)
sha256sum "${OUT}/ofbiz-topics-${STAMP}.tar.gz" | awk -v s="${STAMP}" '{print $1"  ofbiz-topics-"s".tar.gz"}' > "${OUT}/manifest-${STAMP}.txt"
echo "topics=$(echo "${TOPICS}" | wc -l | tr -d ' ')" >> "${OUT}/manifest-${STAMP}.txt"
date -u +%FT%TZ >> "${OUT}/manifest-${STAMP}.txt"

echo "==> [3/3] S3 upload (Garage ofbiz-archive)"
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} bucket create ofbiz-archive >/dev/null 2>&1 || true
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key info ofbiz-archive >/dev/null 2>&1 \
  && kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key delete --yes ofbiz-archive >/dev/null 2>&1 || true
KEY_OUT="$(kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key create ofbiz-archive 2>/dev/null)"
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key allow ofbiz-archive --read --write --create-bucket >/dev/null 2>&1 || true
KEY_ID="$(echo "${KEY_OUT}" | grep -oE 'GK[0-9a-f]+' | head -1)"
SECRET="$(echo "${KEY_OUT}" | grep -oE 'Secret key: +[0-9a-zA-Z+/=]+' | sed 's/Secret key: *//')"
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} bucket allow --read --write ofbiz-archive --key ofbiz-archive >/dev/null 2>&1 || true
if [ -z "${KEY_ID}" ] || [ -z "${SECRET}" ]; then echo "FAIL: could not issue archive key"; exit 1; fi

kubectl -n "${NS}" delete pod p7-s3-upload --ignore-not-found >/dev/null 2>&1
kubectl -n "${NS}" run p7-s3-upload --image=amazon/aws-cli:2.23.0 --command -- sleep 900 >/dev/null
kubectl -n "${NS}" wait --for=condition=Ready pod/p7-s3-upload --timeout=180s >/dev/null
kubectl -n "${NS}" exec -i p7-s3-upload -- sh -c 'cat > /tmp/topics.tar.gz' < "${OUT}/ofbiz-topics-${STAMP}.tar.gz"
kubectl -n "${NS}" exec -i p7-s3-upload -- sh -c 'cat > /tmp/manifest.txt' < "${OUT}/manifest-${STAMP}.txt"
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 cp /tmp/topics.tar.gz "s3://ofbiz-archive/kafka-topics/ofbiz-topics-${STAMP}.tar.gz" >/dev/null
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 cp /tmp/manifest.txt "s3://ofbiz-archive/kafka-topics/manifest-${STAMP}.txt" >/dev/null
echo "--- S3 listing (gate evidence) ---"
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 ls "s3://ofbiz-archive/kafka-topics/"
kubectl -n "${NS}" delete pod p7-s3-upload --ignore-not-found >/dev/null

echo "PASS: P7-S1 topic archive in s3://ofbiz-archive/kafka-topics/"
echo "sha256: $(head -1 "${OUT}/manifest-${STAMP}.txt" | awk '{print $1}')"
echo "local copies in ${OUT} (S3 copy is the record)"
