# P4-GATE — order-svc (Phase 4)

Date: 2026-10-22/23. Status: **PASSED 2026-10-23** (local kind staging).

## Gate evidence (2026-10-23)

- `platform/services/70-order-lifecycle.sh` → **ORDER LIFECYCLE E2E PASSED**: quote→order (live price snapshot 11.00 = catalog price from product-svc), ECA on `order.order.placed.v1` (inventory ATP 10→8 = reserve; `order.order.invoicerequested.v1` outbox row = invoice trigger; attribution count ≥1 for CG4-PROMO), typed 409 on the illegal transition shortcut, approve→complete, reverse-sync carried the order to monolith `ORDER_HEADER` status=COMPLETED.
- `platform/cdc/33-check-order-consistency.sh` → **DELTA 0 — CONSISTENT** (monolith=order-svc=4 rows, checksums 5dacb43f67ac/5dacb43f67ac).
- `platform/cdc/65-rollback-drill-order.sh` → **PASSED** (monolith-side write streamed back into order-svc's `monolith_order_projection` in ~2s via the `monolith-order` CDC connector — zero data loss both directions; drill rows cleaned up).
- Staging write-cutover: gateway routes `/api/v1/orders|quotes|returns|attributions/**` to order-svc (role-gated); monolith ORDER_* tables receive reverse-sync only. **30-day soak started 2026-10-23**; prod cutover remains gated on it (calendar time — same as every prior phase's prod flip).
- CDC: `monolith-order` connector RUNNING, unique slot/publication (`debezium_order`/`pub_debezium_order`).

## Incident + remediation during the gate run (local profile)

1. Docker-VM disk (62.7G) hit capacity (P1 KNOWN-ISSUE recurrence): order-svc Tomcat tmpdir ENOSPC; brokers crash-looped (log dirs marked offline). Remediation: freed ~10G (host build cache + stray images, stale in-node `import-*` garbage via `crictl rmi --prune`), broker pods recovered via CrashLoop restarts with free disk.
2. Kafka Connect worker group `cdc-connect` wedged post-crash: its consumer-group coordinator partition (`__consumer_offsets-49`) lost 2/3 ISR and the coordinator stayed unavailable → all Debezium tasks starved (replication slots inactive). Remediation: renamed the worker group (`cdc-connect2` → different coordinator partition, `kafka-connect.yaml`) — slots active, all four connectors RUNNING.
3. Reverse-sync writer SQL: unquoted identifiers folded lowercase vs the quoted-uppercase monolith DDL — fixed by quoting (plus `excluded."COL"` in the ON CONFLICT updates).

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
- `orderdb` database + `order-svc` release — deployed, 2/2 Running.

Note on CG-4 item 3 ("monolith order writes disabled in staging; 30-day soak before prod cutover"): staging write-cutover is done by construction (gateway routes order API to order-svc; monolith order tables receive reverse-sync only). The 30-day soak is calendar time — started 2026-10-23; prod cutover remains gated on it.

Interpretation note: the CG-4 "invoice trigger" ECA fires as `order.order.invoicerequested.v1`; billing-svc lands in Phase 5 and will consume it (event now, consumer in P5 — same pattern as P3's forward event contracts).
