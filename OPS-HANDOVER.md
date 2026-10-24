# OPS-HANDOVER.md — Post-Monolith Operations (P7-S3)

Ownership, on-call routing, and runbooks for the 16-service target stack after
the monolith decommission (Phase 7). Sign-off line at the bottom is the CG-7
artifact. Team names are the proposed split for a single-platform org; rename
at sign-off if the real org differs — the matrix, not the names, is the contract.

## Service ownership matrix

| Service | Domain | Owning team | DB | Backup | Notes |
|---|---|---|---|---|---|
| identity-svc | Platform | platform | identity | daily dump → ofbiz-archive | OIDC issuer; ALL north-south auth depends on it |
| gateway | Platform | platform | — (Redis for rate limit) | config-only | Only north-south entry point (ADR-010); route table = gateway application.yaml |
| audit-svc | Platform | platform | audit | daily dump → ofbiz-archive | Consumes `audit.*` wildcard; OpenSearch retention |
| notification-svc | Platform | platform | notification | daily dump → ofbiz-archive | Eager SMTP workaround is permanent on local staging (P5-GATE) |
| search-svc | Platform | platform | search | daily dump → ofbiz-archive | OpenSearch index rebuild from events if lost |
| party-svc | Party | commerce-core | party | daily dump → ofbiz-archive | Employee/customer identity references |
| product-svc | Catalog | commerce-core | product | daily dump → ofbiz-archive | Price rules live here |
| order-svc | Order | commerce-core | orderdb | daily dump → ofbiz-archive | placeOrder saga owner; ECA rules replaced monolith ECA |
| billing-svc | Billing | commerce-core | billing | daily dump → ofbiz-archive | PCI scope isolated here |
| finance-svc | Finance | finance-ops | finance | daily dump → ofbiz-archive | GL + closed-period guards |
| inventory-svc | Inventory | finance-ops | inventory | daily dump → ofbiz-archive | Reservations + shipments |
| manufacturing-svc | Manufacturing | finance-ops | manufacturing | daily dump → ofbiz-archive | MRP as streams; v1 = one proposal per short product (P6-GATE deviation 4) |
| workeffort-svc | Work Effort | people-ops | workeffort | daily dump → ofbiz-archive | iCal export (RFC 5545, DTEND exclusive) |
| humanres-svc | HR | people-ops | humanres | daily dump → ofbiz-archive | Onboarding saga (role + employment) |
| content-svc | Content | people-ops | content | Garage ofbiz-content bucket | S3 API only (ADR-008) |
| marketing-svc | Marketing | growth | marketing | daily dump → ofbiz-archive | Attribution consumes order events |

## On-call routing (post-monolith)

One rotation per owning team; alert routing key = team name.

| Team | Services | Escalation order |
|---|---|---|
| platform | identity-svc, gateway, audit-svc, notification-svc, search-svc | primary → secondary → eng manager |
| commerce-core | party-svc, product-svc, order-svc, billing-svc | primary → secondary → eng manager |
| finance-ops | finance-svc, inventory-svc, manufacturing-svc | primary → secondary → eng manager |
| people-ops | workeffort-svc, humanres-svc, content-svc | primary → secondary → eng manager |
| growth | marketing-svc | borrows people-ops primary until headcount |

Routing rules:
- **gateway or identity-svc down** = Sev-1, page platform immediately AND the
  owning team of any affected route — no north-south traffic survives.
- **Kafka/Postgres/Garage platform outage** = Sev-1 platform; domain teams do
  NOT debug shared infra themselves (runbooks below).
- DLQ depth > 0 for 30 min on any `<topic>.dlq` → page the consumer's owning
  team (retry-then-DLQ is the service contract, ADR-009).

## Runbooks

| Scenario | Runbook |
|---|---|
| Verify platform infra | `platform/*/20-verify.sh` (gate evidence generators) |
| Deploy / redeploy services | `platform/services/10-deploy.sh` (full) · `platform/services/15-redeploy-p7.sh` (subset) |
| Docker VM disk full | P6-GATE runbook: host `docker builder/image prune -a -f`, then `ctr -n k8s.io images rm` for stale refs inside each node; watch etcd/apiserver at 100% |
| VM reboot recovery | P5-GATE verdict: cluster + PVs survive; node images need a full `10-deploy.sh` rerun; demo-svc image is `demo-svc:dev` |
| SMTP blackhole (local) | P5-GATE: eager client-first SMTP + mailpit on 8080 — permanent on this cluster |
| Monolith data restore | snapshot + checksum manifest in `s3://ofbiz-archive/monolith-db/`; restore = `pg_restore` into a scratch DB (procedure in `platform/cdc/80-archive-monolith-db.sh` [3/5]) |
| CDC topics forensics | archived dumps + end-offset manifest in `s3://ofbiz-archive/kafka-topics/` |
| Residual `ofbiz.SHIPMENT`/`SHIPMENT_ITEM` topics (P7-GATE known residual) | empty, archived, zero-traffic; recreate within seconds of `--delete` by an unidentified principal. To close out: enable a Kafka authorizer audit log on CreateTopics, or grep each service's producer config; then delete. Non-urgent — no producer this stack owns writes them. |
| Rollback to monolith | **No longer possible** — bridge removed (P7-S1). Recovery = restore from service backups + replay from `s3://ofbiz-archive`. This is final. |

## Retention

- `s3://ofbiz-archive/monolith-db/` — final snapshot + manifest: keep per data-retention policy (default: indefinite; it is the last monolith-state record).
- `s3://ofbiz-archive/kafka-topics/` — final CDC topic dumps: same policy.
- Service DB daily dumps: 30 days rolling (platform team owns the job).

## Sign-off (CG-7)

- [ ] Ownership matrix reviewed by all five proposed teams
- [ ] On-call routing loaded into the paging tool
- [ ] platform / commerce-core / finance-ops / people-ops / growth leads sign below

| Role | Name | Date |
|---|---|---|
| Platform lead | | |
| Commerce-core lead | | |
| Finance-ops lead | | |
| People-ops lead | | |
| Growth lead | | |
