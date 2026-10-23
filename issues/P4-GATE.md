# P4-GATE — order-svc (Phase 4)

Date: 2026-10-22/23. Status: **code complete + CI-green; staging gate runs BLOCKED on the local Docker-VM disk (details below)**.

## Built

- `microservices/order-svc/` — clean-arch service (domain / application / adapters-rest, no-gRPC per the product-svc template):
  - **P4-F1-S1** Order aggregate + enum-guarded status machine (`OrderStatus` transition table, typed `IllegalOrderTransitionException` → 409, append-only history row per transition, one outbox event per state change).
  - **P4-F1-S2** cart → order with immutable price snapshot (price resolved from product-svc at placement; later catalog changes never re-price a placed line).
  - **P4-F1-S3** returns: creation adjusts inventory via the saga's reservation receipts (inventory receive) + emits `order.return.created.v1` / `order.creditmemo.issued.v1`; requires COMPLETED.
  - **P4-F1-S4** quotes: conversion moves the snapshot lines verbatim (no re-pricing) through the same saga.
  - **P4-F2-S1** placeOrder saga orchestrator: party validation (party-svc) → price snapshot (product-svc) → order + saga row + `order.order.placed.v1` in one tx. ECA steps on placed (inventory reserve per line, invoice trigger `order.order.invoicerequested.v1`, marketing attribution) run as idempotent consumers with **guarded persisted saga markers** (`saga_state.step`), resumable after restart (`SagaResumeRunner` → `resumePending`), compensation on partial failure (release + cancel + typed event).
  - **P4-F2-S2** marketing attribution consumer: tracking code recorded per placed order, count endpoint `GET /api/v1/attributions?trackingCode=`.
- Platform wiring: topics + DLQs + order-svc KafkaUser (`platform/helm/topics/10-create.sh`), deploy block (`platform/services/10-deploy.sh`, orderdb DB + monolith reverse-sync datasource + integration URLs), gateway route `/api/v1/orders|quotes|returns|attributions/**`, CDC connector `platform/cdc/order-connector.json` (unique slot/publication per the P3 lesson), monolith order slice DDL (`13-extend-monolith-p4.sh`), consistency checker (`33-check-order-consistency.sh`), rollback drill (`65-rollback-drill-order.sh`), e2e drill (`platform/services/70-order-lifecycle.sh`).

## Test evidence (all green, `./gradlew :microservices:order-svc:…:check`)

- `OrderTest`, `OrderStatusReturnGuardsTest`, `QuoteReturnTest` — status machine, typed errors, history, immutable lines (domain ≥95% jacoco gate enforced).
- `PlaceOrderSagaTest`, `OrderEcaServiceTest` (reserve+invoice, compensation on partial failure, redelivery no-op, restart resume), `ServiceSuiteTest` (lifecycle/quote/return/attribution use cases).
- `OrderApiTest` (Postgres Testcontainers) — full lifecycle quote→order→approve→complete incl. typed 409s, history endpoint, event order in the outbox, price-snapshot immutability, returns + credit memo, cancel event, rejection paths leave no state.
- `OutboxAtomicityTest` — outbox failure rolls back the order write (ADR-009).
- `ServiceArchitectureTest` — full-tree ADR-007 layering; `OrderAdaptersUnitTest` — relay/consumers/HTTP integrations/JDBC stores (5s request timeout on all saga-facing calls: a dead neighbour rejects, never hangs).
- `:microservices:gateway:check` green with the new order route.

## Applied to the staging cluster

- Order topics + DLQs + `kafkauser/order-svc` — applied, Ready.
- Monolith order slice DDL — applied; `monolith-order` Debezium connector — RUNNING.
- `orderdb` database + `order-svc` release deployed; pods schedule but the app container dies: **"No space left on device"** creating the Tomcat temp dir.

## BLOCKED: local Docker-VM disk (P1 KNOWN-ISSUE recurrence, worse)

The kind cluster's containerd image set (every image on every node) + the platform PVs now need ≈2-3G more than the 62.7G Docker Desktop VM disk provides. Already freed this session: host build cache + stray images (~9G), stale in-node `import-*` images. Remaining options need either a user action or workload changes the session's permission guard blocked:

1. **Enlarge the Docker Desktop disk** (Settings → Resources → Disk ≥ 100G) — cleanest; then `sh platform/services/10-deploy.sh` and run the drills below.
2. Or approve scaling `opensearch` (observability) to 0 + restarting the three crash-looping Kafka broker pods (their log dirs were marked offline by the ENOSPC event; data intact; pod recreation clears it) — frees ~1.5G, enough for the gate run.

## Pending gate runs (scripts ready, execute after the disk is fixed)

```
sh platform/services/10-deploy.sh                 # order-svc Ready
sh platform/services/70-order-lifecycle.sh        # CG-4 lifecycle e2e + ECA + attribution + reverse-sync
sh platform/cdc/33-check-order-consistency.sh     # delta 0
sh platform/cdc/65-rollback-drill-order.sh        # rollback drill
```

Note on CG-4 item 3 ("monolith order writes disabled in staging; 30-day soak before prod cutover"): staging write-cutover is done by construction (gateway routes order API to order-svc; monolith order tables receive reverse-sync only). The 30-day soak is calendar time — it starts when the above staging runs pass; prod cutover remains gated on it.

Interpretation note: the CG-4 "invoice trigger" ECA fires as `order.order.invoicerequested.v1`; billing-svc lands in Phase 5 and will consume it (event now, consumer in P5 — same pattern as P3's forward event contracts).
