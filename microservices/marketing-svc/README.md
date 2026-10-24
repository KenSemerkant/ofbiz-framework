# marketing-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.5 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P6**.

- `campaigns, tracking codes, sales opportunities, order attribution report` — see MAPPING.md for the live slice vs deferred entities.
- Modules: `marketing-domain` (pure Java) ← `marketing-application` ← `adapters-rest`
  (ADR-007; ArchUnit-checked, 95% line-coverage gate per module).
- API: `/api/v1/...` via the gateway; events on `marketing.*` topics via
  the transactional outbox (ADR-009); consumes `order.order.placed.v1`
  for attribution (idempotent via processed_events + unique order_id).
- Deploy: `platform/services/10-deploy.sh`; gate drills in `platform/cdc/`
  and `platform/services/`.
