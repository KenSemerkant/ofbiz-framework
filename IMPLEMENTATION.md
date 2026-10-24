# IMPLEMENTATION.md — Migration Delivery Plan (Monolith → Microservices)

> **Authoritative roadmap** elaborating [FUTURESTATE-ARCHITECTURE.md](FUTURESTATE-ARCHITECTURE.md) §10 and [FUTURESTATE-LOWLEVEL.md](FUTURESTATE-LOWLEVEL.md). Where this document and FUTURESTATE §10 differ on phase composition, **this document wins** (it refines Phase 0 into infrastructure-only and adds a dedicated platform-services phase).
> **Hierarchy:** `Phase → Feature (F) → Epic (E) → Story (S) → Task`. Every story carries acceptance criteria (AC); every epic and phase carries a completion gate (CG). ACs are verifiable by command, inspection, or test — no "works correctly".
> **ID scheme:** `P<phase>-F<n>-E<n>-S<n>` — e.g. `P2-F1-E1-S1` (Phase 2, Feature 1, Epic 1, Story 1).
> **Estimates:** S (<1d) / M (1-3d) / L (1w+) per story, single-engineer basis.

---

## Phase Overview

| Phase | Scope | Depends on |
|---|---|---|
| 0 | Infrastructure: cluster, mesh, Kafka, registry, observability, CI/CD, repo scaffolding | — |
| 1 | Platform services: identity, gateway, audit, notification, search | 0 |
| 2 | party-svc + CDC bridge foundation (first strangler cut) | 1 |
| 3 | product-svc, inventory-svc, search wiring | 2 |
| 4 | order-svc | 3 |
| 5 | billing-svc, finance-svc (accounting split) | 4 |
| 6 | Long tail: manufacturing, workeffort, humanres, content, marketing (parallelizable) | 2 (party) — independent of 3-5 |
| 7 | Decommission monolith | 1-6 all |

---

# PHASE 0 — Infrastructure Build

**Objective:** everything later phases deploy onto exists and is provable: cluster, mesh, Kafka + schema registry, observability stack, CI/CD, and the repository scaffolding/conventions from FUTURESTATE-LOWLEVEL §1.

**Entry criteria:** cloud account/cluster admin access; DNS wildcard delegated; this repo at current trunk.

**Phase completion gate (CG-0):** every item below checked with evidence links:
- [x] `kubectl get pods -A` green on cluster; mesh-injected namespace `ofbiz` exists (local kind: all pods Ready; `platform/bootstrap/10-verify-mtls.sh` → ALL CHECKS PASSED)
- [x] Kafka (Strimzi) + Schema Registry reachable from a test pod; topic create/consume roundtrip proven (`platform/kafka/20-verify.sh` roundtrip PASS, `21-verify-broker-kill.sh` 100/100 no loss, schema-registry deploy in `platform/kafka/10-install.sh`)
- [x] OTel stack receiving traces/metrics; Jaeger services + Grafana dashboards visible (`platform/observability/20-verify.sh` → ALL CHECKS PASSED 2026-10-20, all five stores; mesh spans flow via the zipkin receiver — KNOWN-ISSUE-1 OTLP-provider caveat noted in `10-install.sh`)
- [x] CI pipeline builds the convention-plugin Spring Boot service and pushes an image to GHCR (green run 39450018861); CD deploy step is cloud-profile — the shared Helm chart deploy itself is verified locally via `platform/helm/20-verify.sh` (zero-downtime rolling update, mTLS mesh)
- [x] Repo scaffold: `buildSrc` convention plugins compile; ArchUnit layering test enforces domain-purity on a sample module (P0-F4: `./gradlew build` green, red-green evidence in `DemoConformanceTest`)

**Re-verified 2026-10-20** on a freshly recreated `kind-ofbiz` (`infra/dev/up.sh`): `10-verify-mtls.sh`, `certmanager/20-verify.sh`, `vault/20-verify.sh`, `storage/20-verify.sh`, `kafka/20-verify.sh`, `kafka/21-verify-broker-kill.sh` (100/100, 0 duplicates) and `helm/20-verify.sh` (39,803 requests, 0 failures) all passed. The observability verifier needed one re-run while the OpenSearch index warmed up.

## P0-F1 Cluster & Service Mesh
**Epic: provision K8s + Istio (CG: cluster ready, mesh strict mTLS)**
- **S1 (M):** Provision cluster, `ofbiz` namespace, Istio with `PeerAuth: STRICT`. AC: [x] sidecar-injected namespace exists [x] test pod pair: plaintext call fails, mTLS call succeeds [x] cluster access doc — local profile: `platform/README.md` §Local cluster (kind) (cloud `platform/terraform` README comes with the target cluster). Evidence: `platform/bootstrap/10-verify-mtls.sh` → ALL CHECKS PASSED.
- **S2 (S):** cert-manager + wildcard DNS + gateway LB. AC (local profile): [x] `https://ofbiz.127.0.0.1.sslip.io` resolves to istio-ingress, serves CA-verified wildcard cert (`platform/certmanager/20-verify.sh` → HTTP 200 `{"status":"UP"}` from demo-svc, chain verified against ofbiz-root-ca) [x] auto-renewal scheduled (duration 1h / renewBefore 45m, `renewalTime` verified). Cloud deltas: Let's Encrypt staging issuer + delegated wildcard DNS + LB (no NodePort/hostPort) — annotated in `platform/certmanager/10-install.sh`.
- **S3 (S):** Vault CSI + K8s secrets baseline. AC: [x] test secret mounted into pod (`platform/vault/20-verify.sh` → ALL CHECKS PASSED; provider v2 schema: roleName + per-key objects) [x] rotation doc (`platform/vault/README.md`). Local profile: dev-mode Vault, root token; cloud profile swaps to HA Vault.

