# billing-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.4 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P5** (billing) / **implemented in P5** (finance).

- `invoices, payments, payment-gateway integrations, customer billing documents` — see MAPPING.md for the frozen entity split and the live slice.
- Modules: `billing-domain` (pure Java) ← `billing-application` ← `adapters-rest`
  (ADR-007; ArchUnit-checked, 95% line-coverage gate per module).
- API: `/api/v1/...` via the gateway; events on `billing.*` topics via
  the transactional outbox (ADR-009).
- Deploy: `platform/services/10-deploy.sh`; gate drills in `platform/cdc/`
  and `platform/services/`.
