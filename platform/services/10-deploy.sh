#!/usr/bin/env bash
# P1-F6-S2: deploy all five platform services onto the local kind cluster.
#   1. backing stores (postgres DB-per-service, redis, mailhog)
#   2. topics (search.doc.indexed.v1 + DLQ, KafkaUsers + least-privilege ACLs)
#   3. bootJar + distroless image + kind load per service
#   4. helm upgrade via the shared service-chart (mesh-injected, 1 replica)
# Kafka access is mutual-TLS: the KafkaUser secrets are reshaped into PEM
# bundles (ca.crt + user.pem) and mounted, matching the enforced ACLs.
# Local profile uses dev credentials inline; cloud swaps to managed stores.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
NS="ofbiz"
TAG="${1:-local}"

KAFKA="ofbiz-kafka-bootstrap.${NS}.svc:9093"
POSTGRES="postgres.${NS}.svc:5432"
ISSUER="http://identity-svc.${NS}.svc:8080"
CLIENT_SECRET="dev-local-secret"

echo "==> [1/4] Backing stores"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/manifests/"
kubectl -n "${NS}" rollout status statefulset/postgres --timeout=300s
kubectl -n "${NS}" rollout status deploy/redis --timeout=180s
kubectl -n "${NS}" rollout status deploy/mailhog --timeout=180s
# P2: party database (init.sql runs only on first volume init — make it idempotent)
kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -tc \
  "SELECT 1 FROM pg_roles WHERE rolname='party'" | grep -q 1 \
  || kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -c "CREATE ROLE party LOGIN PASSWORD 'party'"
kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -tc \
  "SELECT 1 FROM pg_database WHERE datname='party'" | grep -q 1 \
  || kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -c "CREATE DATABASE party OWNER party"

echo "==> [2/4] Topics + KafkaUsers"
sh "${ROOT}/platform/helm/topics/10-create.sh"

echo "==> [3/4] Build + load images"
build_image() { # name gradle-project dir
  local name="$1" gradle="$2" dir="$3"
  echo "  -- ${name}"
  ./gradlew -p "${ROOT}" "${gradle}" >/dev/null
  docker build -q -t "${name}:${TAG}" "${ROOT}/${dir}" >/dev/null
  kind load docker-image "${name}:${TAG}" --name ofbiz >/dev/null
}
build_image identity-svc ":microservices:identity-svc:identity-adapters:adapters-rest:bootJar" microservices/identity-svc
build_image gateway ":microservices:gateway:bootJar" microservices/gateway
build_image audit-svc ":microservices:audit-svc:audit-adapters:adapters-rest:bootJar" microservices/audit-svc
build_image notification-svc ":microservices:notification-svc:notification-adapters:adapters-rest:bootJar" microservices/notification-svc
build_image search-svc ":microservices:search-svc:search-adapters:adapters-rest:bootJar" microservices/search-svc
build_image party-svc ":microservices:party-svc:party-adapters:adapters-rest:bootJar" microservices/party-svc

# Reshape a Strimzi KafkaUser secret into the file layout Boot 4 pem ssl
# bundles expect: ca.crt (truststore) + user.crt/user.key (keystore).
kafka_pem_secret() { # kafkauser name
  local user="$1"
  # truststore carries BOTH CAs: brokers serve cluster-CA certs while
  # KafkaUser secrets ship the clients CA — one file validates either chain
  kubectl -n "${NS}" get secret ofbiz-cluster-ca-cert -o 'jsonpath={.data.ca\.crt}' | base64 -d > /tmp/kafka-ca.crt
  kubectl -n "${NS}" get secret "${user}" -o 'jsonpath={.data.ca\.crt}' | base64 -d >> /tmp/kafka-ca.crt
  kubectl -n "${NS}" get secret "${user}" -o 'jsonpath={.data.user\.key}' | base64 -d > /tmp/kafka-user.key
  kubectl -n "${NS}" get secret "${user}" -o 'jsonpath={.data.user\.crt}' | base64 -d > /tmp/kafka-user.crt
  kubectl -n "${NS}" create secret generic "${user}-kafka-pem" \
    --from-file=ca.crt=/tmp/kafka-ca.crt --from-file=user.crt=/tmp/kafka-user.crt \
    --from-file=user.key=/tmp/kafka-user.key \
    --dry-run=client -o yaml | kubectl -n "${NS}" apply -f -
  rm -f /tmp/kafka-ca.crt /tmp/kafka-user.key /tmp/kafka-user.crt
}
for user in identity-svc audit-svc notification-svc search-svc party-svc; do kafka_pem_secret "${user}"; done

# helm args to mount the PEM bundle and speak SSL to Kafka. The pem bundle
# itself is declared in each service's application.yaml (env vars cannot
# bind the spring.ssl.bundle map key); only activation goes through env.
tls_args() { # kafkauser — single line; caller expands unquoted
  printf '%s ' \
    "--set-string" "extraVolumes[0].name=kafka-tls" \
    "--set-string" "extraVolumes[0].secret.secretName=$1-kafka-pem" \
    "--set-string" "extraVolumeMounts[0].name=kafka-tls" \
    "--set-string" "extraVolumeMounts[0].mountPath=/etc/kafka-tls" \
    "--set-string" "extraEnv[0].name=SPRING_KAFKA_SECURITY_PROTOCOL" "--set-string" "extraEnv[0].value=SSL" \
    "--set-string" "extraEnv[1].name=SPRING_KAFKA_SSL_BUNDLE" "--set-string" "extraEnv[1].value=kafka"
}

