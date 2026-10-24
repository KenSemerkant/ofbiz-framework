# manufacturing-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.7 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P6**.

- `BOM, production routing, MRP (deterministic netting over the demand/supply ledger)` — see MAPPING.md for the entity split and the live slice.
- Modules: `manufacturing-domain` (pure Java) ← `manufacturing-application`
  ← `adapters-rest` (ADR-007; ArchUnit-checked, 95% line-coverage gate per module).
- API: `/api/v1/...` via the gateway; events on `manufacturing.*` topics via
  the transactional outbox (ADR-009).
- Deploy: `platform/services/10-deploy.sh`; gate drills in `platform/cdc/`
  and `platform/services/`.