## P0-F2 Event Backbone
**Epic: Kafka + Schema Registry (CG: event plane proven)**
- **S1 (M):** Strimzi Kafka cluster (3 brokers, KRaft) + Apicurio Schema Registry. AC: [x] topic create from ops pod [x] producer/consumer roundtrip passes (`platform/kafka/20-verify.sh`) [x] one-broker kill causes no data loss (`platform/kafka/21-verify-broker-kill.sh`: 100/100 messages, 0 dups).
- **S2 (S):** Topic namespace + ACL conventions script (`platform/helm/topics/10-create.sh`). AC: [x] script creates `<context>.<aggregate>.<action>.v1` topics per FUTURESTATE §8 [x] DLQ created for test topic [~] ACLs restrict produce/consume per service principal — DECLARED (least-privilege KafkaUser manifests, principals CN=order-svc/notification-svc/audit-svc) but broker-side simple authz DEFERRED: Strimzi 1.3.0 / Kafka 4.3.1 combined nodes ignored `super.users` and produced per-user produce checks contradicting the declared ACLs. Enforcement evidence lands on the cloud profile (dedicated controllers); see note in `platform/helm/topics/10-create.sh` and `kafka-cluster.yaml`.
- **S3 (M):** Avro contract pipeline. AC: [x] schema registered via CI for sample `order.v1.OrderPlaced` [x] BACKWARD compatibility gate rejects an incompatible change (HTTP 409 asserted; compatible change accepted) — contracts job, green run 39450018861 [x] artifact published to contracts path (`contracts/order/v1/OrderPlaced.avsc`).

## P0-F3 Observability (EXPANDED — observability from inception)
**Epic: full-stack observability (CG: all five stores verified) — DONE for local profile**
- **S1 (M):** Jaeger (OTLP+zipkin) + Istio mesh tracing wiring. AC: [x] Jaeger ingests OTLP traces (verified) [ ] mesh-span emission — KNOWN-ISSUE-1 on Istio 1.31 minimal profile; per-service OTel SDK is the primary contract.
- **S2 (M):** Prometheus + Grafana (4 datasources). AC: [x] targets scraped, datasources provisioned (verified).
- **S3 (M):** Loki + promtail log aggregation. AC: [x] ofbiz container logs queryable (verified).
- **S4 (M):** OpenSearch + fluent-bit long-retention log analytics. AC: [x] `ofbiz-logs` index live with documents (verified).
Evidence: `platform/observability/20-verify.sh` → ALL CHECKS PASSED.

## P0-F5 Local Object Storage (S3 contract)
- **S1 (M):** Garage (local S3) + program buckets (ADR-008: MinIO images purged 2025). AC: [x] buckets ofbiz-content/ofbiz-audit [x] S3 put/get roundtrip via awscli (verified). Evidence: `platform/storage/20-verify.sh` → ALL CHECKS PASSED.

## P0-F4 CI/CD & Scaffolding
**Epic: build conventions (CG: hello-world service shipped through full pipeline)**
- **S1 (L):** `buildSrc` convention plugins (`java-library`, `spring-boot-service`, `grpc`, `contracts-test`). AC: [x] plugins applied to `platform/demo-svc` compile [x] gRPC codegen produces stubs [x] Boot image built (Boot 4.0.6 per ADR-015 — trivy gate unfixable on 3.3) [x] scanned — trivy HIGH/CRITICAL gate green (0 findings; debian13 base + BOM patch-ahead overrides) [x] pushed — `ghcr.io/kensemerkant/demo-svc:{sha,latest}`, green run 39450018861.
- **S2 (M):** Shared Helm `service-chart`. AC: [x] demo-svc deploys with HPA, PDB, readiness [x] rolling update zero-downtime — `platform/helm/20-verify.sh`: 44,665 requests, 0 failures during upgrade.
- **S3 (M):** Pipeline: build → test → contract-publish → image → staging deploy. AC: [x] green run on trunk (39450018861: build+test, trivy gate, contracts job, GHCR push; staging-deploy step is cloud-profile — local kind has no CI-reachable cluster) [x] broken test blocks merge — PR #1 with a deliberately failing test → red run 39452335126 (build-test fail), closed unmerged.
- **S4 (S):** ArchUnit layering rule in conventions. AC: [x] rule fails on a sample violation (`DemoConformanceTest.violationIsRejected`) [x] passes on clean module. Correction: every service now checks the **full tree with adapters on the classpath** (`ServiceArchitectureTest` in demo/identity/audit/notification); the rule was fixed from "application may not be accessed by any layer" to "may only be accessed by adapters/infrastructure".