echo "==> [4/4] Helm releases (prod profiles, env-injected config)"
CHART="${ROOT}/platform/helm/service-chart"

TLS_ARGS_IDENTITY="$(tls_args identity-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install identity-svc "${CHART}" \
  --set image.repository=identity-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_IDENTITY} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=IDENTITY_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/identity" \
  --set-string 'env[2].name=IDENTITY_DB_USER' --set-string 'env[2].value=identity' \
  --set-string 'env[3].name=IDENTITY_DB_PASSWORD' --set-string 'env[3].value=identity' \
  --set-string "env[4].name=IDENTITY_ISSUER" --set-string "env[4].value=${ISSUER}" \
  --set-string 'env[5].name=GATEWAY_CLIENT_ID' --set-string 'env[5].value=gateway' \
  --set-string 'env[6].name=GATEWAY_CLIENT_SECRET' --set-string "env[6].value=\\{noop\\}${CLIENT_SECRET}" \
  --set-string 'env[7].name=GATEWAY_REDIRECT_URI' --set-string 'env[7].value=http://localhost:4200/login/oauth2/code/gateway' \
  --set-string "env[8].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[8].value=${KAFKA}"

helm -n "${NS}" upgrade --install gateway "${CHART}" \
  --set image.repository=gateway --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=IDENTITY_ISSUER" --set-string "env[1].value=${ISSUER}" \
  --set-string 'env[2].name=REDIS_HOST' --set-string "env[2].value=redis.${NS}.svc" \
  --set-string 'env[3].name=REDIS_PORT' --set-string 'env[3].value=6379' \
  --set-string 'env[4].name=REDIS_PASSWORD' --set-string 'env[4].value=' \
  --set-string 'env[5].name=REDIS_SSL' --set-string 'env[5].value=false' \
  --set-string "env[6].name=GATEWAY_DEMO_URI" --set-string "env[6].value=http://demo-svc.${NS}.svc:8080" \
  --set-string "env[7].name=OTEL_EXPORTER_OTLP_ENDPOINT" --set-string "env[7].value=http://jaeger-collector.observability.svc:4318/v1/traces" \
  --set-string 'env[8].name=TRACING_SAMPLING' --set-string 'env[8].value=1.0'

TLS_ARGS_AUDIT="$(tls_args audit-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install audit-svc "${CHART}" \
  --set image.repository=audit-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_AUDIT} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=AUDIT_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/audit" \
  --set-string 'env[2].name=AUDIT_DB_USER' --set-string 'env[2].value=audit' \
  --set-string 'env[3].name=AUDIT_DB_PASSWORD' --set-string 'env[3].value=audit' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}"

TLS_ARGS_NOTIFICATION="$(tls_args notification-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install notification-svc "${CHART}" \
  --set image.repository=notification-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_NOTIFICATION} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=NOTIFICATION_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/notification" \
  --set-string 'env[2].name=NOTIFICATION_DB_USER' --set-string 'env[2].value=notification' \
  --set-string 'env[3].name=NOTIFICATION_DB_PASSWORD' --set-string 'env[3].value=notification' \
  --set-string 'env[4].name=SMTP_HOST' --set-string "env[4].value=mailhog.${NS}.svc" \
  --set-string 'env[5].name=SMTP_PORT' --set-string 'env[5].value=1025' \
  --set-string 'env[6].name=SMTP_USER' --set-string 'env[6].value=' \
  --set-string 'env[7].name=SMTP_PASSWORD' --set-string 'env[7].value=' \
  --set-string "env[8].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[8].value=${KAFKA}"

TLS_ARGS_SEARCH="$(tls_args search-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install search-svc "${CHART}" \
  --set image.repository=search-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set grpcPort=9090 \
  ${TLS_ARGS_SEARCH} \
  --set-string 'env[0].name=OPENSEARCH_URIS' --set-string 'env[0].value=http://opensearch.observability.svc:9200' \
  --set-string "env[1].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[1].value=${KAFKA}" \
  --set-string 'env[2].name=SEARCH_GRPC_PORT' --set-string 'env[2].value=9090'

TLS_ARGS_PARTY="$(tls_args party-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install party-svc "${CHART}" \
  --set image.repository=party-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set grpcPort=9090 \
  ${TLS_ARGS_PARTY} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=PARTY_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/party" \
  --set-string 'env[2].name=PARTY_DB_USER' --set-string 'env[2].value=party' \
  --set-string 'env[3].name=PARTY_DB_PASSWORD' --set-string 'env[3].value=party' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=PARTY_GRPC_PORT' --set-string 'env[5].value=9090' \
  --set-string 'env[6].name=PARTY_MONOLITH_SYNC_ENABLED' --set-string 'env[6].value=true' \
  --set-string 'env[7].name=PARTY_MONOLITH_DATASOURCE_URL' --set-string "env[7].value=jdbc:postgresql://monolith-db.${NS}.svc:5432/ofbiz" \
  --set-string 'env[8].name=PARTY_MONOLITH_DATASOURCE_USERNAME' --set-string 'env[8].value=ofbiz' \
  --set-string 'env[9].name=PARTY_MONOLITH_DATASOURCE_PASSWORD' --set-string 'env[9].value=ofbiz' \
  --set-string 'env[10].name=JAVA_TOOL_OPTIONS' --set-string 'env[10].value=-XX:MaxRAMPercentage=75.0'

for deploy in identity-svc gateway audit-svc notification-svc search-svc party-svc; do
  kubectl -n "${NS}" rollout status "deploy/${deploy}" --timeout=300s
done

echo "DONE — six services deployed (P1-F6-S2 + P2 party-svc)"
