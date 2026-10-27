# Ops Toolkit Design — `./ops`

**Date:** 2026-10-26
**Status:** Approved (user, 2026-10-26)
**Scope:** Bash operations & maintenance scripts for both `monolith/` and `microservices/` (kind cluster).

## Problem

Post-P9, day-2 operations are scattered: `platform/*` scripts exist for bootstrap/deploy/verify/seed but there is no cluster-down, per-service logs/restart, or DB backup/restore. The monolith has no wrapper at all — only upstream OFBiz scripts.

## Decision

Single dispatcher `ops` at repo root. Subcommands delegate to existing `platform/*` scripts where they exist; only missing glue is new code. Monolith runs via the upstream `postgres-demo` compose (user choice: Docker + durable Postgres, option B).

## Interface

```
ops micro up          # platform/bootstrap/00-bootstrap.sh + infra 10-install.sh's
                      # (kafka, certmanager, helm, observability, vault, storage)
                      # + platform/services/10-deploy.sh
ops micro down        # kind delete cluster (requires --yes)
ops micro status      # kubectl get pods -A, gateway URL, ready counts
ops micro logs S      # kubectl logs -f deploy/S
ops micro restart S   # kubectl rollout restart deploy/S + rollout status
ops micro deploy      # re-run platform/services/10-deploy.sh
ops micro verify      # platform/services/20-verify.sh
ops micro seed        # platform/services/40-seed-demo.sh
ops micro backup      # pg_dump all service DBs via kubectl exec postgres-0
                      #   -> backups/micro/<ts>/<db>.sql.gz
ops micro restore DIR # requires --yes; psql-restore every dump in DIR
ops micro fwd [stop]  # port-forward jaeger/grafana/mailhog; `fwd stop` kills

ops mono build        # docker build -t ofbiz-docker monolith/
ops mono start        # compose up -d (postgres-demo) with ops-managed
                      #   docker-compose.override.yml adding a named pgdata volume
ops mono stop         # compose down; STOP_ALL=1 also wipes volumes
ops mono status       # compose ps + curl -k https://localhost:8443
ops mono logs         # compose logs -f ofbiz
ops mono psql         # compose exec db psql -U ofbiz
ops mono backup       # pg_dump all mono DBs (ofbizmaindb/olap/tenant) -> backups/mono/<ts>/
ops mono restore FILE # requires --yes; pipe dump into db container
```

`S` = deployment name among the 15 `-svc` services (identity, audit, notification, search, party, product, inventory, order, billing, finance, manufacturing, workeffort, humanres, content, marketing) or `gateway`. Tab completion dropped — a child process cannot register completions in the parent shell; would require sourcing the dispatcher.

## Non-goals

- Per-service rebuild (`rebuild <svc>`) — full `deploy` covers it; add only if it proves too slow.
- CDC wiring — decommissioned in P7.
- Editing the upstream `postgres-demo` example — durability comes from an ops-owned override file only.
- Embedded Derby mode for the monolith — rejected in favor of compose (user choice B).

## Behavior rules

- Destructive paths (`micro down`, `micro restore`, `mono restore`, `STOP_ALL=1`) require an explicit `--yes` flag or env var; otherwise print what they would do and exit 1.
- All backups land under `backups/` (gitignored).
- Errors fail loud: `set -euo pipefail`; delegated-script exit codes propagate.
- `ops` with no args prints help; `ops help` same.

## Testing

- `bash -n ops` syntax gate.
- `ops` no-arg output contains every subcommand name (assert one-liner).
- Dry-run behavior: `micro down` / restores print intended actions and exit 1 when confirmation absent.
- Manual acceptance: `mono start` → `curl -k https://localhost:8443` responds; `micro backup` produces non-empty `.sql.gz` per DB.

## Files touched

- New: `ops` (root, executable); `backups/` gitignore entry in root `.gitignore`.
- Docs: this spec; IMPLEMENTATION.md story line added same-PR per repo DoD.
