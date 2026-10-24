#!/usr/bin/env bash
# P7-S1: rebuild + redeploy ONLY the five services whose monolith bridge code
# was removed (gateway, party, product, inventory, order). Env blocks mirror
# platform/services/10-deploy.sh minus the deleted *_MONOLITH_* wiring.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
NS="ofbiz"
TAG="${1:-local}"

KAFKA="ofbiz-kafka-bootstrap.${NS}.svc:9093"
POSTGRES="postgres.${NS}.svc:5432"
ISSUER="http://identity-svc.${NS}.svc:8080"

build_image() { # name gradle-project dir
  local name="$1" gradle="$2" dir="$3"
  echo "  -- ${name}"
  ./gradlew -p "${ROOT}" "${gradle}" >/dev/null
  docker build -q -t "${name}:${TAG}" "${ROOT}/${dir}" >/dev/null
  kind load docker-image "${name}:${TAG}" --name ofbiz >/dev/null
}
echo "==> [1/3] Build + load images (bridge-removal rebuild)"
build_image gateway ":microservices:gateway:bootJar" microservices/gateway
build_image party-svc ":microservices:party-svc:party-adapters:adapters-rest:bootJar" microservices/party-svc
build_image product-svc ":microservices:product-svc:product-adapters:adapters-rest:bootJar" microservices/product-svc
build_image inventory-svc ":microservices:inventory-svc:inventory-adapters:adapters-rest:bootJar" microservices/inventory-svc
build_image order-svc ":microservices:order-svc:order-adapters:adapters-rest:bootJar" microservices/order-svc

kafka_pem_secret() { # kafkauser name
  local user="$1"
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
tls_args() { # kafkauser — single line; caller expands unquoted
  printf '%s ' \
    "--set-string" "extraVolumes[0].name=kafka-tls" \
    "--set-string" "extraVolumes[0].secret.secretName=$1-kafka-pem" \
    "--set-string" "extraVolumeMounts[0].name=kafka-tls" \
    "--set-string" "extraVolumeMounts[0].mountPath=/etc/kafka-tls" \
    "--set-string" "extraEnv[0].name=SPRING_KAFKA_SECURITY_PROTOCOL" "--set-string" "extraEnv[0].value=SSL" \
    "--set-string" "extraEnv[1].name=SPRING_KAFKA_SSL_BUNDLE" "--set-string" "extraEnv[1].value=kafka"
}

echo "==> [2/3] PEM bundles"
for user in party-svc product-svc inventory-svc order-svc; do kafka_pem_secret "${user}"; done

echo "==> [3/3] Helm upgrades"
CHART="${ROOT}/platform/helm/service-chart"

helm -n "${NS}" upgrade --install gateway "${CHART}" \
  --set image.repository=gateway --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=IDENTITY_ISSUER" --set-string "env[1].value=${ISSUER}" \
  --set-string "env[2].name=REDIS_HOST" --set-string "env[2].value=redis.${NS}.svc" \
  --set-string 'env[3].name=REDIS_PORT' --set-string 'env[3].value=6379' \
  --set-string 'env[4].name=REDIS_PASSWORD' --set-string 'env[4].value=' \
  --set-string 'env[5].name=REDIS_SSL' --set-string 'env[5].value=false' \
  --set-string "env[6].name=GATEWAY_DEMO_URI" --set-string "env[6].value=http://demo-svc.${NS}.svc:8080" \
  --set-string 'env[7].name=OTEL_EXPORTER_OTLP_ENDPOINT' --set-string 'env[7].value=http://jaeger-collector.observability.svc:4318/v1/traces' \
  --set-string 'env[8].name=TRACING_SAMPLING' --set-string 'env[8].value=1.0' \
  --set-string "env[9].name=GATEWAY_PRODUCT_URI" --set-string "env[9].value=http://product-svc.${NS}.svc:8080"

TLS_ARGS_PARTY="$(tls_args party-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install party-svc "${CHART}" \
  --set image.repository=party-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set grpcPort=9090 \
  ${TLS_ARGS_PARTY} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=PARTY_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/party" \
  --set-string 'env[2].name=PARTY_DB_USER' --set-string 'env[2].value=party' \
  --set-string 'env[3].name=PARTY_DB_PASSWORD' --set-string 'env[3].value=party' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=PARTY_GRPC_PORT' --set-string 'env[5].value=9090' \
  --set-string 'env[6].name=JAVA_TOOL_OPTIONS' --set-string 'env[6].value=-XX:MaxRAMPercentage=75.0'

TLS_ARGS_PRODUCT="$(tls_args product-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install product-svc "${CHART}" \
  --set image.repository=product-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_PRODUCT} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=PRODUCT_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/product" \
  --set-string 'env[2].name=PRODUCT_DB_USER' --set-string 'env[2].value=product' \
  --set-string 'env[3].name=PRODUCT_DB_PASSWORD' --set-string 'env[3].value=product' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'

TLS_ARGS_INVENTORY="$(tls_args inventory-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install inventory-svc "${CHART}" \
  --set image.repository=inventory-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_INVENTORY} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=INVENTORY_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/inventory" \
  --set-string 'env[2].name=INVENTORY_DB_USER' --set-string 'env[2].value=inventory' \
  --set-string 'env[3].name=INVENTORY_DB_PASSWORD' --set-string 'env[3].value=inventory' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'

TLS_ARGS_ORDER="$(tls_args order-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install order-svc "${CHART}" \
  --set image.repository=order-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_ORDER} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=ORDER_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/orderdb" \
  --set-string 'env[2].name=ORDER_DB_USER' --set-string 'env[2].value=orderdb' \
  --set-string 'env[3].name=ORDER_DB_PASSWORD' --set-string 'env[3].value=orderdb' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string "env[5].name=ORDER_PARTY_BASE_URL" --set-string "env[5].value=http://party-svc.${NS}.svc:8080" \
  --set-string "env[6].name=ORDER_PRODUCT_BASE_URL" --set-string "env[6].value=http://product-svc.${NS}.svc:8080" \
  --set-string "env[7].name=ORDER_INVENTORY_BASE_URL" --set-string "env[7].value=http://inventory-svc.${NS}.svc:8080" \
  --set-string 'env[8].name=JAVA_TOOL_OPTIONS' --set-string 'env[8].value=-XX:MaxRAMPercentage=75.0'

for deploy in gateway party-svc product-svc inventory-svc order-svc; do
  kubectl -n "${NS}" rollout status deploy/"${deploy}" --timeout=300s
done
echo "PASS: P7 redeploy — bridge-removal images live, monolith wiring gone"
