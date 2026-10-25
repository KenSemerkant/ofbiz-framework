#!/usr/bin/env bash
# P1-F6: seed demo data for development and demos. Idempotent — safe to re-run.
#   1. identity: admin + demo users (monolith-compatible PBKDF2 credentials)
#   2. search:   five demo documents on search.doc.indexed.v1
#   3. notification: welcome emails (en + de) queued via notification.requested.v1
#   4. audit:    a few sample events on the audit topic
# Requires the stack from platform/services/10-deploy.sh to be running.
set -euo pipefail
NS="ofbiz"
TOOLS="p1f6-seed-tools"

echo "==> 0. tools pod"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 900 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done

echo "==> 1. identity demo users (postgres)"
# monolith HashCrypt format {PBKDF2-SHA256}iter$b64(salt)$b64(hash), computed
# locally with a fixed dev salt so the seed is deterministic and idempotent
python3 - "$NS" <<'EOF'
import base64, hashlib, subprocess, sys
NS = sys.argv[1]

def pbkdf2_hash(password, salt_b64, iterations=10_000):
    salt = base64.b64decode(salt_b64)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, iterations, dklen=32)
    return "{PBKDF2-SHA256}%d$%s$%s" % (iterations, salt_b64, base64.b64encode(dk).decode())

SALT = base64.b64encode(b"ofbiz-dev-seed-salt").decode()  # dev-only fixed salt
users = [
    ("admin",     "admin-password", "ADMIN", "demo-party-admin"),
    ("demo-user", "demo-password",  "USER",  "demo-party-user"),
    ("demo-user-de", "demo-password", "USER", "demo-party-de"),
]
statements = []
for login, pw, role, party in users:
    h = pbkdf2_hash(pw, SALT)
    statements.append(
        "INSERT INTO user_logins (user_login_id, party_id, current_password, roles) "
        "VALUES ('%s', '%s', '%s', '%s') "
        "ON CONFLICT (user_login_id) DO NOTHING;" % (login, party, h, role))
sql = "\n".join(statements)
subprocess.run(["kubectl", "-n", NS, "exec", "-i", "postgres-0", "--", "psql",
                "-U", "identity", "-d", "identity", "-v", "ON_ERROR_STOP=1"],
               input=sql.encode(), check=True, capture_output=True)
print("  seeded: " + ", ".join(u[0] for u in users))
EOF

echo "==> 2. mTLS producer credentials (search-svc principal)"
TMP=$(mktemp -d)
kubectl -n "${NS}" get secret ofbiz-cluster-ca-cert -o 'jsonpath={.data.ca\.crt}' | base64 -d > "${TMP}/ca.crt"
kubectl -n "${NS}" get secret search-svc -o 'jsonpath={.data.user\.key}' | base64 -d > "${TMP}/user.key"
kubectl -n "${NS}" get secret search-svc -o 'jsonpath={.data.user\.crt}' | base64 -d > "${TMP}/user.crt"
cat "${TMP}/user.key" "${TMP}/user.crt" > "${TMP}/user.pem"
printf 'security.protocol=SSL\nssl.truststore.type=PEM\nssl.truststore.location=/tmp/kafka-tls/ca.crt\nssl.keystore.type=PEM\nssl.keystore.location=/tmp/kafka-tls/user.pem\n' > "${TMP}/client.properties"
kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- rm -rf /tmp/kafka-tls >/dev/null 2>&1 || true
kubectl -n "${NS}" exec ofbiz-ofbiz-nodes-0 -- mkdir -p /tmp/kafka-tls >/dev/null
for f in ca.crt user.pem client.properties; do
  kubectl -n "${NS}" cp "${TMP}/${f}" "ofbiz-ofbiz-nodes-0:/tmp/kafka-tls/${f}" >/dev/null 2>&1
done
rm -rf "${TMP}"
produce() { # topic payload...
  local topic="$1"; shift
  for payload in "$@"; do
    echo "${payload}" | kubectl -n "${NS}" exec -i ofbiz-ofbiz-nodes-0 -- \
      bin/kafka-console-producer.sh \
      --bootstrap-server ofbiz-kafka-bootstrap.ofbiz.svc:9093 \
      --topic "${topic}" --producer.config /tmp/kafka-tls/client.properties >/dev/null 2>&1
  done
}

echo "==> 3. search demo documents"
produce search.doc.indexed.v1 \
  '{"docId":"demo-laptop-thinkpad","fields":{"name":"ThinkPad X1 Carbon laptop","category":"laptop","price":"1899"}}' \
  '{"docId":"demo-laptop-macbook","fields":{"name":"MacBook Pro 14 laptop","category":"laptop","price":"2399"}}' \
  '{"docId":"demo-laptop-xps","fields":{"name":"Dell XPS 15 laptop","category":"laptop","price":"1699"}}' \
  '{"docId":"demo-monitor-ultra","fields":{"name":"UltraSharp 32 monitor","category":"monitor","price":"799"}}' \
  '{"docId":"demo-party-acme","fields":{"name":"Acme Corporation party","category":"party","country":"DE"}}'
echo "  produced 5 documents"

echo "==> 3b. demo party CUSTOMER role (party-svc; identity projection picks it up <5s)"
kubectl -n "${NS}" exec "${TOOLS}" -- curl -s -o /dev/null -X POST http://party-svc:8080/api/v1/parties \
  -H 'Content-Type: application/json' \
  -d '{"partyId":"demo-party-user","kind":"PERSON","firstName":"Demo","lastName":"User","roles":["CUSTOMER"]}'
echo "  demo-party-user upserted with CUSTOMER (gateway required-role on SPA routes)"

echo "==> 4. notification demo emails (mailhog sink)"
TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
produce notification.requested.v1 \
  "{\"notificationId\":\"seed-welcome-en-${RANDOM}\",\"channel\":\"EMAIL\",\"recipient\":\"demo-user@example.com\",\"template\":\"welcome\",\"locale\":\"en\",\"params\":{\"name\":\"Demo User\"}}" \
  "{\"notificationId\":\"seed-welcome-de-${RANDOM}\",\"channel\":\"EMAIL\",\"recipient\":\"demo-user-de@example.com\",\"template\":\"welcome\",\"locale\":\"de\",\"params\":{\"name\":\"Demo Nutzer\"}}"
echo "  produced 2 welcome notifications (check mailhog UI)"

echo "==> 5. audit demo events"
produce audit.auditevent.recorded.v1 \
  "{\"eventId\":\"seed-audit-1-${RANDOM}\",\"topic\":\"demo.seed\",\"severity\":\"INFO\",\"message\":\"demo data seeded\",\"occurredAt\":\"${TS}\"}" \
  "{\"eventId\":\"seed-audit-2-${RANDOM}\",\"topic\":\"demo.seed\",\"severity\":\"HIGH\",\"message\":\"HIGH-severity demo alert fires notification\",\"occurredAt\":\"${TS}\"}"
echo "  produced 2 audit events (one HIGH → notification alert)"

echo "==> Summary"
sleep 5
echo "search: $(kubectl -n ${NS} exec ${TOOLS} -- curl -s -m 5 'http://gateway:8080/api/v1/search?q=laptop&limit=10' | head -c 200)"
echo "mailhog UI: kubectl -n ${NS} port-forward svc/mailhog 8025:8025"
echo "admin login (identity http-basic admin API): admin / admin-password"
echo "DONE — demo data seeded"
