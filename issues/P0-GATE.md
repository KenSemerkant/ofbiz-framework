# Gate CG-0 — Phase 0: Infrastructure Build

**Type:** phase completion gate
**Objective:** everything later phases deploy onto exists and is provable: cluster, mesh, Kafka + schema registry, observability stack, CI/CD, and the repository scaffolding/conventions from FUTURESTATE-LOWLEVEL §1.
**Depends on:** all phase 0 stories

## Checklist (all must pass, evidence linked)
- [x] `kubectl get pods -A` green on target cluster; mesh-injected namespace `ofbiz` exists — re-verified 2026-10-20 on `kind-ofbiz` (3 nodes Ready; `platform/bootstrap/10-verify-mtls.sh` → ALL CHECKS PASSED)
- [x] Kafka (Strimzi) + Schema Registry reachable from a test pod; topic create/consume roundtrip proven — `platform/kafka/20-verify.sh` PASS; `21-verify-broker-kill.sh` 100/100 messages, 0 duplicates after killing a broker
- [x] OTel collector receiving spans/metrics from a demo deployment; Jaeger trace and Grafana dashboard visible — `platform/observability/20-verify.sh` → ALL CHECKS PASSED (Jaeger, Prometheus 1/1, Grafana 4 datasources, Loki, OpenSearch `ofbiz-logs`)
- [x] CI pipeline builds a hello-world Spring Boot 4.0.x service (convention plugin) and pushes an image to registry; CD deploys it via the shared Helm chart with mTLS enforced — green run 39450018861; local Helm gate `platform/helm/20-verify.sh` → 39803 requests, 0 failures during rollout
- [x] Repo scaffold: `buildSrc` convention plugins compile; ArchUnit layering test enforces domain-purity on a sample module — full build green; per-service full-tree `ServiceArchitectureTest` + JaCoCo domain coverage gate

## Re-verification 2026-10-20
- `infra/dev/up.sh` recreated `kind-ofbiz` (Terraform) and applied the Phase 0 stack (Ansible); observability needed one re-run of its verifier while the OpenSearch index warmed up.
- `platform/certmanager/20-verify.sh`, `platform/vault/20-verify.sh`, `platform/storage/20-verify.sh` → ALL CHECKS PASSED.
