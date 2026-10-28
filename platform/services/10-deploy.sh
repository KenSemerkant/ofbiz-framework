#!/usr/bin/env bash
# P1-F6-S2: deploy all five platform services onto the local kind cluster.
#   P2 added party-svc; P3-GATE adds product-svc + inventory-svc and the
#   search database (P3-F3-S1 consumer state).
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

# P11-F2 subset deployment: ./10-deploy.sh [tag] [--services=svc1,svc2,...]
# No flag = all sixteen services (backward compatible). identity-svc and
# gateway are always deployed (platform floor: OIDC issuer + edge routing).
# Sync dependencies are validated, never auto-added: order-svc requires
# party/product/inventory (placeOrder saga); humanres-svc requires party-svc
# (onboarding role grant). Event-only consumers idle when their producers
# are absent — safe to omit freely.
TAG="local"
SERVICES="all"
for arg in "$@"; do
  case "${arg}" in
    --services=*) SERVICES="${arg#--services=}" ;;
    *) TAG="${arg}" ;;
  esac
done

ALL_SERVICES="identity-svc gateway audit-svc notification-svc search-svc party-svc product-svc inventory-svc order-svc billing-svc finance-svc manufacturing-svc workeffort-svc humanres-svc content-svc marketing-svc"

want() { # 0 iff service is in the selection (no selection = all)
  if [ "${SERVICES}" = "all" ]; then return 0; fi
  case ",${SERVICES}," in *",$1,"*) return 0 ;; esac
  return 1
}

is_known() { # 0 iff name is one of the sixteen services
  for k in ${ALL_SERVICES}; do
    if [ "${k}" = "$1" ]; then return 0; fi
  done
  return 1
}

