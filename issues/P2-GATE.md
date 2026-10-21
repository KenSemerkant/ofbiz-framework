# Gate CG-2 — Phase 2: party-svc + CDC Bridge

**Type:** phase completion gate
**Objective:** first domain migrated; CDC interop pattern proven end-to-end (the template for all later phases).
**Status:** PASSED 2026-10-21 (local kind staging)

## Checklist (evidence)
- [x] party-svc owns party schema (133 entities -> JPA subset for live slices; historical archive documented) — microservices/party-svc/MAPPING.md
- [x] Debezium streams monolith party tables -> `ofbiz.party.*` topics (lag < 5s at 100 msg/s test) — platform/cdc/20-verify.sh ALL CHECKS PASSED; 40-seed-parties.sh sustained ~270 msg/s (10k posts in 37s), reverse-sync converged lag 0. Topic spelling + JSON values deviations: issues/P2-F2-S1.md
- [x] Write cutover done in staging: gateway routes `/api/v1/parties/**` to party-svc (RoleAuthFilter required-role=CUSTOMER: machine token 403 live-proven); reverse-sync keeps monolith current (new-service-wins); consistency job delta = 0 on a 10k-party dataset — 30-check-consistency.sh: monolith=10,057 party-svc=10,057, checksums identical (both pre-cutover mirror and post-cutover truth)
- [x] Rollback drill executed: route flip back, zero data loss — 50-rollback-drill.sh PASSED (flip back + monolith-side write mirrored by CDC into party-svc + flip forward, 10,058 -> 10,059 rows preserved)
- [x] SPA (skeleton) lists/searches parties via new API — platform/spa/ (OIDC PKCE login + GET /api/v1/parties?role=, nginx :4200); browser sign-in via kubectl port-forward (demo-user / demo-password carries CUSTOMER through the party projection -> JWT roles chain)

## Known issues carried
- CDC values JSON-with-schema, not Avro (Apicurio converter serde lookup-mode; issues/P2-F2-S1.md)
- JWT role revocation is effective at next token issuance (<5s via projection); already-issued tokens ride out their TTL