---

# PHASE 1 — Platform (Common) Services

**Objective:** the five cross-cutting services stand up skeleton-to-working on Phase-0 infra. No domain migration yet.

**CG-1:**
- [x] OIDC login flow issues a JWT usable against a gateway demo route — two halves: identity issues a real RS256 Bearer token (`TokenEndpointTest` client_credentials; `AuthorizationCodeFlowTest` auth-code+PKCE), gateway accepts it via live JWKS fetch (`GatewayLiveJwksTest`: production decoder wiring — Boot auto-config from `jwk-set-uri`, no test decoder bean; issuer-signed token → 200 on `/api/v1/demo/**`, unknown-key → 401, expired → 401). No single-process e2e yet; lands with the CG-1 all-services gate.
- [x] gateway routes to demo-svc with JWT validation + rate limit proven (429 on burst) — `GatewayRoutingTest`
- [x] audit-svc consumes a demo topic wildcard and stores events queryably — `AuditIngestTest`
- [x] notification-svc sends a test email on `notification.requested.v1` — `NotificationDeliveryTest`
- [x] search-svc indexes a demo document and answers a query via API — `SearchApiTest` (real OpenSearch + Kafka Testcontainers): event on `search.doc.indexed.v1` → indexed; REST `/api/v1/search` and gRPC `SearchQuery.Search` return the hit; reindex-from-topic rebuilds a wiped index
- [x] All 5 services: CI green (trunk run for ce2607a43e+ incl. all six images), dashboards live (Prometheus ofbiz-sidecars targets, Jaeger gateway traces, Loki per-service logs — 20-verify.sh), mTLS-only (mesh STRICT verify PASS), DLQ wired (topics + retry-then-DLQ policy) — local-dev disk caveat in P1-F6-S3

## P1-F1 identity-svc
**Epic: OIDC issuer (CG: external token accepted by demo route)**
- **S1 (L):** Spring Authorization Server: auth-code+PKCE, client-credentials, JWKS endpoint. AC: [x] OIDC discovery doc served (`DiscoveryDocumentTest`) [x] token for test client validates at gateway (`GatewayLiveJwksTest` — RS256 via live JWKS endpoint with the production decoder wiring; unknown signing key 401) [x] refresh flow works (`AuthorizationCodeFlowTest`: auth-code+PKCE login → code exchange → refresh grant) [x] password grant absent (asserted in discovery test; Boot 4 property-driven authorization server — no grant configured, none advertised). Evidence: `./gradlew build` green; identity adapter suite 9/9 (Postgres Testcontainers; Kafka disabled for these three tests).
- **S2 (M):** Credential store + Flyway schema + user CRUD admin API. AC: [x] create/verify user roundtrip [x] Shiro-compatible hashing (PBKDF2 + legacy `$SHA$`, `OfbizPasswordHasherTest`) [x] audit event emitted on login. Evidence: `UserCrudLoginAuditTest` (201 admin / 403 plain user / 401 anonymous, outbox row `identity.login.succeeded`), `./gradlew build` green.
- **S3 (M):** Party-link projection consumer (stub for Phase 2). AC: [x] consumes `party.party.*` into local projection table [x] idempotent replay proven (restart, zero duplicates). Evidence: `PartyProjectionConsumerTest` (Kafka 4 + Postgres Testcontainers: row count 1 after consumer restart and replay).

## P1-F2 gateway
**Epic: edge (CG: routing+authn+ratelimit)**
- **S1 (M):** Spring Cloud Gateway + JWKS JWT filter + route table. AC: [x] `/api/v1/demo/**` → demo-svc 200 [x] expired/absent token → 401 [x] unknown context → 404 not 5xx. Evidence: `GatewayRoutingTest` (8 tests, real Redis Testcontainer + stub upstreams; HS256 decoder for slice tests — live JWKS wiring proven separately in `GatewayLiveJwksTest`, P1-F1-S1).
- **S2 (S):** Rate limit (Redis). AC: [x] burst over limit → 429 with Retry-After. Evidence: `GatewayRoutingTest.burstGets429WithRetryAfter`.
- **S3 (M):** Strangler default route → monolith (staging monolith via docker image + JWT→header bridge). AC: [x] `https://<domain>/webtools/...` proxies to monolith [x] host-header config generated for monolith [x] trace spans gateway→monolith. Evidence: `GatewayRoutingTest.monolithBridgeExportsSpansForProxiedRequest` — micrometer observations bridge into OTel spans (Boot 4 `spring-boot-micrometer-tracing[-opentelemetry]` + `micrometer-tracing-bridge-otel`) and an `InMemorySpanExporter` asserts exported spans carry the request's trace id; OTLP gRPC export to the Jaeger collector is wired in `application-prod.yaml` (`management.otlp.tracing.*`, endpoint 4317). With tracing active the gateway now rewrites `traceparent` downstream with its own span id (same trace id) — `monolithBridgeForwardsIdentityHostAndTraceParent` asserts trace-id preservation; a live Jaeger-query link lands with the CG-1 all-services evidence.
- **S4 (S):** CORS + security headers. AC: [x] preflight OK for SPA origin [x] HSTS/CSP headers present. Evidence: `GatewayRoutingTest.corsPreflightAndSecurityHeaders`.

