# humanres-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.10 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P6**.

- `positions, employments, employee onboarding` — see MAPPING.md for the
  frozen entity split and the live slice.
- Modules: `humanres-domain` (pure Java) ← `humanres-application` ←
  `adapters-rest` (ADR-007; ArchUnit-checked, 95% line-coverage gate per
  module).
- API: `/api/v1/positions`, `/api/v1/employments`, `/api/v1/onboarding`
  via the gateway; events on `humanres.*` topics via the transactional
  outbox (ADR-009).
- Onboarding saga (order-svc saga pattern): EMPLOYEE role via party-svc
  REST (`humanres.party.base-url`), then employment + events; persisted
  saga state, resumable after restart, retryable via
  `POST /api/v1/onboarding/{id}/retry`.
- Consumes `party.party.created/updated.v1` into a local projection
  (id + names JSON) — unknown party on onboarding is a 404.
- Deploy: `platform/services/10-deploy.sh`.