if [ "${SERVICES}" != "all" ]; then
  for s in ${SERVICES//,/ }; do
    if ! is_known "${s}"; then
      echo "ERROR: unknown service '${s}'. Valid: ${ALL_SERVICES}" >&2
      exit 1
    fi
  done
  SERVICES="identity-svc,gateway,${SERVICES}" # platform floor
  if want order-svc; then
    for dep in party-svc product-svc inventory-svc; do
      want "${dep}" || { echo "ERROR: order-svc requires ${dep} (placeOrder saga calls it synchronously). Add it to --services." >&2; exit 1; }
    done
  fi
  if want humanres-svc && ! want party-svc; then
    echo "ERROR: humanres-svc requires party-svc (onboarding grants EMPLOYEE role via HTTP). Add it to --services." >&2
    exit 1
  fi
  echo "==> Subset deploy: ${SERVICES} (identity-svc + gateway forced as platform floor)"
fi

KAFKA="ofbiz-kafka-bootstrap.${NS}.svc:9093"
POSTGRES="postgres.${NS}.svc:5432"
ISSUER="http://identity-svc.${NS}.svc:8080"
CLIENT_SECRET="dev-local-secret"

echo "==> [1/4] Backing stores"
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/manifests/"
kubectl -n "${NS}" rollout status statefulset/postgres --timeout=300s
kubectl -n "${NS}" rollout status deploy/redis --timeout=180s
kubectl -n "${NS}" rollout status deploy/mailhog --timeout=180s
# P2: party database; P3: product/inventory/search databases (init.sql runs
# only on first volume init — make it idempotent)
ensure_db() { # role=password=dbname
  kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -tc \
    "SELECT 1 FROM pg_roles WHERE rolname='$1'" | grep -q 1 \
    || kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -c \
      "CREATE ROLE $1 LOGIN PASSWORD '$1'"
  kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -tc \
    "SELECT 1 FROM pg_database WHERE datname='$1'" | grep -q 1 \
    || kubectl -n "${NS}" exec postgres-0 -- psql -U admin -d postgres -c \
      "CREATE DATABASE $1 OWNER $1"
}
# P11-F2: only the databases of selected services
DB_OF="party-svc:party product-svc:product inventory-svc:inventory search-svc:search order-svc:orderdb billing-svc:billing finance-svc:finance manufacturing-svc:manufacturing workeffort-svc:workeffort humanres-svc:humanres content-svc:content marketing-svc:marketing"
for pair in ${DB_OF}; do
  if want "${pair%%:*}"; then ensure_db "${pair##*:}"; fi
done

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
# identity-svc + gateway are the always-deployed platform floor
build_image identity-svc ":microservices:identity-svc:identity-adapters:adapters-rest:bootJar" microservices/identity-svc
build_image gateway ":microservices:gateway:bootJar" microservices/gateway
if want audit-svc; then build_image audit-svc ":microservices:audit-svc:audit-adapters:adapters-rest:bootJar" microservices/audit-svc; fi
if want notification-svc; then build_image notification-svc ":microservices:notification-svc:notification-adapters:adapters-rest:bootJar" microservices/notification-svc; fi
if want search-svc; then build_image search-svc ":microservices:search-svc:search-adapters:adapters-rest:bootJar" microservices/search-svc; fi
if want party-svc; then build_image party-svc ":microservices:party-svc:party-adapters:adapters-rest:bootJar" microservices/party-svc; fi
if want product-svc; then build_image product-svc ":microservices:product-svc:product-adapters:adapters-rest:bootJar" microservices/product-svc; fi
if want inventory-svc; then build_image inventory-svc ":microservices:inventory-svc:inventory-adapters:adapters-rest:bootJar" microservices/inventory-svc; fi
if want order-svc; then build_image order-svc ":microservices:order-svc:order-adapters:adapters-rest:bootJar" microservices/order-svc; fi
if want billing-svc; then build_image billing-svc ":microservices:billing-svc:billing-adapters:adapters-rest:bootJar" microservices/billing-svc; fi
if want finance-svc; then build_image finance-svc ":microservices:finance-svc:finance-adapters:adapters-rest:bootJar" microservices/finance-svc; fi
if want manufacturing-svc; then build_image manufacturing-svc ":microservices:manufacturing-svc:manufacturing-adapters:adapters-rest:bootJar" microservices/manufacturing-svc; fi
if want workeffort-svc; then build_image workeffort-svc ":microservices:workeffort-svc:workeffort-adapters:adapters-rest:bootJar" microservices/workeffort-svc; fi
if want humanres-svc; then build_image humanres-svc ":microservices:humanres-svc:humanres-adapters:adapters-rest:bootJar" microservices/humanres-svc; fi
if want content-svc; then build_image content-svc ":microservices:content-svc:content-adapters:adapters-rest:bootJar" microservices/content-svc; fi
if want marketing-svc; then build_image marketing-svc ":microservices:marketing-svc:marketing-adapters:adapters-rest:bootJar" microservices/marketing-svc; fi

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
for user in ${ALL_SERVICES}; do
  if [ "${user}" = "gateway" ]; then continue; fi # gateway holds no KafkaUser
  if want "${user}"; then kafka_pem_secret "${user}"; fi
done

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
  --set-string 'env[8].name=TRACING_SAMPLING' --set-string 'env[8].value=1.0' \
  --set-string 'env[9].name=GATEWAY_PRODUCT_URI' --set-string "env[9].value=http://product-svc.${NS}.svc:8080"

if want audit-svc; then
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
fi

# P5: staging mail (mailpit) speaks SMTP on 8080 + the eager client-first
# adapter — the local kind datapath eats banner-first SMTP (issues/P5-GATE.md).
if want notification-svc; then
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
  --set-string 'env[5].name=SMTP_PORT' --set-string 'env[5].value=8080' \
  --set-string 'env[9].name=NOTIFICATION_MAIL_EAGER' --set-string 'env[9].value=true' \
  --set-string 'env[6].name=SMTP_USER' --set-string 'env[6].value=' \
  --set-string 'env[7].name=SMTP_PASSWORD' --set-string 'env[7].value=' \
  --set-string "env[8].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[8].value=${KAFKA}"
fi

if want search-svc; then
TLS_ARGS_SEARCH="$(tls_args search-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install search-svc "${CHART}" \
  --set image.repository=search-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  --set grpcPort=9090 \
  ${TLS_ARGS_SEARCH} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=OPENSEARCH_URIS' --set-string 'env[1].value=http://opensearch.observability.svc:9200' \
  --set-string "env[2].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[2].value=${KAFKA}" \
  --set-string 'env[3].name=SEARCH_GRPC_PORT' --set-string 'env[3].value=9090' \
  --set-string 'env[4].name=SEARCH_DB_URL' --set-string "env[4].value=jdbc:postgresql://${POSTGRES}/search" \
  --set-string 'env[5].name=SEARCH_DB_USER' --set-string 'env[5].value=search' \
  --set-string 'env[6].name=SEARCH_DB_PASSWORD' --set-string 'env[6].value=search' \
  --set-string 'env[7].name=JAVA_TOOL_OPTIONS' --set-string 'env[7].value=-XX:MaxRAMPercentage=75.0'
fi

if want party-svc; then
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
  --set-string 'env[6].name=JAVA_TOOL_OPTIONS' --set-string 'env[6].value=-XX:MaxRAMPercentage=75.0'
fi

if want product-svc; then
TLS_ARGS_PRODUCT="$(tls_args product-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install product-svc "${CHART}" \
  --set image.repository=product-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_PRODUCT} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=PRODUCT_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/product" \
  --set-string 'env[2].name=PRODUCT_DB_USER' --set-string 'env[2].value=product' \
  --set-string 'env[3].name=PRODUCT_DB_PASSWORD' --set-string 'env[3].value=product' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

# P4-GATE: order-svc — own DB,
# east-west integration URLs for the placeOrder saga (party/product/inventory).
if want inventory-svc; then
TLS_ARGS_INVENTORY="$(tls_args inventory-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install inventory-svc "${CHART}" \
  --set image.repository=inventory-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_INVENTORY} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string 'env[1].name=INVENTORY_DB_URL' --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/inventory" \
  --set-string 'env[2].name=INVENTORY_DB_USER' --set-string 'env[2].value=inventory' \
  --set-string 'env[3].name=INVENTORY_DB_PASSWORD' --set-string 'env[3].value=inventory' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

if want order-svc; then
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
fi

# P5-GATE: billing-svc — own DB, Kafka for the P4 invoicerequested
# handshake + notification.requested emit.
if want billing-svc; then
TLS_ARGS_BILLING="$(tls_args billing-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install billing-svc "${CHART}" \
  --set image.repository=billing-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_BILLING} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=BILLING_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/billing" \
  --set-string 'env[2].name=BILLING_DB_USER' --set-string 'env[2].value=billing' \
  --set-string 'env[3].name=BILLING_DB_PASSWORD' --set-string 'env[3].value=billing' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

# P5-GATE: finance-svc — own DB, consumes billing invoice events.
if want finance-svc; then
TLS_ARGS_FINANCE="$(tls_args finance-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install finance-svc "${CHART}" \
  --set image.repository=finance-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_FINANCE} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=FINANCE_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/finance" \
  --set-string 'env[2].name=FINANCE_DB_USER' --set-string 'env[2].value=finance' \
  --set-string 'env[3].name=FINANCE_DB_PASSWORD' --set-string 'env[3].value=finance' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

# P6-GATE: long-tail services — own DBs; manufacturing/marketing consume
# order+inventory events, humanres consumes party events + calls party-svc,
# content uses the Garage S3 API (ADR-008).
if want manufacturing-svc; then
TLS_ARGS_MFG="$(tls_args manufacturing-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install manufacturing-svc "${CHART}" \
  --set image.repository=manufacturing-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_MFG} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=MANUFACTURING_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/manufacturing" \
  --set-string 'env[2].name=MANUFACTURING_DB_USER' --set-string 'env[2].value=manufacturing' \
  --set-string 'env[3].name=MANUFACTURING_DB_PASSWORD' --set-string 'env[3].value=manufacturing' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

if want workeffort-svc; then
TLS_ARGS_WE="$(tls_args workeffort-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install workeffort-svc "${CHART}" \
  --set image.repository=workeffort-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_WE} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=WORKEFFORT_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/workeffort" \
  --set-string 'env[2].name=WORKEFFORT_DB_USER' --set-string 'env[2].value=workeffort' \
  --set-string 'env[3].name=WORKEFFORT_DB_PASSWORD' --set-string 'env[3].value=workeffort' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

if want humanres-svc; then
TLS_ARGS_HR="$(tls_args humanres-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install humanres-svc "${CHART}" \
  --set image.repository=humanres-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_HR} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=HUMANRES_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/humanres" \
  --set-string 'env[2].name=HUMANRES_DB_USER' --set-string 'env[2].value=humanres' \
  --set-string 'env[3].name=HUMANRES_DB_PASSWORD' --set-string 'env[3].value=humanres' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string "env[5].name=HUMANRES_PARTY_BASE_URL" --set-string "env[5].value=http://party-svc.${NS}.svc:8080" \
  --set-string 'env[6].name=JAVA_TOOL_OPTIONS' --set-string 'env[6].value=-XX:MaxRAMPercentage=75.0'
fi

# content-svc object storage: (re)issue the Garage ofbiz-s3 key — same
# rotation semantics as platform/storage/20-verify.sh (secret prints only
# at creation, so we recreate it on every deploy run).
if want content-svc; then
GARAGE_POD="garage-0"
kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml key info ofbiz-s3 >/dev/null 2>&1 \
  && kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml key delete --yes ofbiz-s3 >/dev/null 2>&1 || true
GARAGE_KEY_OUT="$(kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml key create ofbiz-s3 2>/dev/null)"
GARAGE_KEY_ID="$(echo "${GARAGE_KEY_OUT}" | grep -oE 'GK[0-9a-f]+' | head -1)"
GARAGE_SECRET="$(echo "${GARAGE_KEY_OUT}" | grep -oE 'Secret key: +[0-9a-zA-Z+/=]+' | sed 's/Secret key: *//')"
kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml key allow ofbiz-s3 --read --write --create-bucket >/dev/null 2>&1 || true
kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml bucket create ofbiz-content >/dev/null 2>&1 || true
kubectl -n storage exec "${GARAGE_POD}" -- /garage -c /etc/garage.toml bucket allow --read --write ofbiz-content --key ofbiz-s3 >/dev/null 2>&1 || true

TLS_ARGS_CONTENT="$(tls_args content-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install content-svc "${CHART}" \
  --set image.repository=content-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_CONTENT} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=CONTENT_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/content" \
  --set-string 'env[2].name=CONTENT_DB_USER' --set-string 'env[2].value=content' \
  --set-string 'env[3].name=CONTENT_DB_PASSWORD' --set-string 'env[3].value=content' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=CONTENT_S3_ENDPOINT' --set-string 'env[5].value=http://garage.storage.svc:3900' \
  --set-string "env[6].name=CONTENT_S3_ACCESS_KEY" --set-string "env[6].value=${GARAGE_KEY_ID}" \
  --set-string "env[7].name=CONTENT_S3_SECRET_KEY" --set-string "env[7].value=${GARAGE_SECRET}" \
  --set-string 'env[8].name=CONTENT_S3_BUCKET' --set-string 'env[8].value=ofbiz-content' \
  --set-string 'env[9].name=JAVA_TOOL_OPTIONS' --set-string 'env[9].value=-XX:MaxRAMPercentage=75.0'
fi

if want marketing-svc; then
TLS_ARGS_MKT="$(tls_args marketing-svc)"
# shellcheck disable=SC2086
helm -n "${NS}" upgrade --install marketing-svc "${CHART}" \
  --set image.repository=marketing-svc --set image.tag="${TAG}" \
  --set replicaCount=1 --set autoscaling.enabled=false --set podDisruptionBudget.enabled=false \
  ${TLS_ARGS_MKT} \
  --set-string 'env[0].name=SPRING_PROFILES_ACTIVE' --set-string 'env[0].value=prod' \
  --set-string "env[1].name=MARKETING_DB_URL" --set-string "env[1].value=jdbc:postgresql://${POSTGRES}/marketing" \
  --set-string 'env[2].name=MARKETING_DB_USER' --set-string 'env[2].value=marketing' \
  --set-string 'env[3].name=MARKETING_DB_PASSWORD' --set-string 'env[3].value=marketing' \
  --set-string "env[4].name=KAFKA_BOOTSTRAP_SERVERS" --set-string "env[4].value=${KAFKA}" \
  --set-string 'env[5].name=JAVA_TOOL_OPTIONS' --set-string 'env[5].value=-XX:MaxRAMPercentage=75.0'
fi

for deploy in ${ALL_SERVICES}; do
  if want "${deploy}"; then
    kubectl -n "${NS}" rollout status "deploy/${deploy}" --timeout=300s
  fi
done

if [ "${SERVICES}" = "all" ]; then
  echo "DONE — sixteen services deployed (P1..P6: platform + party + product/inventory + order + billing/finance + long-tail)"
else
  echo "DONE — subset deployed: ${SERVICES}"
fi