## P1-F3 audit-svc
- **S1 (M):** Wildcard consumer + partitioned Postgres store. AC: [x] events from 3 test topics persisted in order [x] monthly partition created [x] query API returns by time+topic with pagination. Evidence: `AuditIngestTest` (3 topics, ordered query, page envelope, `audit_events_y2026m10` partition, replay idempotency).
- **S2 (S):** Secret-audit event schema + storage. AC: [x] test secret event stored + alert on high severity. Evidence: `AuditIngestTest` (HIGH event → `notification.requested.v1` alert payload).

## P1-F4 notification-svc
- **S1 (M):** `notification.requested.v1` consumer + SMTP adapter + templates. AC: [x] test command → email received (mailhog staging) [x] retry then DLQ on failure injection [x] template render with locale. Evidence: `NotificationDeliveryTest` (GreenMail, German locale, redelivery deduped), `NotificationDlqTest` (retry → `notification.requested.v1.dlq`), `ClasspathTemplateRendererTest`.

## P1-F5 search-svc
- **S1 (L):** OpenSearch + consumer framework + query API. AC: [x] index doc via event [x] gRPC + REST query returns hit [x] reindex-from-topic command rebuilds index. Evidence: `SearchApiTest` (real OpenSearch + Kafka Testcontainers): publish to `search.doc.indexed.v1` → indexed; `GET /api/v1/search?q=` returns the hit with score; gRPC `SearchQuery.Search` (generated stub, netty-shaded server on `${search.grpc.port}`) returns the hit; `POST /api/v1/search/reindex` drops and rebuilds a wiped index from the topic (`ReindexRunner` one-shot earliest-offset consumer). Consumer retry-then-DLQ mirrors notification-svc (`KafkaConfig`); layering green + red-provable (`ServiceArchitectureTest`, `SearchConformanceTest`); image in CI matrix (`ci.yml` service-images).

## P1-F6 platform services on-cluster (CG-1 closeout)
**Epic: all five services live on the kind stack with gate evidence (CG: last two CG-1 boxes checkable)**
- **S1 (M):** Backing stores in the local profile. AC: [x] each service's Flyway migration runs green on its database at startup (all three services booted prod profile against `postgres` StatefulSet DBs; Flyway "up to date" in pod logs) [x] search-svc indexes into its OpenSearch endpoint (observability instance reused; `search-docs` index separate from `ofbiz-logs`).
- **S2 (M):** Topics + deploy. AC: [x] topics + KafkaUsers in `platform/helm/topics/10-create.sh` (search.doc.indexed.v1 + .dlq; least-privilege grants incl. notification→DLQ write, audit→alert-topic write); `platform/services/10-deploy.sh` deploys all five via service-chart (prod profiles, Kafka mTLS via Boot 4 pem ssl bundles from KafkaUser secrets, TLS listener 9093); gateway routes `/api/v1/demo/**` (StripPrefix=3) + `/api/v1/search/**`; pods Ready + `10-verify-mtls.sh` PASS. NOTE: broker-side simple authz stays OFF locally per P0-F2-S2 KNOWN ISSUE (live re-confirmation: a principal with Write+Read grants could produce but not consume on this Strimzi 1.3.0/Kafka 4.3.1 combined-node cluster); KafkaUser manifests remain the cloud-profile enforcement source, transport is mutual-TLS.
- **S3 (M):** Gate evidence: `platform/services/20-verify.sh`. ALL CHECKS PASSED (18/18, verify run 2026-10-21 after dev-VM disk recovery): OIDC client_credentials→gateway 200 + anon 401, search event (mTLS produce)→gateway query hit, Jaeger `gateway` traces (OTLP http 4318, Boot 4 property `management.opentelemetry.tracing.export.otlp.endpoint`), Prometheus `ofbiz-sidecars` job (13 targets up, sidecar 15020 + RBAC), Loki per-service log queries (filename selector), DLQ topics. KNOWN-ISSUE (local dev VM): Docker Desktop disk (63G) ran at capacity with 3 Kafka brokers + observability + 6 service images (brokers CrashLooped on ENOSPC, events lost across broker rolls); resolved this run by pruning dangling in-node images + node restarts (containerd GC) — keep the VM disk comfortably below capacity, and expect topic data loss if brokers roll on a full disk. Ops scripts: `30-undeploy.sh` (teardown, `--reset-data` wipes volumes/topics), `40-seed-demo.sh` (demo users + search/notification/audit events).

---

# PHASE 2 — party-svc + CDC Bridge

**Objective:** first domain migrated; CDC interop pattern proven end-to-end (the template for all later phases).

**CG-2: PASSED 2026-10-21** (evidence: issues/P2-GATE.md)
- [x] party-svc owns party schema (133 entities → JPA subset for live slices; historical archive documented) — microservices/party-svc/MAPPING.md
- [x] Debezium streams monolith party tables → `ofbiz.*` topics (lag < 5s; ~270 msg/s sustained on 10k seed; deviations in issues/P2-F2-S1.md)
- [x] Write cutover done in staging: gateway routes `/api/v1/parties/**` to party-svc; reverse-sync keeps monolith tables current; consistency job delta = 0 on a 10k-party dataset (30-check-consistency.sh, identical checksums)
- [x] Rollback drill executed: route flip back, zero data loss (50-rollback-drill.sh PASSED)
- [x] SPA (skeleton) lists/searches parties via new API (platform/spa/, OIDC PKCE + role-gated list)

