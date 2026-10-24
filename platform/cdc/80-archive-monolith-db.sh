#!/usr/bin/env bash
# P7-S2: final monolith DB snapshot + checksum manifest -> S3 (Garage
# ofbiz-archive bucket), restore test into a scratch DB with per-table
# count parity. Run BEFORE 82-remove-cdc.sh scales the monolith down.
set -euo pipefail

NS="ofbiz"
STORAGE_NS="storage"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$(mktemp -d)"
GARAGE_POD="garage-0"
G="/garage -c /etc/garage.toml"
SVC="garage.${STORAGE_NS}.svc"

DBPOD="$(kubectl -n "${NS}" get pods --no-headers | awk '/^monolith-db-/{print $1; exit}')"
if [ -z "${DBPOD}" ]; then echo "FAIL: monolith-db pod not found"; exit 1; fi

COUNTS_SQL="SELECT tablename || '=' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', tablename), false, true, '')))[1]::text FROM pg_tables WHERE schemaname='public' ORDER BY tablename"

echo "==> [1/5] pg_dump (custom format) from ${DBPOD}"
kubectl -n "${NS}" exec "${DBPOD}" -- pg_dump -U ofbiz -d ofbiz -Fc -f /tmp/monolith-final.dump
kubectl -n "${NS}" cp "${NS}/${DBPOD}:/tmp/monolith-final.dump" "${OUT}/monolith-final-${STAMP}.dump"

echo "==> [2/5] checksum manifest"
sha256sum "${OUT}/monolith-final-${STAMP}.dump" | awk -v s="${STAMP}" '{print $1"  monolith-final-"s".dump"}' > "${OUT}/manifest-${STAMP}.txt"
kubectl -n "${NS}" exec "${DBPOD}" -- psql -U ofbiz -d ofbiz -Atc "${COUNTS_SQL}" > "${OUT}/counts-source-${STAMP}.txt"
kubectl -n "${NS}" exec "${DBPOD}" -- psql -U ofbiz -d ofbiz -Atc "SELECT version();" | head -1 >> "${OUT}/manifest-${STAMP}.txt"
echo "tables=$(wc -l < "${OUT}/counts-source-${STAMP}.txt" | tr -d ' ')" >> "${OUT}/manifest-${STAMP}.txt"
cat "${OUT}/counts-source-${STAMP}.txt" >> "${OUT}/manifest-${STAMP}.txt"

echo "==> [3/5] restore test (scratch DB ofbiz_restore_test)"
kubectl -n "${NS}" exec "${DBPOD}" -- dropdb -U ofbiz --if-exists ofbiz_restore_test
kubectl -n "${NS}" exec "${DBPOD}" -- createdb -U ofbiz ofbiz_restore_test
kubectl -n "${NS}" exec "${DBPOD}" -- pg_restore -U ofbiz --no-owner -d ofbiz_restore_test /tmp/monolith-final.dump
kubectl -n "${NS}" exec "${DBPOD}" -- psql -U ofbiz -d ofbiz_restore_test -Atc "${COUNTS_SQL}" > "${OUT}/counts-restored-${STAMP}.txt"
if ! diff -u "${OUT}/counts-source-${STAMP}.txt" "${OUT}/counts-restored-${STAMP}.txt"; then
  echo "FAIL: restored table counts differ from source"; exit 1
fi
kubectl -n "${NS}" exec "${DBPOD}" -- dropdb -U ofbiz ofbiz_restore_test
echo "restore parity OK ($(wc -l < "${OUT}/counts-source-${STAMP}.txt" | tr -d ' ') tables)"

echo "==> [4/5] S3 upload (Garage ofbiz-archive)"
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} bucket create ofbiz-archive >/dev/null 2>&1 || true
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key info ofbiz-archive >/dev/null 2>&1 \
  && kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key delete --yes ofbiz-archive >/dev/null 2>&1 || true
KEY_OUT="$(kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key create ofbiz-archive 2>/dev/null)"
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} key allow ofbiz-archive --read --write --create-bucket >/dev/null 2>&1 || true
KEY_ID="$(echo "${KEY_OUT}" | grep -oE 'GK[0-9a-f]+' | head -1)"
SECRET="$(echo "${KEY_OUT}" | grep -oE 'Secret key: +[0-9a-zA-Z+/=]+' | sed 's/Secret key: *//')"
if [ -z "${KEY_ID}" ] || [ -z "${SECRET}" ]; then echo "FAIL: could not issue archive key"; echo "${KEY_OUT}"; exit 1; fi
kubectl -n "${STORAGE_NS}" exec "${GARAGE_POD}" -- ${G} bucket allow --read --write ofbiz-archive --key ofbiz-archive >/dev/null 2>&1 || true

kubectl -n "${NS}" delete pod p7-s3-upload --ignore-not-found >/dev/null 2>&1
kubectl -n "${NS}" run p7-s3-upload --image=amazon/aws-cli:2.23.0 --command -- sleep 900 >/dev/null
kubectl -n "${NS}" wait --for=condition=Ready pod/p7-s3-upload --timeout=180s >/dev/null
# aws-cli image ships no tar (kubectl cp needs it) — stream via stdin instead
kubectl -n "${NS}" exec -i p7-s3-upload -- sh -c 'cat > /tmp/monolith.dump' < "${OUT}/monolith-final-${STAMP}.dump"
kubectl -n "${NS}" exec -i p7-s3-upload -- sh -c 'cat > /tmp/manifest.txt' < "${OUT}/manifest-${STAMP}.txt"
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 cp /tmp/monolith.dump "s3://ofbiz-archive/monolith-db/monolith-final-${STAMP}.dump" >/dev/null
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 cp /tmp/manifest.txt "s3://ofbiz-archive/monolith-db/manifest-${STAMP}.txt" >/dev/null
echo "--- S3 listing (gate evidence) ---"
kubectl -n "${NS}" exec p7-s3-upload -- env AWS_ACCESS_KEY_ID="${KEY_ID}" AWS_SECRET_ACCESS_KEY="${SECRET}" AWS_DEFAULT_REGION=ofbiz-local \
  aws --endpoint-url "http://${SVC}:3900" s3 ls "s3://ofbiz-archive/monolith-db/"
kubectl -n "${NS}" exec p7-s3-upload -- sha256sum /tmp/monolith.dump | awk -v s="${STAMP}" '{print $1"  monolith-final-"s".dump (uploaded copy)"}'
kubectl -n "${NS}" delete pod p7-s3-upload --ignore-not-found >/dev/null

echo "==> [5/5] cleanup"
kubectl -n "${NS}" exec "${DBPOD}" -- rm -f /tmp/monolith-final.dump
echo "PASS: P7-S2 — snapshot + manifest in s3://ofbiz-archive/monolith-db/, restore parity verified"
echo "sha256: $(head -1 "${OUT}/manifest-${STAMP}.txt" | awk '{print $1}')"
echo "local copies kept in ${OUT} until reboot (S3 copy is the record)"
