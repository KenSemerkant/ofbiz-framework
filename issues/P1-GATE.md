# Gate CG-1 — Phase 1: Platform (Common) Services

**Type:** phase completion gate
**Objective:** the five cross-cutting services stand up skeleton-to-working on Phase-0 infra. No domain migration yet.
**Depends on:** all phase 1 stories

## Checklist (all must pass, evidence linked)
- [ ] OIDC login flow issues a JWT usable against a gateway demo route
- [x] gateway routes to demo-svc with JWT validation + rate limit proven (429 on burst) — `GatewayRoutingTest`
- [x] audit-svc consumes a demo topic wildcard and stores events queryably — `AuditIngestTest`
- [x] notification-svc sends a test email on `notification.requested.v1` — `NotificationDeliveryTest`
- [ ] search-svc indexes a demo document and answers a query via API (domain model only)
- [ ] All 5 services: CI green, dashboards live, mTLS-only, DLQ wired

## Notes
- The identity-issued token → gateway end-to-end path is still open: gateway tests mint their own HS256 token and identity tests do not call the gateway.
- Audit/notification integration tests run against Testcontainers, not the kind dev stack.
