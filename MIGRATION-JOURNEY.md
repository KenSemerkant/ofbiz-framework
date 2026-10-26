# MIGRATION-JOURNEY — Monolith to Cloud-Native Microservices

How Apache OFBiz (a 28-component, 904-entity, single-JVM ERP) was migrated to
16 cloud-native services on Kubernetes, and what the frontend parity program
delivered on top. Narrative companion to [IMPLEMENTATION.md](IMPLEMENTATION.md)
(execution authority), [FUTURESTATE-ARCHITECTURE.md](FUTURESTATE-ARCHITECTURE.md)
(target design), and [adr/](adr/) (decisions).

- **Program window:** 2026-10-16 → 2026-10-26 (local kind staging; production
  soak and sign-off remain org-side per [OPS-HANDOVER.md](OPS-HANDOVER.md))
- **Outcome:** monolith decommissioned (CG-7), all 16 services live, journey
  SPA + parity coverage closed at CG-9 (e2e 19/19)
- **Method:** strangler fig — bounded context by bounded context, gateway
  cutover per phase, consistency-delta-0 gates before every flip

---

## 1. Starting point: the monolith

Apache OFBiz trunk: Java 17 single-JVM ERP. 12 business applications
(accounting, order, product, party, content, humanres, manufacturing,
marketing, workeffort…), 1,708 screen definitions, 1,318 form definitions,
Service Engine (9 engine types), Entity Engine (XML-declared entities over
12 DB dialects), Widget/FreeMarker UI. Everything coupled through the entity
layer: any screen could join any table.

**Why migrate:** shared-persistence coupling made independent change
impossible; the UI stack was upstream-frozen; scaling was whole-JVM only.

## 2. Target: 16 services, event-driven, mesh-native

Decided in [FUTURESTATE-ARCHITECTURE.md](FUTURESTATE-ARCHITECTURE.md) and
locked by ADRs (do not re-litigate; supersede via new ADR):

- **11 domain services:** party, product, order, billing, finance, inventory,
  manufacturing, content, workeffort, humanres, marketing
- **5 platform services:** identity (OIDC), gateway, search, notification, audit
- **Clean architecture per service** (ADR-007/013): `domain` (pure Java, zero
  Spring) ← `application` ← `adapters`/`infrastructure`, enforced by ArchUnit
- **Database-per-service** (ADR-007): no cross-DB joins; cross-context data is
  an API call or an event, references are IDs
- **Kafka event backbone** (ADR-009): transactional outbox + relay — no
  dual-writes; topics `<context>.<aggregate>.<action>.v<N>`, Avro + registry,
  BACKWARD gate in CI; idempotent consumers (`processed_events`)
- **Java 21 + Spring Boot 4.0.x** (ADR-015, security-forced bump), virtual
  threads; JPA/Hibernate + Flyway; S3-API storage only (ADR-008, Garage local);
  OTel everywhere (ADR-011); Kubernetes + Istio STRICT mTLS, east-west gRPC,
  north-south REST via gateway only (ADR-010)

## 3. Phase-by-phase

### Phase 0 — Platform (CG-0)
Kind cluster, Istio mesh (STRICT mTLS), cert-manager + wildcard DNS,
Kafka (KRaft) + schema registry + DLQ policy, Postgres-per-service,
Garage (S3 API), Vault, OTel → Jaeger/Prometheus/Loki, CI with trivy-gated
images. Convention plugins in `buildSrc` stamp the service shape from day one.
*Trap:* Kafka 4.3 ignored `super.users`; broker-side ACL enforcement deferred
to the cloud profile (declared, not silent).

### Phase 1 — identity + party + platform services (CG-1)
Spring Authorization Server: auth-code + PKCE, client-credentials, live JWKS
at the gateway. party-svc first domain service (zero inbound dependencies).
audit/notification/search demonstrated the full event path (DLQ wired).
*Traps:* Boot 4 removed the SAS DSL — authorization server is property-config
only; local-dev disk pressure from topic partitions.

### Phase 2 — product + inventory + search (CG-2)
Product aggregate + pricing (date-range rules, currency fallback), categories
with tree query. inventory-svc: facilities, stock ops, reservation saga with
optimistic-lock oversell prevention (parallel-reserve drill: 0 over-reserved).
search-svc indexes product events into OpenSearch (< 5s). CDC via Debezium
carried monolith rows over. *Traps:* Debezium slot/publication/table filters,
dual-CA truststore, OIDC login scripting.

### Phase 3 — order (CG-3)
Order aggregate + guarded status machine + history; placeOrder saga
(reserve stock, validate party, emit placed) with compensation and
restart-resume; marketing attribution consumer on placed orders.
OCC retry thundering-herd bounded (conflicts surface as typed 409s).

### Phase 4/5 — billing + finance (CG-4/CG-5)
The hard split: 184 accounting entities frozen per-entity into
`billing-svc/MAPPING.md` (99) + `finance-svc/MAPPING.md` (85) — PCI scope
(payment methods) confined to billing-svc; GL mapping tables to finance-svc.
Invoice lifecycle with credit-limit checks, payment gateway adapter
(auth/capture/void, idempotency keys), GL auto-posting from billing events.
Gate evidence: trial balance equals the monolith's on the migrated dataset
(consistency delta 0). *Traps:* kind SMTP port blackhole (invoice-email leg
delivered via eager adapter, recorded), Postgres connection-slot pressure.

### Phase 6 — long tail (CG-6)
Five parallel tracks: manufacturing (BOM/routings/MRP with netting semantics),
workeffort (+ iCal), humanres (onboarding saga), content (S3-backed),
marketing (campaigns/tracking/opportunities). *Traps:* Garage aws-chunked
encoding, zsh quoting in deploy scripts, VM disk during image loads.

