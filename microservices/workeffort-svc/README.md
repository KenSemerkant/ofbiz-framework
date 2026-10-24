# workeffort-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §5.9 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: **implemented in P6**.

- `projects, tasks, timesheets, iCalendar export` — see MAPPING.md for the
  entity split (live slice vs deferred to Phase 7).
- Modules: `workeffort-domain` (pure Java) ← `workeffort-application` ←
  `adapters-rest` (ADR-007; ArchUnit-checked, 95% line-coverage gate per
  module).
- API: `/api/v1/workefforts` (+ `?parent=` tree query, `/{id}/status`,
  `/calendar.ics`) and `/api/v1/timesheets` (+ `/{id}/submit`) via the
  gateway; events on `workeffort.*` topics via the transactional outbox
  (ADR-009).
- Deploy: `platform/services/10-deploy.sh`; gate drills in `platform/cdc/`
  and `platform/services/`.