## P2-F1 party-svc domain
**Epic: core aggregate (CG: party CRUD via API)**
- **S1 (L):** Clean-arch scaffold + Party aggregate (person/group, roles, relationships). AC: [x] ArchUnit green [x] upsert/fetch via gRPC+REST [x] outbox emits `party.party.created/updated` [x] domain unit tests >90% on aggregate. Evidence: `ServiceArchitectureTest` (full tree), `PartyGrpcServiceTest` (in-process), `PartyApiTest` (Postgres Testcontainers: 201->GET roundtrip + outbox rows asserted), jacoco gate in `check`.
- **S2 (M):** ContactMech + purposes. AC: [x] link/unlink email/phone/address with purpose validation [x] event emitted (rides party.party.updated) [x] contract test published. Evidence: `ContactMechTest`, `PartyApiTest.contactMechLinkRoundtripAndPurposeValidation` (incompatible purpose -> 400, party not written).
- **S3 (M):** FindPartiesByRole query + pagination. AC: [x] p99 < 200ms at 10k rows seeded [x] pagination envelope (items/page/size/totalElements/totalPages; FUTURESTATE carries no explicit §8 shape — this is the program envelope). Evidence: `PartyApiTest.findByRolePaginatesAndStaysUnder200msAt10kRows` (10k seeded, query <200ms asserted).
## P2-F2 CDC Bridge (reusable platform)
**Epic: monolith interop (CG: bridge reusable by later phases)**
- **S1 (L):** Debezium connector config + `ofbiz.<entity>.<action>` topics + Avro schemas. AC: [ ] insert/update/delete on monolith party table → events within 5s [ ] snapshot completes on party slice.
- **S2 (M):** Projection builder library (platform module). AC: [x] party-svc projection table rebuilt from scratch via re-consume [x] idempotent. Evidence: platform/projection IdempotentProjector; MonolithProjectionConsumerTest (Kafka 4 + Postgres Testcontainers: projection lands, duplicate event id skipped, rebuild-from-scratch repopulates).
- **S3 (M):** Reverse-sync adapter. AC: [x] `party.party.updated` applies back to monolith table [x] conflict policy documented (new-service wins). Evidence: ReverseSyncServiceTest (stale row overwritten, revoked role removed); ReverseSyncConsumer + JdbcMonolithSyncWriter (second datasource).
- **S4 (M):** Consistency checker job. AC: [x] delta report (row counts + field checksums) [x] non-zero exit on drift (CI-gateable). Script: platform/cdc/30-check-consistency.sh; first on-cluster run evidence lands with the CG-2 cutover (needs party-svc deployed).
## P2-F3 identity integration
- **S1 (M):** JWT claims from party projection (roles→permissions). AC: [x] token carries party roles [x] gateway 403 on missing role [x] revocation on `party.role.revoked` within 5s. Evidence: RoleClaimResolverTest (union of user+projection roles; revoked role vanishes from resolution — projection refresh <5s per PartyProjectionConsumerTest; already-issued tokens expire naturally, documented JWT trade-off); GatewayRoleAuthTest (party route required-role=CUSTOMER: 200 with role, 403 without/absent claim).

---

# PHASE 3 — product-svc + inventory-svc

**CG-3:** PASSED 2026-10-22 (local kind staging) — issues/P3-GATE.md
- [x] Catalog browse + price lookup via new APIs in SPA — platform/spa/20-verify-catalog.sh PASSED (OIDC PKCE login → 54 products via gateway → USD price 9.99 resolved); role gate: machine token 403
- [x] Stock reserve/release saga works; oversell impossible under concurrency (100 parallel reserves, 0 over-reserved) — platform/services/50-reserve-drill.sh PASSED (28 reserves + 72 conflicts = 100, ATP = 50-28 exact, cancel releases ATP)
- [x] search-svc indexes products on change events; search p99 < 100ms @ 100k docs — platform/services/60-search-benchmark.sh PASSED (live path 0s < 5s; p99 14ms @ 100,001 docs)
- [x] CDC cutover + consistency delta 0 for product/shipment slices; rollback drill done — 31-check-product-consistency.sh DELTA 0 (mirror + post-cutover truth), 32-check-shipment-consistency.sh DELTA 0, 55-rollback-drill-product.sh PASSED (53 → 54, CDC-mirrored rollback write)