### Phase 7 — decommission (CG-7)
Monolith scaled to zero, archived to `s3://ofbiz-archive`. Rollback = flip
`gateway.<svc>.uri` per service. Four deviations recorded. *Traps:* ctr image
pruning to reclaim disk, KRaft broker recovery, residual SHIPMENT topics
mystery (documented).

### Phase 8 — journey SPA + Playwright (P8-GATE, 12/12)
Rebuilt the frontend as `platform/spa` — Vite + React + TypeScript (an early
Angular sketch was superseded), deployed as an nginx pod behind the gateway,
OIDC login through the gateway passthrough. Playwright harness at `tests/e2e`
with auth storageState, per-run ID prefixes (isolation), accessibility
locators. Twelve journey specs: order-to-cash (quote→convert→approve→
invoice→capture→trial balance), returns (server-truth COMPLETED), plan-to-ship
(MRP + pack/ship), hire-to-retire (onboarding saga), campaign→attribution,
content roundtrip, calendar iCal. One descope recorded with evidence: T9
notifications (no REST surface exists — pure Kafka consumer; coverage stands
at service-test level).

### Phase 9 — frontend parity (CG-9, 2026-10-26)
Gap analysis first: the monolith has 1,708 screens across 11 apps; the SPA
had 10. Full enumeration checked in as
[docs/PARITY-MATRIX.md](docs/PARITY-MATRIX.md) (regeneration command in its
header), each app mapped to its owning service with REST-surface and SPA
coverage columns. Nine area tracks then closed in sequence — accounting,
product+facilities, order, party, content, humanres, marketing, manufacturing,
workeffort:

- **REST slice added per track** (search/list/statement endpoints — ~25 new
  mappings; e.g. invoice/payment search, GL accounts/journal listings,
  balance sheet + income statement class-bucketed from the trial balance,
  category/store/facility/stock/shipment listings, order/quote/return lists,
  role-optional party paging, position/employment/routing lists)
- **SPA areas:** Accounting (5 tabs), Catalog (6 tabs), Orders (3 tabs),
  party detail panel, find screens for every long-tail domain
- **Evidence:** 40-run-e2e.sh green at each track close — 14→15→17→18→19/19;
  jacoco 95% + ArchUnit gates green on every touched service

**Honest scope of "parity":** core find/browse/statement surfaces per app,
with every gap recorded as a MoSCoW descope in IMPLEMENTATION.md (P8-T9
precedent) — admin/setup slices over archived tables, screens whose data was
never migrated, lookup/decorator sub-screens. Demo-site-level edit depth
(relationship maintenance, classifications, security groups, requirement
editing…) is **not** present: those need domain modeling in the owning
services first. That is future story work, not an omission by accident.

## 4. Cross-cutting lessons (paid for, written down)

1. **The outbox pattern is non-negotiable.** Every cross-context state change
   wrote aggregate + outbox row in one transaction from Phase 1; the relay
   publishes. No dual-writes anywhere in the target.
2. **Gates, not calendars.** Each CG checklist demanded evidence links
   (Jaeger trace, metrics query, log query, consistency delta 0, rollback
   drill). Phases exited on evidence.
3. **Consistency delta 0 before every flip.** Billing/finance GL parity with
   the monolith trial balance was the gate that caught real mapping bugs.
4. **Local kind ≠ cloud.** ACL enforcement, SMTP paths, disk limits all
   behaved differently; each deviation was declared in the gate file, not
   hidden.
5. **Buildpack images have opinions.** `bootBuildImage` at a 512M pod limit
   needs `BPL_JVM_THREAD_COUNT=50` or the memory calculator refuses to boot —
   and a crashlooping new pod leaves the OLD pod serving, so "endpoint 404s"
   after a deploy means "check `kubectl get pods`, not just rollout status."
6. **Every service's bootBuildImage is named `adapters-rest:latest`** (module
   name). Tag to `<svc>:<tag>` immediately after each build, sequentially, or
   the next build overwrites your image.
7. **Test fakes follow ports.** Adding a store method means every in-memory
   fake across application + adapters test suites needs it; the pre-push full
   build is the safety net that per-module `check` runs are not.
8. **Frontends are only as deep as their REST surface.** Screen parity work
   surfaced exactly where the migration had stopped at journey-depth: the
   listing endpoints were cheap; the edit/admin depth is domain work.

## 5. Receipts

| Milestone | Gate | Date |
|---|---|---|
| Platform live | CG-0 | 2026-10-20 |
| identity + party + platform svcs | CG-1 | 2026-10-21 |
| product + inventory + search | CG-2 | 2026-10-21 |
| order | CG-3 | 2026-10-22 |
| billing + finance | CG-4/CG-5 | 2026-10-23 |
| long tail (5 tracks) | CG-6 | 2026-10-23 |
| monolith decommissioned | CG-7 | 2026-10-24 |
| journey SPA + E2E | P8-GATE 12/12 | 2026-10-25 |
| frontend parity, all apps triaged | CG-9, e2e 19/19 | 2026-10-26 |

Story-level evidence: [IMPLEMENTATION.md](IMPLEMENTATION.md) and
[issues/](issues/). Screen coverage: [docs/PARITY-MATRIX.md](docs/PARITY-MATRIX.md).

## 6. What is deliberately not in the target

- Monolith admin/setup slices over tables that were archived, not migrated
  (fixed assets, fin accounts, budgets, GL setup trees…) — recorded per track
- Agreement maintenance (party-context domain modeling not done),
  tax-authority admin (cross product+finance)
- Notification screens (no REST surface by design; service-test coverage)
- PDF/CSV export variants of reports (SPA renders HTML)
- Production-soak actions (org-side, OPS-HANDOVER.md)
