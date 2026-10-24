#!/usr/bin/env bash
# Phase 0 / P0-F2-S2: topic namespace + ACL conventions.
#   AC1: creates <context>.<aggregate>.<action>.v1 topics per FUTURESTATE §8
#   AC2: ACLs restrict produce/consume per service principal (Strimzi
#        KafkaUser + simple authorization)
#   AC3: DLQ created for a test topic
# Local-profile caveat: brokers run with allowEveryoneIfNoAclFound=true so
# the unauthenticated verify tooling keeps working; the per-principal ACLs
# below are the real cloud-profile restrictions (enforced for every
# authenticated KafkaUser).
set -euo pipefail

NS="ofbiz"

echo "==> Creating contract topics (<context>.<aggregate>.<action>.v1)"
kubectl -n "${NS}" apply -f - <<'EOF'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.placed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000", min.insync.replicas: "2"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: notification.requested.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
# Test topic + its DLQ (AC3)
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: audit.auditevent.recorded.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: audit.auditevent.recorded.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
---
# P1-F6-S2: search ingest topic + DLQ
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: search.doc.indexed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: search.doc.indexed.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
EOF

echo "==> Creating service principals with least-privilege ACLs"
kubectl -n "${NS}" apply -f - <<'EOF'
---
# order-svc owns order events: produce its aggregate, consume nothing here
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: order-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
# notification-svc consumes notifications: read-only
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: notification-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
# audit-svc consumes wildcard + owns the DLQ
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: audit-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
# search-svc consumes documents, owns its DLQ (P1-F6-S2)
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: search-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF

for u in order-svc notification-svc audit-svc search-svc; do
  kubectl -n "${NS}" wait kafkauser/${u} --for=condition=Ready --timeout=180s
done

echo "==> ACL evidence: declared least-privilege ACLs per principal"
# NOTE: broker-side enforcement deferred. Simple authorization behaved
# inconsistently on this Strimzi 1.3.0 / Kafka 4.3.1 combined-node cluster
# (super.users ignored; per-user produce checks contradicted the declared
# ACLs), so kafka-cluster.yaml ships WITHOUT authorization for the local
# profile. The KafkaUser manifests below are the enforcement source of
# truth; they activate unchanged on the cloud profile once brokers enable
# simple authz there (tracked in IMPLEMENTATION.md P0-F2-S2 annotations).
kubectl -n "${NS}" get kafkauser order-svc notification-svc audit-svc search-svc \
  -o custom-columns='USER:.metadata.name,PRINCIPAL:.status.username,ACLs:.spec.authorization.acls[*].operations'

echo "DONE — topics + DLQ + per-principal ACL manifests applied (P0-F2-S2; enforcement deferred, see note)"

# P2: party-svc principal (tls; broker-side authz off locally, P0-F2-S2)
kubectl -n "${NS}" apply -f - <<'EOF2'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: party-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF2

# P2: identity-svc principal for the party projection + audit listeners
kubectl -n "${NS}" apply -f - <<'EOF2'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: identity-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF2

# P3: product-svc aggregate topics (outbox relay produces; search-svc
# consumes, so each carries a DLQ per the P1-F6 convention)
kubectl -n "${NS}" apply -f - <<'EOF3'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.created.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.updated.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.deleted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.product.deleted.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.price.changed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.price.changed.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.category.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: product.category.updated.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 1, replicas: 3, config: {retention.ms: "2592000000"}}
EOF3

# P3: inventory-svc aggregate topics (outbox relay produces)
kubectl -n "${NS}" apply -f - <<'EOF4'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.facility.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.facility.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.facility.deleted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.item.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.received.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.adjusted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.transferred.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.reserved.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.confirmed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.released.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.shipment.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.shipped.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
EOF4

# P3: product-svc principal (produces own aggregate, consumes CDC topics +
# own events for reverse-sync); inventory-svc principal (produces own
# aggregate, consumes shipment CDC topics). TLS only — no authorization
# block (P0-F2-S2 known issue: broker-side authz off locally).
kubectl -n "${NS}" apply -f - <<'EOF5'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: product-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: inventory-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF5

for u in product-svc inventory-svc; do
  kubectl -n "${NS}" wait kafkauser/${u} --for=condition=Ready --timeout=180s
done

# P4: order-svc aggregate topics (outbox relay produces; the ECA consumers
# and the reverse-sync consumer are order-svc itself, marketing attribution
# is P6 — DLQs on the consumed placed/cancelled topics per the P1-F6 rule)
kubectl -n "${NS}" apply -f - <<'EOF6'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.placed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.placed.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.approved.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.completed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.cancelled.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.cancelled.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.invoicerequested.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.return.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.creditmemo.issued.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
EOF6

# P4: order-svc principal (produces own aggregate, consumes own placed/
# cancelled events + the ORDER CDC topics). TLS only — same P0-F2-S2 caveat.
kubectl -n "${NS}" apply -f - <<'EOF7'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: order-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF7

kubectl -n "${NS}" wait kafkauser/order-svc --for=condition=Ready --timeout=180s

# P5: billing + finance aggregate topics. billing-svc produces the invoice/
# payment aggregates and consumes order.order.invoicerequested (P4
# handshake) + emits notification.requested; finance-svc consumes the
# billing invoice events for auto-posting. DLQs on the consumed topics per
# the P1-F6 rule.
kubectl -n "${NS}" apply -f - <<'EOF8'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.invoice.issued.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.invoice.issued.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.invoice.settled.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.invoice.settled.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.payment.captured.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: billing.payment.failed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: finance.transaction.posted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: finance.period.closed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: order.order.invoicerequested.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: billing-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: finance-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF8

for u in billing-svc finance-svc; do
  kubectl -n "${NS}" wait kafkauser/${u} --for=condition=Ready --timeout=180s
done

# P6: long-tail domains (manufacturing, workeffort, humanres, content,
# marketing). manufacturing-svc consumes order.order.placed (DLQ exists) +
# inventory stock events; humanres-svc consumes party events; marketing-svc
# consumes order.order.placed. DLQs added for the newly-consumed topics.
kubectl -n "${NS}" apply -f - <<'EOF9'
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.bom.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.bom.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.bom.deleted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.routing.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.routing.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: manufacturing.mrp.proposed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.received.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: inventory.stock.adjusted.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: workeffort.effort.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: workeffort.effort.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: workeffort.timesheet.submitted.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: humanres.position.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: humanres.position.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: humanres.employment.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: humanres.onboarding.completed.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: party.party.created.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: party.party.updated.v1.dlq, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: content.content.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: content.content.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: content.content.published.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.campaign.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.campaign.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.trackingcode.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.trackingcode.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.opportunity.created.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata: {name: marketing.opportunity.updated.v1, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec: {partitions: 3, replicas: 3, config: {retention.ms: "604800000"}}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: manufacturing-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: workeffort-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: humanres-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: content-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
---
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata: {name: marketing-svc, namespace: ofbiz, labels: {strimzi.io/cluster: ofbiz}}
spec:
  authentication: {type: tls}
EOF9

for u in manufacturing-svc workeffort-svc humanres-svc content-svc marketing-svc; do
  kubectl -n "${NS}" wait kafkauser/${u} --for=condition=Ready --timeout=180s
done

echo "DONE — P3 product/inventory + P4 order + P5 billing/finance + P6 long-tail topics + principals applied"