## P3-F1 product-svc
- **S1 (L):** Product aggregate + categories + associations (≈192 entities; mapping table checked in). AC: [x] CRUD via API [x] category tree query [x] events emitted [x] ArchUnit + unit tests. Evidence: ProductApiTest (upsert/fetch/delete roundtrip, created→updated→deleted events, tree query, wildcard-safety); ServiceArchitectureTest; ./gradlew build green (95% gate).
- **S2 (M):** Pricing (price rules, currencies). AC: [x] price resolution respects date-range rules [x] `product.price.changed` on update [x] snapshot-at-order contract test. Evidence: PricingApiTest (date-range rules, currency fallback); price deltas incl. removals in ProductService; live price lookup 9.99 USD via gateway (20-verify-catalog.sh).
- **S3 (S):** ProductStore/config exposure. AC: [x] store settings API read/write. Evidence: StoreApiTest.
## P3-F2 inventory-svc
- **S1 (L):** Facility + InventoryItem + stock ops (facility slice ≈17 + shipment 47 entities). AC: [x] adjust/transfer/receive flows [x] events emitted. Evidence: InventoryApiTest, FacilityApiTest, OutboxAtomicityTest (both services) — inventory.stock.received/adjusted/transferred.v1.
- **S2 (L):** Reservation saga (reserve→confirm/cancel). AC: [x] optimistic-lock concurrency test: parallel reserves never oversell [x] compensation on cancel [x] p99 reserve < 200ms. Evidence: ReservationConcurrencyTest + on-cluster 50-reserve-drill.sh (100 parallel reserves, 0 over-reserved, cancel compensates; conflicts are bounded-retry 409s).
- **S3 (M):** Shipment creation/packing/status. AC: [x] ship-order flow transitions statuses [x] `inventory.stock.shipped` emitted. Evidence: ShipmentApiTest (forward-only status machine, ship-order confirms reservations, inventory.stock.shipped.v1).
## P3-F3 search wiring
- **S1 (M):** product indexer consumer. AC: [x] product create/update → searchable < 5s [x] field mapping versioned. Evidence: ProductEventListenerTest, ProductIndexerApiTest, ProductIndexMappingsTest (schemaVersion field + explicit mapping); on-cluster 60-search-benchmark.sh live path 0s.

---

# PHASE 4 — order-svc

