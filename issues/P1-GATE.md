# Gate CG-1 — Phase 1: Platform (Common) Services

**Type:** phase completion gate
**Objective:** the five cross-cutting services stand up skeleton-to-working on Phase-0 infra. No domain migration yet.
**Depends on:** all phase 1 stories

## Checklist (all must pass, evidence linked)
- [x] OIDC login flow issues a JWT usable against a gateway demo route — `GatewayLiveJwksTest` (issuer-signed RS256 → 200 on `/api/v1/demo/**`, unknown key → 401); on-cluster client_credentials→gateway 200 (`platform/services/20-verify.sh`, 18/18)
- [x] gateway routes to demo-svc with JWT validation + rate limit proven (429 on burst) — `GatewayRoutingTest`
- [x] audit-svc consumes a demo topic wildcard and stores events queryably — `AuditIngestTest`
- [x] notification-svc sends a test email on `notification.requested.v1` — `NotificationDeliveryTest`
- [x] search-svc indexes a demo document and answers a query via API — `SearchApiTest` (real OpenSearch + Kafka Testcontainers; REST + gRPC + reindex); on-cluster mTLS produce → gateway query hit (20-verify.sh)
- [x] All 5 services: CI green, dashboards live, mTLS-only, DLQ wired — P1-F6 closeout (`platform/services/20-verify.sh` ALL CHECKS PASSED 18/18, 2026-10-21; local-dev disk caveat in P1-F6-S3)

## Notes
- The identity-issued token → gateway end-to-end path was open at first run (gateway tests minted their own HS256 token); closed by `GatewayLiveJwksTest` (production decoder wiring via live JWKS) + the P1-F6 on-cluster verify.
- Audit/notification integration tests run against Testcontainers, not the kind dev stack.
