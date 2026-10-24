# content-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.5 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P6** (content).

- `content trees, document upload/publish, S3-compatible object storage` — see MAPPING.md for the entity split and the live slice.
- Modules: `content-domain` (pure Java) ← `content-application` ← `adapters-rest`
  (ADR-007; ArchUnit-checked, 95% line-coverage gate per module).
- Storage: S3 API only (ADR-008) — Garage locally (`platform/storage/`),
  cloud S3 = endpoint+creds swap; object bytes never touch the database.
- API: `/api/v1/contents` via the gateway; events on `content.*` topics via
  the transactional outbox (ADR-009).
- Deploy: `platform/services/10-deploy.sh`; gate drills in `platform/cdc/`
  and `platform/services/`.