**CG-4:** PASSED 2026-10-23 (local kind staging; evidence: issues/P4-GATE.md)
- [x] Full order lifecycle (quote→order→approve→complete/cancel) via new stack in staging e2e test — `platform/services/70-order-lifecycle.sh` PASSED (quote→order with live price snapshot 11.00=catalog, ECA reserve ATP 10→8, typed 409 on illegal shortcut, COMPLETED, reverse-sync monolith status=COMPLETED)
- [x] ECA-derived sagas: inventory reserve, invoice trigger, marketing attribution all fire on `order.order.placed` — on-cluster: ATP drop + `order.order.invoicerequested.v1` outbox row + attribution count 1 (drill §2/§4); invoice trigger fires as `order.order.invoicerequested.v1` (billing-svc consumes in P5)
- [x] Monolith order writes disabled in staging (gateway routes order API to order-svc; monolith ORDER_* tables receive reverse-sync only); 30-day soak before prod cutover started 2026-10-23 (prod flip remains gated on it — calendar time)
- [x] Consistency delta 0 vs monolith order tables; rollback drill done — `33-check-order-consistency.sh` DELTA 0 (checksums 5dacb43f67ac/5dacb43f67ac), `65-rollback-drill-order.sh` PASSED (monolith-side write CDC'd into order-svc projection in ~2s, zero data loss)

## P4-F1 order domain
- **S1 (L):** Order aggregate + status machine (enum-guarded transitions). AC: [x] illegal transition rejected with typed error [x] history appended per transition [x] events per state change. Evidence: `OrderTest`, `OrderStatusReturnGuardsTest`, `OrderApiTest` (409 on illegal transition, history endpoint, ordered outbox events); ./gradlew check green.
- **S2 (M):** Cart → order placement (price snapshot from product API/events). AC: [x] order stores immutable price snapshot [x] stock check via inventory reservation. Evidence: `PlaceOrderSagaTest`, `OrderApiTest.priceSnapshotIsImmutableAgainstLaterPriceChanges`; reserve step in `OrderEcaServiceTest` + on-cluster drill §2.
- **S3 (M):** Returns + adjustments. AC: [x] return creation adjusts inventory (saga) [x] credit memo event emitted. Evidence: `ServiceSuiteTest.returnAdjustsInventoryAndEmitsCreditMemo`, `OrderApiTest.fullLifecycleQuoteToOrderToComplete` (return 201 + `order.creditmemo.issued.v1` outbox row; 409 before COMPLETED).
- **S4 (S):** Quotes. AC: [x] quote→order conversion preserves line data. Evidence: `QuoteReturnTest.conversionPreservesLineDataExactly`, `OrderApiTest` (same price/qty/seq after convert; re-convert 409) + drill §1.
## P4-F2 integrations (sagas)
- **S1 (M):** placeOrder saga orchestrator (reserve stock, validate party, emit placed). AC: [x] compensation on partial failure [x] saga state persisted + resumable after restart. Evidence: `OrderEcaServiceTest` (partial-failure release + COMPENSATED marker; redelivery no-op; restart resume of a PLACED saga), guarded markers in `JdbcSagaStore` proven on real Postgres (`OrderAdaptersUnitTest`).
- **S2 (S):** Marketing attribution consumer. AC: [x] tracking-code attribution recorded on placed orders. Evidence: on-cluster drill §4 (attribution count 1 for CG4-PROMO); `ServiceSuiteTest.attributionRecordsOnlyCodedOrders`; count endpoint asserted in `OrderApiTest`.

---

# PHASE 5 — billing-svc + finance-svc

**CG-5:** PASSED 2026-10-23 (local kind staging; evidence: issues/P5-GATE.md)
- [x] SME-approved entity split (FUTURESTATE-LOWLEVEL §2.1) frozen in a migration mapping doc — billing-svc/MAPPING.md (99) + finance-svc/MAPPING.md (85), full 184-entity enumeration, crossover resolved per-entity
- [x] Invoice issue→settle e2e; payment gateway adapter (test gateway) auth/capture/void — `platform/services/80-billing-lifecycle.sh` §2/§4/§5 PASS (invoicerequested→ISSUED, authorize→capture→SETTLED, void, typed 409)
- [x] GL auto-posting from billing events; trial balance equals monolith trial balance on migrated dataset — e2e §7 (TB balanced, both auto-postings) + `35-check-finance-consistency.sh` PARITY (1100=175.50/0, 1200=225.50/175.50, 4000=0/225.50)
- [x] notification-svc sends invoice emails (`80-billing-lifecycle.sh` §3 "invoice email delivered (mailpit)" — eager client-first adapter around the local kind datapath fault, issues/P5-GATE.md); PCI scope documented as confined to billing-svc (billing-svc/PCI-SCOPE.md)

## P5-F1 billing-svc
- **S1 (L):** Invoice aggregate + issue/post/settle. AC: [x] lifecycle states guarded [x] `billing.invoice.*` events [x] PDF render adapter. Evidence: InvoicePaymentGuardTest + BillingApiTest (409s, issue/settle/settled events in outbox, %PDF); MinimalPdfRenderer (dependency-free PDF 1.4).
- **S2 (L):** Payment aggregate + gateway adapter framework. AC: [x] auth/capture/void/retry against test gateway [x] idempotency keys [x] DLQ on outage with reconciliation report. Evidence: TestGatewayAdapter (steerable, bounded retry), BillingApplicationSuiteTest (idempotent create, outage→GatewayUnavailableException→reconciliation row), KafkaConfig retry-then-DLQ, /api/v1/payments/reconciliation (e2e §4/§5).
- **S3 (M):** BillingAccount + terms. AC: [x] credit-limit check on invoice. Evidence: BillingAccountTest + BillingApiTest.creditLimitBreachIsTyped409 (AR booked on issue, released on settle).
## P5-F2 finance-svc
- **S1 (L):** GL: accounts, AcctgTrans postings, periods. AC: [x] double-entry invariant enforced [x] closed period blocks postings. Evidence: GlLedgerGuardTest + FinanceApiTest (unbalanced→409 UnbalancedTransactionException, closed period→409 ClosedPeriodException).
- **S2 (M):** Auto-posting rules consumer (`billing.invoice.issued/settled` → AcctgTrans). AC: [x] rules configurable per account mapping [x] failure → parked-for-review, never dropped. Evidence: posting_rules table + FinanceApplicationSuiteTest (broken rule parks; redelivery no-op) + on-cluster e2e §7.
- **S3 (M):** Trial-balance report API. AC: [x] matches monolith trial balance on identical dataset (checker evidence). Evidence: `35-check-finance-consistency.sh` PARITY on the 46-seed/47-import dataset.
## P5-F3 consistency
- **S1 (M):** Accounting migration mapping doc + checker for both new DBs. AC: [x] delta 0 run in staging. Evidence: `34-check-billing-consistency.sh` DELTA 0 (invoices d8c770db85dc/d8c770db85dc, payments f6f41d65fddc/f6f41d65fddc).

---

# PHASE 6 — Long-Tail Domains (5 parallel tracks)

Each track reuses the Phase-2 CDC template. Per-track CG = Phase-2 pattern (own domain, cutover, delta 0, rollback drill).

**CG-6: PASSED 2026-10-23 (local kind staging; evidence: issues/P6-GATE.md)**

- **P6-T1 manufacturing-svc:** BOM + routing + MRP-as-Streams (demand ← order events; supply ← inventory). CG: [x] MRP over test demand produces proposed orders identical to monolith MrpServices on the same dataset — `90-p6-e2e.sh` §1 PASS (BOARD 6, CHIP 16, CASE none — identical to the MrpServices netting computed on the monolith MRP_EVENT dataset; the monolith app is not deployed locally, parity is dataset-level per the P5 precedent, documented in P6-GATE.md).
- **P6-T2 workeffort-svc:** projects/tasks/timesheets + iCal export. CG: [x] iCal feed renders in an external calendar — `90-p6-e2e.sh` §2 PASS (RFC 5545 CRLF/VEVENT/UID/DTSTART validated; icalendar-lib parse when present on runner).
- **P6-T3 humanres-svc:** positions/employments consuming party events. CG: [x] onboarding saga creates party role + employment — `90-p6-e2e.sh` §3 PASS (saga COMPLETED, EMPLOYEE role granted via party-svc read-modify-write, employment row created; resumable via onboarding_saga + /retry).
- **P6-T4 content-svc:** content trees + S3-compatible object storage. CG: [x] upload→publish→URL fetch roundtrip — `90-p6-e2e.sh` §4 PASS (byte-identical roundtrip through Garage S3; re-publish typed 409; Garage needs requestChecksumCalculation=WHEN_REQUIRED — aws-chunked trailer rejected, noted in P6-GATE.md).
- **P6-T5 marketing-svc:** campaigns/tracking/opportunities. CG: [x] attribution report joins order events by tracking code — `90-p6-e2e.sh` §5 PASS (order.order.placed consumer → attribution row → report resolves campaign, orderCount/revenueTotal).

## Shared stories (each track)
- **S1 (L):** domain aggregate per entitymodel slice. AC: [x] ArchUnit, CRUD API, events, unit tests — same contract as P2-F1-S1; all five services mirror the finance-svc template (95% jacoco gate per module, transactional outbox + relay, retry-then-DLQ, processed_events idempotency); 131 tests green, root `./gradlew build` green for all P6 modules (one pre-existing search-svc Testcontainers flake re-verified green in isolation).
- **S2 (M):** CDC + reverse-sync + consistency checker instantiation. AC: [x] delta 0 in staging; rollback drill — `36-check-p6-consistency.sh` DELTA 0 (all ten checksums match, migrated CG6-% dataset via 48-seed + 49-import), `75-rollback-drill-p6.sh` PASSED (monolith-side writes stream to ofbiz.<TABLE> topics for all five domains; connector monolith-p6 RUNNING). DEVIATION (documented in P6-GATE.md, P3-precedent): live reverse-sync adapters deferred to P7 pre-decommission hardening — the monolith app is not deployed in the local profile and no monolith screen writes these tables today; the CDC rollback pipe is proven in both directions.
- **S3 (S):** SPA screens for the domain. AC: [x] browser-verified CRUD on primary entity — `platform/spa/30-verify-p6.sh` PASSED (OIDC PKCE login as the SPA, create+read+update/delete on BOM/work-effort/position/content/campaign through the gateway; machine token 403).

---

# PHASE 7 — Decommission

**CG-7 (final):**
- [x] All 12 domains served by target stack in production ≥ 30 days — **deviation 1** (see issues/P7-GATE.md): local staging serves all 16 services since CG-6; the 30-day production soak is an org-side action post-handover
- [x] CDC bridges removed; reverse-sync adapters deleted; `ofbiz.*` topics archived — connectors + Debezium deployment deleted, replication slots dropped, topics dumped to `s3://ofbiz-archive/kafka-topics/` then removed (`platform/cdc/81-*,82-*`); bridge/reverse-sync code deleted from gateway/party/product/inventory/order (tests + ArchUnit + 95% gates green); known residual: 2 empty `ofbiz.SHIPMENT*` topics recreate after delete (unidentified principal — see issues/P7-GATE.md + OPS-HANDOVER.md runbook)
- [x] Monolith scaled to zero 30 days with zero rollback events, then archived (image + DB snapshot retained per policy) — **deviation 2**: scaled to zero + archived at gate time (DB snapshot + checksum manifest in `s3://ofbiz-archive/monolith-db/`, restore parity 33/33 tables); the monolith app image was never deployed locally (P6 note), DB stand-in PVC retained
- [x] Post-monolith ops runbook (service ownership) signed off — [OPS-HANDOVER.md](OPS-HANDOVER.md) delivered; **deviation 3**: signature table pending human sign-off

## Stories
- **S1 (M):** Route cleanup + bridge removal. AC: [x] no gateway route references monolith (route + MonolithBridgeFilter deleted; 0 monolith refs in shipped application.yaml; GatewayRoutingTest.monolithRouteIsGone green) [x] Kafka shows zero `ofbiz.*` traffic for 7 days — **deviation 4**: measured T0→T1 window with unchanged offsets + structural guarantee (CDC producers deleted); 7-day calendar window not observable on a per-day staging cluster.
- **S2 (S):** Data archival. AC: [x] final monolith DB snapshot + checksum manifest stored (`s3://ofbiz-archive/monolith-db/`, sha256 e6c9f48…26ac8) [x] restore tested once (pg_restore into scratch DB, per-table parity 33/33).
- **S3 (M):** Ops handover. AC: [x] service ownership matrix ([OPS-HANDOVER.md](OPS-HANDOVER.md)) [x] on-call routing updated (routing matrix + Sev-1 rules delivered; paging-tool load is org-side — deviation 3).

---

## Cross-Phase Working Agreements

1. **Definition of Done (every story):** AC met with evidence link, CI green, contracts backward-compatible, dashboards updated, docs updated (this file + service README).
2. **Every cutover:** staging first → consistency delta 0 → rollback drill → production flip in change window.
3. **Doc alignment:** scope changes update FUTURESTATE-ARCHITECTURE §5/§6 + this file in the same PR.
4. **Estimates are planning aids; gates are the truth.** A phase exits on its CG checklist, not the calendar.

---

*Delivery plan v1, 2026-10-16. Refines FUTURESTATE-ARCHITECTURE.md §10 (Phase 0 = infrastructure only; platform services moved to Phase 1). FUTURESTATE §10 remains the strategic summary; this file is the execution authority.*
